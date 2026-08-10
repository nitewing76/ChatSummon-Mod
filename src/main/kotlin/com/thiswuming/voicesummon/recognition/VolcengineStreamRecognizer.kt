package com.thiswuming.voicesummon.recognition

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.thiswuming.voicesummon.util.ModLogger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.nio.ByteBuffer
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import java.util.zip.GZIPOutputStream
import java.io.ByteArrayOutputStream


class VolcengineStreamRecognizer : SpeechRecognizer {

    private val logger = ModLogger.getLogger("VolcengineStream")
    private val gson = Gson()
    private val httpClient = HttpClient.newHttpClient()
    private val running = AtomicBoolean(false)

    private var webSocket: WebSocket? = null
    private var onResultCallback: ((RecognitionResult) -> Unit)? = null
    private var config: VolcengineConfig? = null
    private var connectId: String = ""

    
    
    
    @Volatile
    private var initialized: Boolean = false

    
    private val audioQueuedCount = java.util.concurrent.atomic.AtomicLong(0)
    private val audioSentCount = java.util.concurrent.atomic.AtomicLong(0)
    private val emptyResultCount = java.util.concurrent.atomic.AtomicLong(0)

    
    private var heartbeatExecutor: ScheduledExecutorService? = null

    
    private var reconnectAttempts: Int = 0
    private val maxReconnectAttempts: Int = 5
    private val reconnectDelayMs: Long = 3000

    
    private val MAX_BUFFER_CHUNKS: Int = 500

    
    private val sendTimeoutMs: Long = 5000

    
    
    private val sendLock = ReentrantLock()

    
    
    private val audioSendQueue = java.util.concurrent.LinkedBlockingQueue<ByteBuffer>()
    private var sendWorker: Thread? = null

    
    private val reconnecting = java.util.concurrent.atomic.AtomicBoolean(false)

    
    private var reconnectExecutor: ScheduledExecutorService? = null

    companion object {
        
        @JvmStatic
        private val activeConnections = java.util.concurrent.atomic.AtomicInteger(0)

        
        private const val PROTOCOL_VERSION = 0b0001
        private const val DEFAULT_HEADER_SIZE = 0b0001

        
        private const val FULL_CLIENT_REQUEST = 0b0001
        private const val AUDIO_ONLY_REQUEST = 0b0010
        private const val FULL_SERVER_RESPONSE = 0b1001
        private const val SERVER_ERROR_RESPONSE = 0b1111

        
        private const val NO_SEQUENCE = 0b0000
        private const val NEG_SEQUENCE = 0b0010

        
        private const val NO_SERIALIZATION = 0b0000
        private const val JSON_SERIALIZATION = 0b0001

        
        private const val NO_COMPRESSION = 0b0000
        private const val GZIP_COMPRESSION = 0b0001
    }

    override fun start(config: VolcengineConfig, onResult: (RecognitionResult) -> Unit) {
        this.config = config
        this.onResultCallback = onResult
        this.connectId = UUID.randomUUID().toString()
        reconnectAttempts = 0
        initialized = false
        audioQueuedCount.set(0)
        audioSentCount.set(0)
        emptyResultCount.set(0)
        audioSendQueue.clear()
        
        
        running.set(true)
        startSendWorker()
        connect()
    }

    private fun connect() {
        val cfg = config ?: return
        val url = "wss://openspeech.bytedance.com/api/v3/sauc/bigmodel"

        try {
            val wsBuilder = httpClient.newWebSocketBuilder()
            
            wsBuilder.header("X-Api-Key", cfg.apiKey)
            wsBuilder.header("X-Api-Resource-Id", cfg.resourceId)
            wsBuilder.header("X-Api-Connect-Id", connectId)

            logger.info("Connecting to Volcengine ASR WebSocket...")
            
            wsBuilder.buildAsync(URI(url), WebSocketListener()).whenComplete { ws, err ->
                if (err != null) {
                    logger.error("Failed to connect to Volcengine ASR: ${err.message}", err)
                    
                    if (running.get()) {
                        scheduleReconnect()
                    }
                } else {
                    
                    
                    
                    if (!running.get()) {
                        logger.info("WebSocket connected but recognizer stopped during connect, closing")
                        try { ws.sendClose(1000, "Stopped during connect") } catch (_: Exception) {}
                        return@whenComplete
                    }
                    webSocket = ws
                    reconnectAttempts = 0
                    reconnecting.set(false)
                    val active = activeConnections.incrementAndGet()
                    logger.info("WebSocket connected. connectId={}, activeConnections={}/10", connectId, active)
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to initiate connect to Volcengine ASR: ${e.message}", e)
            scheduleReconnect()
        }
    }

    override fun feedAudio(audioData: ByteArray) {
        if (!running.get()) return

        try {
            val header = generateHeader(
                messageType = AUDIO_ONLY_REQUEST,
                messageTypeSpecificFlags = NO_SEQUENCE,
                serialMethod = NO_SERIALIZATION,
                compressionType = NO_COMPRESSION
            )

            val payloadSize = audioData.size
            val fullMessage = ByteBuffer.allocate(header.size + 4 + payloadSize)
            fullMessage.put(header)
            fullMessage.putInt(payloadSize)
            fullMessage.put(audioData)
            fullMessage.flip()

            
            
            if (audioSendQueue.size < MAX_BUFFER_CHUNKS) {
                audioSendQueue.offer(fullMessage)
                audioQueuedCount.incrementAndGet()
            } else {
                logger.warn("Audio send queue full ({}), dropping audio chunk", MAX_BUFFER_CHUNKS)
            }
        } catch (e: Exception) {
            logger.error("Error queueing audio data: ${e.message}", e)
        }
    }

    
    private fun startSendWorker() {
        stopSendWorker()
        sendWorker = Thread({
            while (running.get()) {
                val current = webSocket
                
                if (current == null || !initialized) {
                    try {
                        Thread.sleep(100)
                    } catch (e: InterruptedException) {
                        break
                    }
                    continue
                }
                val msg = try {
                    audioSendQueue.poll(200, TimeUnit.MILLISECONDS)
                } catch (e: InterruptedException) {
                    break
                }
                if (msg == null) continue
                try {
                    sendLock.lock()
                    try {
                        current.sendBinary(msg, true)?.orTimeout(sendTimeoutMs, TimeUnit.MILLISECONDS)?.join()
                        
                        val sent = audioSentCount.incrementAndGet()
                        if (sent % 100 == 0L) {
                            logger.info(
                                "Audio sent: {} packets, queued: {}, emptyResults: {}",
                                sent, audioSendQueue.size, emptyResultCount.get()
                            )
                        }
                    } finally {
                        sendLock.unlock()
                    }
                } catch (e: Exception) {
                    logger.error("Error sending audio data: ${e.message}", e)
                }
            }
        }, "volcengine-asr-sender").apply {
            isDaemon = true
            start()
        }
    }

    private fun stopSendWorker() {
        sendWorker?.interrupt()
        sendWorker = null
    }

    
    private fun sendLastAudio() {
        if (!running.get() || webSocket == null) return

        try {
            
            val header = generateHeader(
                messageType = AUDIO_ONLY_REQUEST,
                messageTypeSpecificFlags = NEG_SEQUENCE,
                serialMethod = NO_SERIALIZATION,
                compressionType = NO_COMPRESSION
            )

            val payload = ByteArray(0)
            val fullMessage = ByteBuffer.allocate(header.size + 4)
            fullMessage.put(header)
            fullMessage.putInt(0)

            
            if (sendLock.tryLock(1, TimeUnit.SECONDS)) {
                try {
                    webSocket?.sendBinary(fullMessage.flip(), true)?.orTimeout(sendTimeoutMs, TimeUnit.MILLISECONDS)?.join()
                } finally {
                    sendLock.unlock()
                }
            }
            logger.debug("Sent last audio packet")
        } catch (e: Exception) {
            logger.error("Error sending last audio packet: ${e.message}", e)
        }
    }

    override fun stop() {
        running.set(false)
        initialized = false
        stopHeartbeat()
        reconnectExecutor?.shutdownNow()
        reconnectExecutor = null
        stopSendWorker()
        try {
            sendLastAudio()
            
            if (sendLock.tryLock(1, TimeUnit.SECONDS)) {
                try {
                    webSocket?.sendClose(1000, "Client closing")
                } finally {
                    sendLock.unlock()
                }
            }
        } catch (_: Exception) {
        }
        webSocket = null
        
        audioSendQueue.clear()
        logger.info("Stream recognizer stopped")
    }

    override fun isRunning(): Boolean = running.get()

    
    private fun startHeartbeat() {
        stopHeartbeat()
        val executor = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "volcengine-asr-heartbeat").apply { isDaemon = true }
        }
        heartbeatExecutor = executor
        executor.scheduleAtFixedRate({
            try {
                sendLock.lock()
                try {
                    webSocket?.sendPing(ByteBuffer.wrap(ByteArray(0)))
                } finally {
                    sendLock.unlock()
                }
            } catch (_: Exception) {
            }
        }, 10, 10, TimeUnit.SECONDS)
    }

    
    private fun stopHeartbeat() {
        heartbeatExecutor?.shutdownNow()
        heartbeatExecutor = null
    }

    
    private fun attemptReconnect() {
        if (!reconnecting.compareAndSet(false, true)) {
            return 
        }
        scheduleReconnect()
    }

    
    private fun scheduleReconnect() {
        if (reconnectAttempts >= maxReconnectAttempts) {
            logger.error("Max reconnect attempts ({}) reached. Giving up.", maxReconnectAttempts)
            running.set(false)
            reconnecting.set(false)
            return
        }

        reconnectAttempts++
        logger.info("Reconnecting in {}ms (attempt {}/{})", reconnectDelayMs, reconnectAttempts, maxReconnectAttempts)

        val exec = reconnectExecutor ?: Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "volcengine-asr-reconnect").apply { isDaemon = true }
        }.also { reconnectExecutor = it }

        exec.schedule({
            if (!running.get()) {
                reconnecting.set(false)
                return@schedule
            }
            connectId = UUID.randomUUID().toString()
            connect()
        }, reconnectDelayMs, TimeUnit.MILLISECONDS)
    }

    
    private fun readIntBE(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                (bytes[offset + 3].toInt() and 0xFF)
    }

    
    private fun generateHeader(
        messageType: Int,
        messageTypeSpecificFlags: Int,
        serialMethod: Int,
        compressionType: Int,
        reservedData: Int = 0x00
    ): ByteArray {
        val header = ByteArray(4)
        header[0] = ((PROTOCOL_VERSION shl 4) or DEFAULT_HEADER_SIZE).toByte()
        header[1] = ((messageType shl 4) or messageTypeSpecificFlags).toByte()
        header[2] = ((serialMethod shl 4) or compressionType).toByte()
        header[3] = reservedData.toByte()
        return header
    }

    
    private fun gzipCompress(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { gzip ->
            gzip.write(data)
        }
        return bos.toByteArray()
    }

    
    private fun sendFullClientRequest(ws: WebSocket) {
        val cfg = config ?: return

        
        val requestParams = mutableMapOf<String, Any>(
            "reqid" to UUID.randomUUID().toString(),
            "sequence" to 1,
            "nbest" to 1,
            "model_name" to "bigmodel",
            "show_utterances" to true
        )

        
        if (cfg.boostingTableId.isNotBlank()) {
            requestParams["corpus"] = mapOf("boosting_table_id" to cfg.boostingTableId)
        }

        val requestJson = mapOf(
            "app" to mapOf(
                "appid" to cfg.apiKey,
                "token" to cfg.apiKey,
                "cluster" to cfg.cluster
            ),
            "user" to mapOf(
                "uid" to "voicesummon-player"
            ),
            "audio" to mapOf(
                "format" to "pcm",
                "codec" to "raw",
                "rate" to 16000,
                "bits" to 16,
                "channel" to 1
            ),
            "request" to requestParams
        )

        val jsonStr = gson.toJson(requestJson)
        val jsonBytes = jsonStr.toByteArray(Charsets.UTF_8)
        val compressedPayload = gzipCompress(jsonBytes)

        val header = generateHeader(
            messageType = FULL_CLIENT_REQUEST,
            messageTypeSpecificFlags = NO_SEQUENCE,
            serialMethod = JSON_SERIALIZATION,
            compressionType = GZIP_COMPRESSION
        )

        val fullMessage = ByteBuffer.allocate(header.size + 4 + compressedPayload.size)
        fullMessage.put(header)
        fullMessage.putInt(compressedPayload.size)
        fullMessage.put(compressedPayload)

        sendLock.lock()
        try {
            ws.sendBinary(fullMessage.flip(), true)?.orTimeout(sendTimeoutMs, TimeUnit.MILLISECONDS)?.join()
        } finally {
            sendLock.unlock()
        }
        logger.info("Full client request sent, ASR session initialized")
    }

    
    private fun sendFullClientRequestOn(ws: WebSocket) = sendFullClientRequest(ws)

    
    private fun parseServerResponse(payload: ByteArray) {
        try {
            
            val jsonStr = try {
                val gzipStream = java.util.zip.GZIPInputStream(payload.inputStream())
                String(gzipStream.readBytes(), Charsets.UTF_8)
            } catch (_: Exception) {
                String(payload, Charsets.UTF_8)
            }

            val jsonObj = JsonParser.parseString(jsonStr).asJsonObject

            
            
            val code = jsonObj.get("code")?.asInt

            if (code != null) {
                
                val message = jsonObj.get("message")?.asString ?: ""
                logger.warn("ASR returned error: code={}, message={}", code, message)
                return
            }

            
            val resultElem = jsonObj.get("result")
            var text = ""
            var confidence = 0.9f
            if (resultElem != null && resultElem.isJsonObject) {
                val resultObj = resultElem.asJsonObject
                text = resultObj.get("text")?.asString ?: ""
                confidence = parseConfidence(resultObj) ?: 0.9f
            } else if (resultElem != null && resultElem.isJsonArray) {
                val arr = resultElem.asJsonArray
                if (arr.size() > 0) {
                    val first = arr[0].asJsonObject
                    text = first.get("text")?.asString ?: ""
                    confidence = parseConfidence(first) ?: 0.9f
                }
            }

            if (text.isNotBlank()) {
                onResultCallback?.invoke(
                    RecognitionResult(
                        text = text.trim(),
                        confidence = confidence,
                        isFinal = true
                    )
                )
                
                ModLogger.writeRecognizedText(text.trim())
            } else {
                
                
                val n = emptyResultCount.incrementAndGet()
                if (n == 1L || n % 50 == 0L) {
                    logger.warn("ASR returned empty text (count={}), queued={}, sent={}", n, audioQueuedCount.get(), audioSentCount.get())
                }
            }
        } catch (e: Exception) {
            logger.error("Error parsing server response: ${e.message}", e)
        }
    }

    
    private fun parseConfidence(resultObj: com.google.gson.JsonObject): Float? {
        val utterances = resultObj.getAsJsonArray("utterances") ?: return null
        if (utterances.size() == 0) return null
        val words = utterances[0].asJsonObject.getAsJsonArray("words") ?: return null
        if (words.size() == 0) return null
        var total = 0f
        var count = 0
        for (w in words) {
            val conf = w.asJsonObject.get("confidence")?.asFloat ?: continue
            total += conf
            count++
        }
        return if (count > 0) (total / count).coerceIn(0f, 1f) else null
    }

    
    private inner class WebSocketListener : WebSocket.Listener {

        override fun onOpen(webSocket: WebSocket) {
            logger.info("WebSocket opened, sending full client request...")
            
            
            
            sendFullClientRequestOn(webSocket)
            this@VolcengineStreamRecognizer.webSocket = webSocket
            initialized = true
            startHeartbeat()
            
            webSocket.request(1)
        }

        override fun onBinary(webSocket: WebSocket, data: ByteBuffer, last: Boolean): CompletableFuture<*> {
            if (data.remaining() < 4) {
                return CompletableFuture.completedFuture(null)
            }

            val bytes = ByteArray(data.remaining())
            data.get(bytes)

            
            val headerSecondByte = bytes[1].toInt() and 0xFF
            val messageType = (headerSecondByte shr 4) and 0x0F
            val flags = headerSecondByte and 0x0F

            
            
            
            val hasSequence = (flags and 0b0011) != 0
            val baseOffset = 4 + if (hasSequence) 4 else 0

            when (messageType) {
                FULL_SERVER_RESPONSE -> {
                    
                    if (bytes.size >= baseOffset + 4) {
                        val payloadSize = readIntBE(bytes, baseOffset)

                        
                        
                        if (payloadSize > 0 && payloadSize <= bytes.size - (baseOffset + 4)) {
                            val payload = bytes.copyOfRange(baseOffset + 4, baseOffset + 4 + payloadSize)
                            parseServerResponse(payload)
                        }
                    }
                }
                SERVER_ERROR_RESPONSE -> {
                    
                    if (bytes.size >= baseOffset + 4) {
                        val errorMsgSize = readIntBE(bytes, baseOffset)
                        val errorMsg = if (errorMsgSize > 0 && bytes.size >= baseOffset + 4 + errorMsgSize) {
                            String(bytes, baseOffset + 4, errorMsgSize, Charsets.UTF_8)
                        } else {
                            "Unknown error"
                        }
                        logger.error("Server error: {}", errorMsg)
                    }
                }
            }

            webSocket.request(1)
            return CompletableFuture.completedFuture(null)
        }

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletableFuture<*> {
            logger.debug("WebSocket text received: {}", data)
            webSocket.request(1)
            return CompletableFuture.completedFuture(null)
        }

        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletableFuture<*> {
            
            
            val active = activeConnections.updateAndGet { (it - 1).coerceAtLeast(0) }
            logger.info("WebSocket closed: code={}, reason={}, activeConnections={}/10", statusCode, reason, active)
            
            initialized = false
            this@VolcengineStreamRecognizer.webSocket = null
            if (running.get()) {
                attemptReconnect()
            }
            return CompletableFuture.completedFuture(null)
        }

        override fun onError(webSocket: WebSocket, error: Throwable?) {
            logger.error("WebSocket error: ${error?.message}", error)
            initialized = false
            if (running.get()) {
                attemptReconnect()
            }
        }
    }
}
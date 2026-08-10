package com.thiswuming.voicesummon.recognition

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.thiswuming.voicesummon.util.ModLogger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec


class AliyunStreamRecognizer : SpeechRecognizer {

    private val logger = ModLogger.getLogger("AliyunStream")
    private val gson = Gson()
    private val httpClient = HttpClient.newHttpClient()
    private val running = AtomicBoolean(false)
    private val sessionReady = AtomicBoolean(false)

    private var webSocket: WebSocket? = null
    private var onResultCallback: ((RecognitionResult) -> Unit)? = null
    private var config: VolcengineConfig? = null
    private var taskId: String = ""

    
    private val sendLock = ReentrantLock()

    
    private val audioSendQueue = java.util.concurrent.LinkedBlockingQueue<ByteBuffer>()
    private var sendWorker: Thread? = null

    
    private var heartbeatExecutor: ScheduledExecutorService? = null

    
    private val reconnecting = AtomicBoolean(false)
    private var reconnectAttempts: Int = 0
    private val maxReconnectAttempts: Int = 5
    private val reconnectDelayMs: Long = 3000
    private var reconnectExecutor: ScheduledExecutorService? = null

    
    private val sendTimeoutMs: Long = 5000

    companion object {
        private const val TOKEN_URL = "https://nls-meta.cn-shanghai.aliyuncs.com/"
        private const val GATEWAY_URL = "wss://nls-gateway.cn-shanghai.aliyuncs.com/ws/v1"
    }

    override fun start(config: VolcengineConfig, onResult: (RecognitionResult) -> Unit) {
        this.config = config
        this.onResultCallback = onResult
        reconnectAttempts = 0
        running.set(true)
        startSendWorker()
        
        Thread({ obtainTokenAndConnect() }, "aliyun-asr-token").apply { isDaemon = true }.start()
    }

    private fun obtainTokenAndConnect() {
        val cfg = config ?: return
        if (cfg.aliyunAccessKeyId.isBlank() || cfg.aliyunAccessKeySecret.isBlank()) {
            logger.error("阿里云 AccessKey ID/Secret 未配置，无法获取 Token")
            running.set(false)
            return
        }
        val token = fetchToken(cfg.aliyunAccessKeyId, cfg.aliyunAccessKeySecret)
        if (token == null) {
            logger.error("阿里云 Token 获取失败，无法连接语音服务")
            attemptReconnect()
            return
        }
        connect(token)
    }

    
    private fun fetchToken(accessKeyId: String, accessKeySecret: String): String? {
        return try {
            val params = sortedMapOf(
                "AccessKeyId" to accessKeyId,
                "Action" to "CreateToken",
                "Format" to "JSON",
                "SignatureMethod" to "HMAC-SHA1",
                "SignatureNonce" to UUID.randomUUID().toString(),
                "SignatureVersion" to "1.0",
                "Timestamp" to isoUtcTimestamp(),
                "Version" to "2019-02-28"
            )
            val canonical = params.map { (k, v) -> "${percentEncode(k)}=${percentEncode(v)}" }.joinToString("&")
            val stringToSign = "POST&%2F&" + percentEncode(canonical)

            val mac = Mac.getInstance("HmacSHA1")
            mac.init(SecretKeySpec((accessKeySecret + "&").toByteArray(Charsets.UTF_8), "HmacSHA1"))
            val signature = Base64.getEncoder().encodeToString(mac.doFinal(stringToSign.toByteArray(Charsets.UTF_8)))

            val url = URI(TOKEN_URL + "?" + canonical + "&Signature=" + percentEncode(signature)).toURL()
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            val status = conn.getResponseCode()
            if (status == 200) {
                val body = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                val json = JsonParser.parseString(body).asJsonObject
                val token = json.getAsJsonObject("Token")?.get("Id")?.asString
                if (token != null) {
                    logger.debug("阿里云 Token 获取成功")
                } else {
                    logger.warn("阿里云 CreateToken 响应缺少 Token 字段: {}", body)
                }
                token
            } else {
                val errBody = conn.errorStream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                logger.warn("阿里云 CreateToken 失败: status={}, body={}", status, errBody)
                null
            }
        } catch (e: Exception) {
            logger.error("阿里云 Token 获取异常: ${e.message}", e)
            null
        }
    }

    private fun isoUtcTimestamp(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date())
    }

    private fun percentEncode(s: String): String {
        return java.net.URLEncoder.encode(s, Charsets.UTF_8.name())
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")
    }

    private fun connect(token: String) {
        val cfg = config ?: return
        if (cfg.aliyunAppKey.isBlank()) {
            logger.error("阿里云 AppKey 未配置")
            running.set(false)
            return
        }
        taskId = UUID.randomUUID().toString().replace("-", "")
        val url = "$GATEWAY_URL?token=$token"
        try {
            logger.info("Connecting to Aliyun NLS WebSocket...")
            httpClient.newWebSocketBuilder()
                .buildAsync(URI(url), WebSocketListener())
                .whenComplete { ws, err ->
                    if (err != null) {
                        logger.error("Failed to connect to Aliyun NLS: ${err.message}", err)
                        attemptReconnect()
                    } else {
                        webSocket = ws
                        running.set(true)
                        reconnectAttempts = 0
                        reconnecting.set(false)
                        logger.info("Aliyun WebSocket connected. taskId={}", taskId)
                    }
                }
        } catch (e: Exception) {
            logger.error("Failed to initiate connect to Aliyun NLS: ${e.message}", e)
            attemptReconnect()
        }
    }

    override fun feedAudio(audioData: ByteArray) {
        
        if (!running.get() || !sessionReady.get() || webSocket == null) return
        try {
            audioSendQueue.offer(ByteBuffer.wrap(audioData))
        } catch (e: Exception) {
            logger.error("Error queueing audio data: ${e.message}", e)
        }
    }

    
    private fun sendStartTranscription() {
        val cfg = config ?: return
        val messageId = UUID.randomUUID().toString().replace("-", "")
        val payload = mutableMapOf<String, Any>(
            "format" to "pcm",
            "sample_rate" to 16000,
            "enable_intermediate_result" to true,
            "enable_punctuation_prediction" to true,
            "enable_inverse_text_normalization" to true
        )
        
        if (cfg.boostingTableId.isNotBlank()) {
            payload["vocabulary_id"] = cfg.boostingTableId
        }
        val request = mapOf(
            "header" to mapOf(
                "message_id" to messageId,
                "task_id" to taskId,
                "namespace" to "SpeechTranscriber",
                "name" to "StartTranscription",
                "appkey" to cfg.aliyunAppKey
            ),
            "payload" to payload
        )
        sendText(request)
    }

    private fun sendStopTranscription() {
        val cfg = config ?: return
        val request = mapOf(
            "header" to mapOf(
                "message_id" to UUID.randomUUID().toString().replace("-", ""),
                "task_id" to taskId,
                "namespace" to "SpeechTranscriber",
                "name" to "StopTranscription",
                "appkey" to cfg.aliyunAppKey
            )
        )
        sendText(request)
    }

    private fun sendText(obj: Any) {
        try {
            val json = gson.toJson(obj)
            sendLock.lock()
            try {
                webSocket?.sendText(json, true)?.orTimeout(sendTimeoutMs, TimeUnit.MILLISECONDS)?.join()
            } finally {
                sendLock.unlock()
            }
        } catch (e: Exception) {
            logger.error("Error sending text frame: ${e.message}", e)
        }
    }

    
    private fun startSendWorker() {
        stopSendWorker()
        sendWorker = Thread({
            while (running.get()) {
                val current = webSocket
                if (current == null) {
                    
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
                    } finally {
                        sendLock.unlock()
                    }
                } catch (e: Exception) {
                    logger.error("Error sending audio data: ${e.message}", e)
                }
            }
        }, "aliyun-asr-sender").apply {
            isDaemon = true
            start()
        }
    }

    private fun stopSendWorker() {
        sendWorker?.interrupt()
        sendWorker = null
    }

    private fun startHeartbeat() {
        stopHeartbeat()
        val executor = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "aliyun-asr-heartbeat").apply { isDaemon = true }
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
        }, 5, 5, TimeUnit.SECONDS)
    }

    private fun stopHeartbeat() {
        heartbeatExecutor?.shutdownNow()
        heartbeatExecutor = null
    }

    override fun stop() {
        running.set(false)
        sessionReady.set(false)
        stopHeartbeat()
        reconnectExecutor?.shutdownNow()
        reconnectExecutor = null
        stopSendWorker()
        try {
            sendStopTranscription()
            
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
        logger.info("Aliyun stream recognizer stopped")
    }

    override fun isRunning(): Boolean = running.get()

    private fun attemptReconnect() {
        if (!reconnecting.compareAndSet(false, true)) return
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        if (reconnectAttempts >= maxReconnectAttempts) {
            logger.error("阿里云最大重连次数（{}）已用尽，放弃重连", maxReconnectAttempts)
            running.set(false)
            reconnecting.set(false)
            return
        }
        reconnectAttempts++
        logger.info("阿里云将在 {}ms 后重连（第 {}/{} 次）", reconnectDelayMs, reconnectAttempts, maxReconnectAttempts)
        val exec = reconnectExecutor ?: Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "aliyun-asr-reconnect").apply { isDaemon = true }
        }.also { reconnectExecutor = it }
        exec.schedule({
            if (!running.get()) {
                reconnecting.set(false)
                return@schedule
            }
            obtainTokenAndConnect()
        }, reconnectDelayMs, TimeUnit.MILLISECONDS)
    }

    
    private fun handleServerEvent(raw: String) {
        try {
            val json = JsonParser.parseString(raw).asJsonObject
            val header = json.getAsJsonObject("header") ?: return
            val name = header.get("name")?.asString ?: return
            val status = header.get("status")?.asInt ?: 20000000
            val payload = json.getAsJsonObject("payload")

            when (name) {
                "TranscriptionStarted" -> {
                    sessionReady.set(true)
                    logger.debug("Aliyun transcription started")
                }
                "TranscriptionResultChanged" -> {
                    
                }
                "SentenceEnd" -> {
                    val text = payload?.get("result")?.asString ?: ""
                    val confidence = payload?.get("confidence")?.asFloat ?: 1.0f
                    if (text.isNotBlank()) {
                        onResultCallback?.invoke(
                            RecognitionResult(
                                text = text.trim(),
                                confidence = confidence,
                                isFinal = true
                            )
                        )
                        ModLogger.writeRecognizedText(text.trim())
                    }
                }
                "TranscriptionCompleted" -> {
                    logger.info("Aliyun transcription completed")
                }
                "TaskFailed" -> {
                    val msg = header.get("status_message")?.asString ?: "unknown error"
                    logger.error("Aliyun task failed: status={}, message={}", status, msg)
                }
                else -> {
                    logger.debug("Aliyun event: name={}, status={}", name, status)
                }
            }
        } catch (e: Exception) {
            logger.error("Error parsing Aliyun event: {}", raw)
        }
    }

    
    private inner class WebSocketListener : WebSocket.Listener {

        override fun onOpen(webSocket: WebSocket) {
            logger.info("Aliyun WebSocket opened, sending StartTranscription...")
            this@AliyunStreamRecognizer.webSocket = webSocket
            sendStartTranscription()
            startHeartbeat()
            webSocket.request(1)
        }

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletableFuture<*> {
            handleServerEvent(data.toString())
            webSocket.request(1)
            return CompletableFuture.completedFuture(null)
        }

        override fun onBinary(webSocket: WebSocket, data: ByteBuffer, last: Boolean): CompletableFuture<*> {
            
            webSocket.request(1)
            return CompletableFuture.completedFuture(null)
        }

        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletableFuture<*> {
            logger.info("Aliyun WebSocket closed: code={}, reason={}", statusCode, reason)
            sessionReady.set(false)
            if (running.get()) {
                attemptReconnect()
            }
            return CompletableFuture.completedFuture(null)
        }

        override fun onError(webSocket: WebSocket, error: Throwable?) {
            logger.error("Aliyun WebSocket error: ${error?.message}", error)
            sessionReady.set(false)
            if (running.get()) {
                attemptReconnect()
            }
        }
    }
}
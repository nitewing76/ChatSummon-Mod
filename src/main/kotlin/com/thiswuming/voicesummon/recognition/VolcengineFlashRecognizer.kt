package com.thiswuming.voicesummon.recognition

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.thiswuming.voicesummon.audio.AudioUtils
import com.thiswuming.voicesummon.util.ModLogger
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.io.ByteArrayOutputStream


class VolcengineFlashRecognizer : SpeechRecognizer {

    private val logger = ModLogger.getLogger("VolcengineFlash")
    private val gson = Gson()
    private val running = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "VoiceSummon-FlashRecognizer").apply { isDaemon = true }
    }

    private var onResultCallback: ((RecognitionResult) -> Unit)? = null
    private var config: VolcengineConfig? = null

    
    private val audioBuffer = ByteArrayOutputStream()
    private val chunkDurationMs = 2500L 
    private var lastSendTime = 0L

    override fun start(config: VolcengineConfig, onResult: (RecognitionResult) -> Unit) {
        this.config = config
        this.onResultCallback = onResult
        running.set(true)
        lastSendTime = System.currentTimeMillis()
        logger.info("Flash recognizer started (chunk duration: {}ms)", chunkDurationMs)
    }

    override fun feedAudio(audioData: ByteArray) {
        if (!running.get()) return

        synchronized(audioBuffer) {
            audioBuffer.write(audioData)
        }

        
        val now = System.currentTimeMillis()
        if (now - lastSendTime >= chunkDurationMs) {
            lastSendTime = now
            val audioToSend = synchronized(audioBuffer) {
                val data = audioBuffer.toByteArray()
                audioBuffer.reset()
                data
            }

            if (audioToSend.isNotEmpty()) {
                executor.submit {
                    recognizeAudio(audioToSend)
                }
            }
        }
    }

    override fun stop() {
        running.set(false)
        
        synchronized(audioBuffer) {
            val remaining = audioBuffer.toByteArray()
            audioBuffer.reset()
            if (remaining.isNotEmpty()) {
                executor.submit {
                    recognizeAudio(remaining)
                }
            }
        }
        executor.shutdown()
        logger.info("Flash recognizer stopped")
    }

    override fun isRunning(): Boolean = running.get()

    private fun recognizeAudio(audioData: ByteArray) {
        val cfg = config ?: return

        try {
            
            val wavData = AudioUtils.pcmToWav(audioData)
            val base64Audio = java.util.Base64.getEncoder().encodeToString(wavData)

            val requestBody = mapOf(
                "user" to mapOf("uid" to cfg.apiKey),
                "audio" to mapOf("data" to base64Audio),
                "request" to mapOf("model_name" to "bigmodel")
            )

            val jsonBody = gson.toJson(requestBody)

            val url = URI("https://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash").toURL()
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            
            connection.setRequestProperty("X-Api-Key", cfg.apiKey)
            connection.setRequestProperty("X-Api-Resource-Id", "volc.bigasr.auc_turbo")
            connection.setRequestProperty("X-Api-Request-Id", UUID.randomUUID().toString())
            connection.setRequestProperty("X-Api-Sequence", "-1")
            connection.doOutput = true
            connection.connectTimeout = 10000
            connection.readTimeout = 15000

            connection.outputStream.use { os ->
                os.write(jsonBody.toByteArray(Charsets.UTF_8))
            }

            val statusCode = connection.responseCode
            val responseStatus = connection.getHeaderField("X-Api-Status-Code")
            val responseMessage = connection.getHeaderField("X-Api-Message")
            val logId = connection.getHeaderField("X-Tt-Logid")

            logger.debug("Flash API response: status={}, apiStatus={}, logId={}", statusCode, responseStatus, logId)

            if (statusCode == 200 && responseStatus == "20000000") {
                val responseBody = connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                val jsonObj = JsonParser.parseString(responseBody).asJsonObject

                val resultObj = jsonObj.getAsJsonObject("result")
                val text = resultObj?.get("text")?.asString ?: ""

                if (text.isNotBlank()) {
                    val utterances = resultObj.getAsJsonArray("utterances")
                    var confidence = 0.9f

                    
                    if (utterances != null && utterances.size() > 0) {
                        val firstUtterance = utterances[0].asJsonObject
                        val words = firstUtterance.getAsJsonArray("words")
                        if (words != null && words.size() > 0) {
                            var totalConf = 0f
                            for (word in words) {
                                totalConf += word.asJsonObject.get("confidence")?.asFloat ?: 0.9f
                            }
                            confidence = totalConf / words.size()
                        }
                    }

                    onResultCallback?.invoke(
                        RecognitionResult(
                            text = text.trim(),
                            confidence = confidence,
                            isFinal = true
                        )
                    )
                    logger.debug("Flash recognition result: text='{}', confidence={}", text, confidence)
                }
            } else {
                logger.warn("Flash API error: status={}, apiStatus={}, message={}", statusCode, responseStatus, responseMessage)
            }

            connection.disconnect()
        } catch (e: Exception) {
            logger.error("Flash recognition error: ${e.message}", e)
        }
    }
}
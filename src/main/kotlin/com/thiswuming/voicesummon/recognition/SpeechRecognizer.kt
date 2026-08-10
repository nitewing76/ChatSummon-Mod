package com.thiswuming.voicesummon.recognition

data class VolcengineConfig(
    val apiKey: String, 
    val resourceId: String,
    val cluster: String,
    val boostingTableId: String = "", 
    
    val aliyunAppKey: String = "", 
    val aliyunAccessKeyId: String = "", 
    val aliyunAccessKeySecret: String = "" 
)

data class RecognitionResult(
    val text: String,
    val confidence: Float = 1.0f,
    val isFinal: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

interface SpeechRecognizer {
    fun start(config: VolcengineConfig, onResult: (RecognitionResult) -> Unit)
    fun feedAudio(audioData: ByteArray)
    fun stop()
    fun isRunning(): Boolean
}
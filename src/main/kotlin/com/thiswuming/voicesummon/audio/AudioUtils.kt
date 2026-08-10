package com.thiswuming.voicesummon.audio

import java.io.ByteArrayOutputStream
import java.util.Base64


object AudioUtils {

    
    fun pcmToBase64(pcmData: ByteArray): String {
        return Base64.getEncoder().encodeToString(pcmData)
    }

    
    fun pcmToWav(
        pcmData: ByteArray,
        sampleRate: Int = 16000,
        channels: Int = 1,
        bitsPerSample: Int = 16
    ): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcmData.size
        val totalSize = 36 + dataSize

        val output = ByteArrayOutputStream(totalSize + 8)

        
        output.write("RIFF".toByteArray())
        output.write(intToByteArrayLE(totalSize))
        output.write("WAVE".toByteArray())

        
        output.write("fmt ".toByteArray())
        output.write(intToByteArrayLE(16)) 
        output.write(shortToByteArrayLE(1)) 
        output.write(shortToByteArrayLE(channels.toShort()))
        output.write(intToByteArrayLE(sampleRate))
        output.write(intToByteArrayLE(byteRate))
        output.write(shortToByteArrayLE(blockAlign.toShort()))
        output.write(shortToByteArrayLE(bitsPerSample.toShort()))

        
        output.write("data".toByteArray())
        output.write(intToByteArrayLE(dataSize))
        output.write(pcmData)

        return output.toByteArray()
    }

    
    fun toApiBase64(pcmData: ByteArray, format: String = "wav"): String {
        return when (format.lowercase()) {
            "wav" -> Base64.getEncoder().encodeToString(pcmToWav(pcmData))
            else -> Base64.getEncoder().encodeToString(pcmData)
        }
    }

    private fun intToByteArrayLE(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            (value shr 8 and 0xFF).toByte(),
            (value shr 16 and 0xFF).toByte(),
            (value shr 24 and 0xFF).toByte()
        )
    }

    private fun shortToByteArrayLE(value: Short): ByteArray {
        return byteArrayOf(
            (value.toInt() and 0xFF).toByte(),
            (value.toInt() shr 8 and 0xFF).toByte()
        )
    }
}
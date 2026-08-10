package com.thiswuming.voicesummon.audio

import com.thiswuming.voicesummon.util.ModLogger
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Line
import javax.sound.sampled.Mixer
import javax.sound.sampled.TargetDataLine


class AudioCaptureManager(
    private val onAudioData: (ByteArray) -> Unit,
    noiseReductionEnabled: Boolean = false,
    noiseReductionStrength: Int = 2
) {
    private val logger = ModLogger.getLogger("AudioCapture")

    private val audioFormat = AudioFormat(16000f, 16, 1, true, false)
    private var targetDataLine: TargetDataLine? = null
    private var captureThread: Thread? = null

    @Volatile
    private var running: Boolean = false

    @Volatile
    private var currentVolume: Float = 0f

    private val bufferSize: Int = 6400 
    private val buffer = ByteArray(bufferSize)

    
    private var selectedMixer: Mixer? = null

    
    @Volatile
    private var noiseReducer: NoiseReducer? = null

    init {
        setNoiseReduction(noiseReductionEnabled, noiseReductionStrength, log = false)
    }

    
    fun setNoiseReduction(enabled: Boolean, strength: Int = 2, log: Boolean = true) {
        if (enabled) {
            val clampedStrength = strength.coerceIn(1, 3)
            val existing = noiseReducer
            if (existing == null) {
                noiseReducer = NoiseReducer(strength = clampedStrength).apply {
                    this.enabled = true
                }
                if (log) logger.info("Noise reduction ENABLED (strength={})", clampedStrength)
            } else {
                existing.enabled = true
                existing.strength = clampedStrength
                if (log) logger.info("Noise reduction updated (strength={})", clampedStrength)
            }
        } else {
            if (noiseReducer != null) {
                noiseReducer = null
                if (log) logger.info("Noise reduction DISABLED")
            }
        }
    }

    fun isNoiseReductionEnabled(): Boolean = noiseReducer?.enabled == true

    fun getNoiseReductionStrength(): Int = noiseReducer?.strength ?: 0

    fun start() {
        start(null)
    }

    
    fun start(mixer: Mixer?) {
        if (running) {
            logger.warn("Audio capture already running")
            return
        }

        try {
            selectedMixer = mixer
            targetDataLine = openLine(mixer)
            targetDataLine?.start()

            
            noiseReducer?.reset()

            running = true
            captureThread = Thread({
                captureLoop()
            }, "VoiceSummon-AudioCapture").apply {
                isDaemon = true
                start()
            }

            val mixerInfo = selectedMixer?.mixerInfo
            val mixerName = mixerInfo?.name ?: "default"
            
            
            val mixerDesc = mixerInfo?.description ?: ""
            logger.info("Audio capture started. Format: 16kHz, 16bit, mono, mixer: {} (desc: {})", mixerName, mixerDesc)

            
            val allMics = listMicrophones().map { it.mixerInfo.name }
            logger.info("Available microphones ({}): {}", allMics.size, allMics)
        } catch (e: Exception) {
            logger.error("Failed to start audio capture: ${e.message}", e)
            running = false
        }
    }

    fun stop() {
        running = false
        try {
            captureThread?.interrupt()
            targetDataLine?.stop()
            targetDataLine?.close()
        } catch (e: Exception) {
            logger.warn("Error stopping audio capture: ${e.message}")
        }
        targetDataLine = null
        captureThread = null
        logger.info("Audio capture stopped")
    }

    fun isRunning(): Boolean = running

    fun getCurrentVolume(): Float = currentVolume

    private fun captureLoop() {
        logger.debug("Audio capture loop started")
        var firstRead = true
        var totalBytes = 0L
        while (running && !Thread.currentThread().isInterrupted) {
            val line = targetDataLine ?: break
            try {
                val bytesRead = line.read(buffer, 0, buffer.size)

                if (bytesRead > 0) {
                    totalBytes += bytesRead

                    
                    val rawChunk = buffer.copyOf(bytesRead)
                    val processedChunk = noiseReducer?.process(rawChunk) ?: rawChunk

                    
                    currentVolume = calculateRms(processedChunk, processedChunk.size)

                    if (firstRead) {
                        firstRead = false
                        logger.info("Audio capture reading data. First chunk size: {} bytes, noiseReducer: {}",
                            bytesRead, if (noiseReducer != null) "ON(strength=${noiseReducer!!.strength})" else "OFF")
                    }

                    
                    onAudioData(processedChunk)
                } else if (bytesRead < 0) {
                    
                    logger.warn("Audio line closed (bytesRead={}). Attempting reconnect...", bytesRead)
                    if (!tryReconnect()) break
                }
            } catch (e: InterruptedException) {
                break
            } catch (e: Exception) {
                if (!running) break
                
                logger.error("Audio capture error, attempting reconnect: ${e.message}")
                if (!tryReconnect()) break
            }
        }
        logger.info("Audio capture loop ended. Total bytes read: {}", totalBytes)
    }

    
    private fun tryReconnect(): Boolean {
        
        try {
            targetDataLine?.stop()
            targetDataLine?.close()
        } catch (_: Exception) {
        }

        val maxAttempts = 15
        for (attempt in 1..maxAttempts) {
            if (!running) return false
            try {
                val newLine = openLine(selectedMixer)
                newLine.start()
                targetDataLine = newLine
                currentVolume = 0f
                logger.info("Audio capture reconnected successfully (attempt {}/{})", attempt, maxAttempts)
                return true
            } catch (e: Exception) {
                logger.warn("Audio reconnect attempt {}/{} failed: {}", attempt, maxAttempts, e.message)
                try {
                    Thread.sleep(1000)
                } catch (_: InterruptedException) {
                    return false
                }
            }
        }
        logger.error("Audio reconnect failed after {} attempts. Giving up.", maxAttempts)
        return false
    }

    
    private fun openLine(mixer: Mixer?): TargetDataLine {
        var line: TargetDataLine = if (mixer != null) {
            try {
                mixer.getLine(Line.Info(TargetDataLine::class.java)) as TargetDataLine
            } catch (e: Exception) {
                logger.warn("Failed to open selected mixer '{}', falling back to default: {}", mixer.mixerInfo.name, e.message)
                AudioSystem.getTargetDataLine(audioFormat)
            }
        } else {
            AudioSystem.getTargetDataLine(audioFormat)
        }

        
        try {
            line.open(audioFormat, bufferSize * 2)
        } catch (e: Exception) {
            logger.warn("Failed to open line with format, opening default: {}", e.message)
            try { line.close() } catch (_: Exception) {}
            line = AudioSystem.getTargetDataLine(audioFormat)
            line.open(audioFormat, bufferSize * 2)
        }
        return line
    }

    
    private fun calculateRms(audioData: ByteArray, length: Int): Float {
        if (length < 2) return 0f

        var sum = 0L
        var count = 0

        
        for (i in 0 until length - 1 step 2) {
            
            val sample = ((audioData[i + 1].toInt() shl 8) or (audioData[i].toInt() and 0xFF)).toShort()
            sum += (sample * sample).toLong()
            count++
        }

        if (count == 0) return 0f

        val rms = kotlin.math.sqrt(sum.toDouble() / count)
        
        return (rms / 32768.0).toFloat().coerceIn(0f, 1f)
    }

    companion object {
        
        fun listMicrophones(): List<Mixer> {
            val result = mutableListOf<Mixer>()
            val seenNames = mutableSetOf<String>()
            for (mixerInfo in AudioSystem.getMixerInfo()) {
                val name = mixerInfo.name
                val mixer = AudioSystem.getMixer(mixerInfo)
                if (!supportsInput(mixer)) continue
                
                if (!seenNames.add(name)) continue
                result.add(mixer)
            }
            return result
        }

        
        private fun supportsInput(mixer: Mixer): Boolean {
            return try {
                mixer.isLineSupported(Line.Info(TargetDataLine::class.java))
            } catch (e: Exception) {
                false
            }
        }

        
        fun getMicrophoneByIndex(index: Int): Mixer? {
            val list = listMicrophones()
            return if (index in list.indices) list[index] else null
        }

        
        fun getMicrophoneByName(name: String): Mixer? {
            if (name.isBlank()) return null
            val microphones = listMicrophones()
            return microphones.firstOrNull { it.mixerInfo.name == name }
                ?: microphones.firstOrNull { it.mixerInfo.name.contains(name, ignoreCase = true) }
        }
    }
}
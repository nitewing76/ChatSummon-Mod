package com.thiswuming.voicesummon.audio

import com.thiswuming.voicesummon.util.ModLogger
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt


class NoiseReducer(
    
    strength: Int = 2,
    private val sampleRate: Int = 16000
) {
    private val logger = ModLogger.getLogger("NoiseReducer")

    companion object {
        
        private const val FRAME_SIZE = 256
        
        private const val HOP_SIZE = FRAME_SIZE / 2
        
        private const val NOISE_INIT_FRAMES = 20
        
        private const val NOISE_UPDATE_ALPHA = 0.9f
        
        private const val LOW_ENERGY_RATIO = 1.2f
    }

    
    private val fft = FastFourierTransform(FRAME_SIZE)
    private val hannWindow: FloatArray = createHannWindow(FRAME_SIZE)

    
    private val inputBuffer = ShortArray(FRAME_SIZE * 4)
    private var inputPos: Int = 0

    
    private val outputOverlap = FloatArray(FRAME_SIZE) 
    private val outputBuffer = ShortArray(FRAME_SIZE * 4)
    private var outputPos: Int = 0

    
    private val noisePower = FloatArray(FRAME_SIZE)
    private var initNoiseFramesLeft: Int = NOISE_INIT_FRAMES

    
    private var previousFrameTail: FloatArray? = null

    
    var enabled: Boolean = true

    
    var strength: Int = strength.coerceIn(1, 3)
        set(value) {
            val clamped = value.coerceIn(1, 3)
            if (clamped != field) {
                logger.info("NoiseReducer strength changed: {} → {}", field, clamped)
                field = clamped
            }
        }

    init {
        require(FRAME_SIZE and (FRAME_SIZE - 1) == 0) { "FRAME_SIZE must be power of 2" }
        require(HOP_SIZE <= FRAME_SIZE) { "HOP_SIZE must be <= FRAME_SIZE" }
        logger.info(
            "NoiseReducer initialized. strength={}, sampleRate={}, frameSize={} ({}ms), hopSize={} ({}ms)",
            this.strength, sampleRate, FRAME_SIZE,
            FRAME_SIZE * 1000f / sampleRate,
            HOP_SIZE, HOP_SIZE * 1000f / sampleRate
        )
    }

    
    fun process(pcmBytes: ByteArray): ByteArray {
        if (!enabled || pcmBytes.isEmpty()) return pcmBytes

        val samplesIn = pcmToShorts(pcmBytes)
        var srcPos = 0
        val totalIn = samplesIn.size

        
        while (srcPos < totalIn) {
            val space = inputBuffer.size - inputPos
            val toCopy = (totalIn - srcPos).coerceAtMost(space)
            for (i in 0 until toCopy) {
                inputBuffer[inputPos + i] = samplesIn[srcPos + i]
            }
            srcPos += toCopy
            inputPos += toCopy

            
            while (inputPos >= FRAME_SIZE) {
                processFrame(inputBuffer.copyOfRange(0, FRAME_SIZE))
                
                inputBuffer.copyInto(destination = inputBuffer, destinationOffset = 0, startIndex = HOP_SIZE, endIndex = inputPos)
                inputPos -= HOP_SIZE
            }
        }

        
        val outSamples = ShortArray(totalIn)
        val avail = outputPos.coerceAtMost(totalIn)
        for (i in 0 until avail) outSamples[i] = outputBuffer[i]
        
        if (avail < outputPos) {
            outputBuffer.copyInto(destination = outputBuffer, destinationOffset = 0, startIndex = avail, endIndex = outputPos)
            outputPos -= avail
        } else {
            outputPos = 0
        }

        return shortsToPcm(outSamples)
    }

    
    private fun processFrame(frameShorts: ShortArray) {
        
        val x = FloatArray(FRAME_SIZE)
        for (i in 0 until FRAME_SIZE) x[i] = frameShorts[i] / 32768f

        
        for (i in 0 until FRAME_SIZE) x[i] *= hannWindow[i]

        
        val X = fft.forward(x)

        
        val mag = FloatArray(FRAME_SIZE)
        val power = FloatArray(FRAME_SIZE)
        var totalEnergy = 0f
        for (k in 0 until FRAME_SIZE) {
            val re = X[2 * k]
            val im = X[2 * k + 1]
            val p = re * re + im * im
            power[k] = p
            mag[k] = sqrt(p)
            totalEnergy += p
        }
        totalEnergy /= FRAME_SIZE.toFloat()

        
        if (initNoiseFramesLeft > 0) {
            
            for (k in 0 until FRAME_SIZE) {
                noisePower[k] += power[k] / NOISE_INIT_FRAMES.toFloat()
            }
            initNoiseFramesLeft--
            if (initNoiseFramesLeft == 0) {
                logger.info("Noise estimation complete. Initial noise floor (avg power): {:.6f}",
                    noisePower.average().toFloat())
            }
        } else {
            
            val avgNoise = noisePower.average().toFloat()
            if (avgNoise > 0f && totalEnergy < avgNoise * LOW_ENERGY_RATIO) {
                for (k in 0 until FRAME_SIZE) {
                    noisePower[k] = NOISE_UPDATE_ALPHA * noisePower[k] + (1f - NOISE_UPDATE_ALPHA) * power[k]
                }
            }
        }

        
        val (alpha, beta) = when (strength) {
            1 -> 1.5f to 0.05f     
            3 -> 3.0f to 0.02f     
            else -> 2.2f to 0.03f  
        }

        for (k in 0 until FRAME_SIZE) {
            val nP = noisePower[k]
            if (nP <= 0f) continue
            
            var estPower = power[k] - alpha * nP
            val floor = beta * beta * nP
            if (estPower < floor) estPower = floor
            val newMag = sqrt(estPower)
            
            val gain = if (mag[k] > 1e-8f) (newMag / mag[k]).coerceIn(0f, 1f) else 0f
            X[2 * k] *= gain      
            X[2 * k + 1] *= gain  
        }

        
        val y = fft.inverse(X)

        
        for (i in 0 until FRAME_SIZE) y[i] *= hannWindow[i]

        
        val hop = HOP_SIZE
        if (previousFrameTail != null) {
            val tail = previousFrameTail!!
            for (i in 0 until hop) {
                val s = y[i] + tail[i]
                val sample = (s * 32768f).toInt().coerceIn(-32768, 32767).toShort()
                if (outputPos < outputBuffer.size) {
                    outputBuffer[outputPos++] = sample
                }
            }
        } else {
            
            for (i in 0 until hop) {
                val s = y[i]
                val sample = (s * 32768f).toInt().coerceIn(-32768, 32767).toShort()
                if (outputPos < outputBuffer.size) {
                    outputBuffer[outputPos++] = sample
                }
            }
        }
        previousFrameTail = y.copyOfRange(hop, FRAME_SIZE)
    }

    
    fun reset() {
        inputPos = 0
        outputPos = 0
        initNoiseFramesLeft = NOISE_INIT_FRAMES
        previousFrameTail = null
        for (i in 0 until FRAME_SIZE) {
            noisePower[i] = 0f
            outputOverlap[i] = 0f
        }
        logger.info("NoiseReducer state reset")
    }

    
    
    

    private fun createHannWindow(n: Int): FloatArray {
        val w = FloatArray(n)
        for (i in 0 until n) {
            w[i] = (0.5 * (1.0 - cos(2.0 * PI * i.toDouble() / (n - 1).toDouble()))).toFloat()
        }
        
        
        var sumSq = 0f
        for (v in w) sumSq += v * v
        val comp = sqrt(n / (2f * sumSq)) 
        for (i in 0 until n) w[i] *= comp
        return w
    }

    
    private fun pcmToShorts(bytes: ByteArray): ShortArray {
        val n = bytes.size / 2
        val out = ShortArray(n)
        for (i in 0 until n) {
            val lo = bytes[2 * i].toInt() and 0xFF
            val hi = bytes[2 * i + 1].toInt()
            out[i] = ((hi shl 8) or lo).toShort()
        }
        return out
    }

    
    private fun shortsToPcm(shorts: ShortArray): ByteArray {
        val out = ByteArray(shorts.size * 2)
        for (i in shorts.indices) {
            val v = shorts[i].toInt()
            out[2 * i] = (v and 0xFF).toByte()
            out[2 * i + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return out
    }

    
    
    

    private class FastFourierTransform(private val n: Int) {
        private val log2n: Int = run {
            var k = 0
            var v = n
            while (v > 1) {
                v = v shr 1; k++
            }
            k
        }
        private val reverse: IntArray = IntArray(n) { it.reverseBits(log2n) }

        
        fun forward(x: FloatArray): FloatArray {
            require(x.size == n)
            
            val out = FloatArray(2 * n)
            for (i in 0 until n) {
                out[2 * reverse[i]] = x[i]
                out[2 * reverse[i] + 1] = 0f
            }
            
            butterfly(out, sign = -1f)
            return out
        }

        
        fun inverse(X: FloatArray): FloatArray {
            require(X.size == 2 * n)
            val buf = X.copyOf()
            butterfly(buf, sign = +1f)
            
            val out = FloatArray(n)
            for (i in 0 until n) {
                out[i] = buf[2 * i] / n.toFloat()
            }
            return out
        }

        
        private fun butterfly(data: FloatArray, sign: Float) {
            var size = 2
            while (size <= n) {
                val half = size / 2
                val wReBase = kotlin.math.cos(2.0 * PI / size).toFloat()
                val wImBase = (sign * kotlin.math.sin(2.0 * PI / size)).toFloat()
                for (start in 0 until n step size) {
                    var wRe = 1f
                    var wIm = 0f
                    for (k in 0 until half) {
                        val i1 = 2 * (start + k)
                        val i2 = 2 * (start + k + half)
                        
                        val tRe = wRe * data[i2] - wIm * data[i2 + 1]
                        val tIm = wRe * data[i2 + 1] + wIm * data[i2]
                        
                        data[i2] = data[i1] - tRe
                        data[i2 + 1] = data[i1 + 1] - tIm
                        
                        data[i1] += tRe
                        data[i1 + 1] += tIm
                        
                        val nr = wRe * wReBase - wIm * wImBase
                        val ni = wRe * wImBase + wIm * wReBase
                        wRe = nr; wIm = ni
                    }
                }
                size *= 2
            }
        }

        private fun Int.reverseBits(bits: Int): Int {
            var x = this
            var r = 0
            for (i in 0 until bits) {
                r = (r shl 1) or (x and 1)
                x = x shr 1
            }
            return r
        }
    }
}

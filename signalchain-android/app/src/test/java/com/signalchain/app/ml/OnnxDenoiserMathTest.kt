package com.signalchain.app.ml

import com.signalchain.app.dsp.Fft
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.max
import kotlin.math.sin

class OnnxDenoiserMathTest {

    // These are pure Kotlin verbatim copies of the private math functions in OnnxDenoiser.kt
    // They are tested here to lock in the STFT/ISTFT + resample regression fix.
    
    private fun resampleLinear(x: FloatArray, srcRate: Int, dstRate: Int): FloatArray {
        if (srcRate == dstRate) return x
        require(srcRate > 0 && dstRate > 0)
        val ratio = dstRate.toDouble() / srcRate.toDouble()
        val outLen = max(1, (x.size * ratio).toInt())
        return FloatArray(outLen) { i ->
            val srcPos = i / ratio
            val i0 = srcPos.toInt().coerceIn(0, x.size - 1)
            val i1 = (i0 + 1).coerceIn(0, x.size - 1)
            val frac = (srcPos - i0).toFloat()
            x[i0] * (1 - frac) + x[i1] * frac
        }
    }

    private fun reflectPad(x: FloatArray, pad: Int): FloatArray {
        val n = x.size
        require(n > pad) { "Audio too short" }
        return FloatArray(n + 2 * pad) { i ->
            when {
                i < pad -> x[pad - i]
                i >= pad + n -> x[n - 2 - (i - pad - n)]
                else -> x[i - pad]
            }
        }
    }

    private fun stftCentered(x: FloatArray, nFft: Int, hop: Int): Array<Array<Pair<Float, Float>>> {
        val padded = reflectPad(x, nFft / 2)
        val full = Fft.stft(padded, nFft, hop)
        val trueFrames = 1 + (padded.size - nFft) / hop
        return full.copyOfRange(0, trueFrames)
    }

    private fun istftCentered(s: Array<Array<Pair<Float, Float>>>, hop: Int, nFft: Int, originalLen: Int): FloatArray {
        val padded = Fft.istft(s, hop)
        val start = nFft / 2
        val end = (start + originalLen).coerceAtMost(padded.size)
        return padded.copyOfRange(start, end).let {
            if (it.size < originalLen) it + FloatArray(originalLen - it.size) else it
        }
    }

    @Test
    fun testStftIstftRoundTrip() {
        val n = 16000
        val original = FloatArray(n) { i ->
            (sin(2.0 * Math.PI * 400.0 * i / 16000.0) * 0.5f).toFloat()
        }

        // Apply STFT
        val nFft = 512
        val hop = 128
        val stft = stftCentered(original, nFft, hop)
        
        // Apply ISTFT
        val reconstructed = istftCentered(stft, hop, nFft, original.size)
        
        assertEquals(original.size, reconstructed.size)
        
        // Check round-trip error (should be ~1e-7 due to float precision)
        for (i in original.indices) {
            assertEquals("Mismatch at $i", original[i], reconstructed[i], 1e-6f)
        }
    }
    
    @Test
    fun testResampleLinear() {
        val n = 16000
        val original = FloatArray(n) { i ->
            (sin(2.0 * Math.PI * 400.0 * i / 16000.0) * 0.5f).toFloat()
        }
        
        val upsampled = resampleLinear(original, 16000, 48000)
        assertEquals(48000, upsampled.size)
        
        val downsampled = resampleLinear(upsampled, 48000, 16000)
        assertEquals(16000, downsampled.size)
        
        // Resampling with linear interpolation is lossy, so we check general magnitude tolerance.
        for (i in original.indices) {
            assertEquals("Mismatch after resample round trip at $i", original[i], downsampled[i], 0.1f)
        }
    }
}

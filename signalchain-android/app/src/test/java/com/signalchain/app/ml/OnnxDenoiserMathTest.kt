package com.signalchain.app.ml

import com.signalchain.app.dsp.Fft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.*

class OnnxDenoiserMathTest {

    // These are pure Kotlin verbatim copies of the private math functions in OnnxDenoiser.kt
    // They are tested here to lock in the STFT/ISTFT + resample regression fix.
    
    @Test
    fun testStftIstftRoundTrip() {
        val n = 16000
        val original = FloatArray(n) { i ->
            (sin(2.0 * Math.PI * 400.0 * i / 16000.0) * 0.5f).toFloat()
        }

        // Apply STFT
        val nFft = 512
        val hop = 128
        val stft = OnnxDenoiser.stftCentered(original, nFft, hop)
        
        // Apply ISTFT
        val reconstructed = OnnxDenoiser.istftCentered(stft, hop, nFft, original.size)
        
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
        
        val upsampled = OnnxDenoiser.resampleLinear(original, 16000, 48000)
        assertEquals(48000, upsampled.size)
        
        val downsampled = OnnxDenoiser.resampleLinear(upsampled, 48000, 16000)
        assertEquals(16000, downsampled.size)
        
        // Resampling with linear interpolation is lossy, so we check general magnitude tolerance.
        for (i in original.indices) {
            assertEquals("Mismatch after resample round trip at $i", original[i], downsampled[i], 0.1f)
        }
    }

    @Test
    fun testResampleAntiAliasSuppression() {
        val fsIn = 48000
        val fsOut = 16000

        // 1. 13 kHz tone suppression test (must be < -60 dB)
        val fTone13k = 13000.0
        val n = 48000 // 1 second
        val input13k = FloatArray(n) { i ->
            sin(2.0 * Math.PI * fTone13k * i / fsIn).toFloat()
        }
        val resampled13k = OnnxDenoiser.resampleLinear(input13k, fsIn, fsOut)
        assertEquals(16000, resampled13k.size)

        // Ignore edge transients from filter window (half-width w = 16/fc ≈ 51 samples)
        val margin = 200
        val steady13k = resampled13k.copyOfRange(margin, resampled13k.size - margin)
        val peakAmp13k = steady13k.maxOf { abs(it) }
        val peakDb13k = 20.0 * log10(peakAmp13k.toDouble())
        assertTrue(
            "13 kHz tone should be suppressed below -60 dB, was ${peakDb13k} dB",
            peakDb13k < -60.0
        )

        // 2. 10 kHz tone: 48 kHz -> 16 kHz, RMS-amplitude must be < 0.01
        val fTone10k = 10000.0
        val input10k = FloatArray(n) { i ->
            sin(2.0 * Math.PI * fTone10k * i / fsIn).toFloat()
        }
        val resampled10k = OnnxDenoiser.resampleLinear(input10k, fsIn, fsOut)
        val steady10k = resampled10k.copyOfRange(margin, resampled10k.size - margin)
        val rms10k = sqrt(steady10k.map { it * it.toDouble() }.average())
        assertTrue("10 kHz RMS amplitude must be < 0.01, was $rms10k", rms10k < 0.01)

        // 3. 1 kHz tone: 48 kHz -> 16 kHz, amplitude must be 1.0 +/- 0.02
        val fTone1k = 1000.0
        val input1k = FloatArray(n) { i ->
            sin(2.0 * Math.PI * fTone1k * i / fsIn).toFloat()
        }
        val resampled1k = OnnxDenoiser.resampleLinear(input1k, fsIn, fsOut)
        val steady1k = resampled1k.copyOfRange(margin, resampled1k.size - margin)
        val peakAmp1k = steady1k.maxOf { abs(it) }
        assertEquals("1 kHz tone amplitude must be 1.0 +/- 0.02", 1.0, peakAmp1k.toDouble(), 0.02)
    }
}

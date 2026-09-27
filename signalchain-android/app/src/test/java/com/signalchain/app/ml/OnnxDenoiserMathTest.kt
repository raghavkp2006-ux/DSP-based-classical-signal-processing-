package com.signalchain.app.ml

import com.signalchain.app.dsp.Fft
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.max
import kotlin.math.sin

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
}

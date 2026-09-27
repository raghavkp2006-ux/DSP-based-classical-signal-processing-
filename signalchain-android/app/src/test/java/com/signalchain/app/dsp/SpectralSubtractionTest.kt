package com.signalchain.app.dsp

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.math.sin

class SpectralSubtractionTest {
    @Test
    fun testSpectralSubtractionImprovesSnr() {
        val sampleRate = 16000
        val n = 16000
        val fp = FrameParams.create(n, sampleRate)
        val random = Random(42)
        
        val clean = FloatArray(n) { i -> 
            if (i > 1600) (sin(2.0 * Math.PI * 500.0 * i / sampleRate) * 0.5).toFloat() else 0f 
        }
        val noiseAmp = 0.2f
        val noisy = FloatArray(n) { i -> clean[i] + (random.nextFloat() * 2 - 1) * noiseAmp }
        
        // Assume first 10 frames are pure noise for PSD estimation
        val isSpeech = BooleanArray(fp.numFrames) { it >= 10 }
        val psd = NoisePsd.estimate(noisy, fp, isSpeech)
        
        val denoised = SpectralSubtraction.apply(noisy, fp, psd, n, alpha = 1.2, beta = 0.1)
        
        // Calculate noise power during the silence (first 1600 samples)
        var maxNoisy = 1e-6f
        var maxDenoised = 1e-6f
        for (v in noisy) { if (Math.abs(v) > maxNoisy) maxNoisy = Math.abs(v) }
        for (v in denoised) { if (Math.abs(v) > maxDenoised) maxDenoised = Math.abs(v) }

        var noisePowerBefore = 0.0
        var noisePowerAfter = 0.0
        for (i in 0 until 1600) {
            val vB = noisy[i] / maxNoisy
            noisePowerBefore += vB * vB
            val vA = denoised[i] / maxDenoised
            noisePowerAfter += vA * vA
        }
        
        assertTrue("Noise power should improve. Before: $noisePowerBefore, After: $noisePowerAfter", noisePowerAfter < noisePowerBefore * 0.5)
    }
}

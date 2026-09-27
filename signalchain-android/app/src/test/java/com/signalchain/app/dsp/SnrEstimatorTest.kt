package com.signalchain.app.dsp

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random
import kotlin.math.sin
import kotlin.math.sqrt

class SnrEstimatorTest {
    @Test
    fun testSnrEstimate() {
        val fs = 16000
        val n = fs * 2
        val fp = FrameParams.create(n, fs)
        val random = Random(42)
        
        // Construct a signal with ~10 dB SNR
        // Signal power = A^2 / 2 for sine wave. For A=0.5, power = 0.125
        val signalPower = 0.125
        val targetSnrLinear = Math.pow(10.0, 10.0 / 10.0) // SNR = 10 -> Linear = 10
        val noisePower = signalPower / targetSnrLinear // 0.0125
        val noiseAmp = sqrt(noisePower * 12).toFloat()
        
        val input = FloatArray(n) { i ->
            val speech = if (i > fs / 2 && i < 3 * fs / 2) (sin(2.0 * Math.PI * 400.0 * i / fs) * 0.5).toFloat() else 0f
            val noise = (random.nextFloat() - 0.5f) * noiseAmp
            speech + noise
        }
        
        val isSpeech = BooleanArray(fp.numFrames) { i ->
            val t = i * fp.hopLen / fs.toDouble()
            t > 0.5 && t < 1.5
        }
        
        val snr = SnrEstimator.estimateSnr(input, fp, isSpeech)
        
        // The estimate won't be exactly 10 due to finite length and estimator approximations, 
        // but should be reasonably close to 10 dB.
        assertTrue("Estimated SNR ($snr) should be near 10dB", snr in 5.0..15.0)
    }
}

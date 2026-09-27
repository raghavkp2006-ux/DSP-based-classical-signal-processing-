package com.signalchain.app.dsp

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class NoisePsdTest {
    @Test
    fun testNoisePsdEstimate() {
        val sampleRate = 16000
        val fp = FrameParams.create(16000, sampleRate)
        
        // Generate pure white noise with known variance
        val noiseVariance = 0.01
        val noiseAmp = sqrt(noiseVariance * 12).toFloat() // uniform distribution
        
        val signal = FloatArray(16000) { ((Math.random() - 0.5) * noiseAmp).toFloat() }
        
        // All frames marked as non-speech
        val s = BooleanArray(fp.numFrames) { false }
        
        val psd = NoisePsd.estimate(signal, fp, s)
        
        // PSD should be roughly uniform and positive
        for (i in psd.indices) {
            assertTrue("PSD must be non-negative", psd[i] >= 0f)
            assertTrue("PSD should not be zero for white noise", psd[i] > 1e-6f)
        }
        
        val avgPsd = psd.average()
        assertTrue("Average PSD $avgPsd is reasonable", avgPsd > 0.001 && avgPsd < 10.0)
    }
}

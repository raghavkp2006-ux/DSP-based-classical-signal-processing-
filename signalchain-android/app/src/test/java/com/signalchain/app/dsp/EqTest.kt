package com.signalchain.app.dsp

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class EqTest {
    @Test
    fun testEqCorners() {
        val fs = 16000
        val n = 16000
        
        // Input: mix of 40Hz (rumble), 1kHz (mid), and 3.5kHz (high)
        val fLow = 40.0
        val fMid = 1500.0
        val fHigh = 3500.0
        
        val input = FloatArray(n) { i ->
            val t = i / fs.toDouble()
            (sin(2 * Math.PI * fLow * t) + sin(2 * Math.PI * fMid * t) + sin(2 * Math.PI * fHigh * t)).toFloat()
        }
        
        val output = Eq.apply(input, fs, gain = 1.0)
        
        // Measure energies of each component using simple DFT at those frequencies
        fun getMagnitude(x: FloatArray, freq: Double): Double {
            var re = 0.0
            var im = 0.0
            for (i in x.indices) {
                val t = i / fs.toDouble()
                val ang = 2 * Math.PI * freq * t
                re += x[i] * Math.cos(ang)
                im -= x[i] * Math.sin(ang)
            }
            return Math.sqrt(re * re + im * im) / n
        }
        
        val inLowMag = getMagnitude(input, fLow)
        val inMidMag = getMagnitude(input, fMid)
        val inHighMag = getMagnitude(input, fHigh)
        
        val outLowMag = getMagnitude(output, fLow)
        val outMidMag = getMagnitude(output, fMid)
        val outHighMag = getMagnitude(output, fHigh)
        
        // Low should be heavily suppressed (< 80Hz)
        assertTrue("40Hz should be suppressed: $outLowMag vs $inLowMag", outLowMag < inLowMag * 0.85)
        
        // Mid should be boosted 
        assertTrue("1.5kHz should be boosted: $outMidMag vs $inMidMag", outMidMag > inMidMag * 1.1)
        
        // High should be boosted (in the 2.5k-4k range)
        assertTrue("3.5kHz should be boosted: $outHighMag vs $inHighMag", outHighMag > inHighMag * 1.1)
    }
}

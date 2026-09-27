package com.signalchain.app.dsp

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CompressorTest {
    @Test
    fun testCompressorLimits() {
        val fs = 16000
        val n = fs * 2
        
        // Signal with peaks exceeding 1.0
        val input = FloatArray(n) { i ->
            (Math.sin(2 * Math.PI * 400.0 * i / fs) * 2.0).toFloat()
        }
        
        // Apply compression
        val compressed = Compressor.compress(input, fs, ratio = 4.0, makeup = 0.0)
        
        val maxIn = input.maxOf { abs(it) }
        val maxOut = compressed.maxOf { abs(it) }
        
        assertTrue("Compressor should reduce gain for high peaks", maxOut < maxIn)
        
        // Check Normalize and Limit
        val limitResult = Compressor.normalizeAndLimit(compressed, input)
        val finalMax = limitResult.audio.maxOf { abs(it) }
        
        assertTrue("Normalize and limit should not exceed 1.0, got $finalMax", finalMax <= 1.0f)
        assertTrue("Clips percentage should be recorded", limitResult.clipsPct >= 0.0)
    }
}

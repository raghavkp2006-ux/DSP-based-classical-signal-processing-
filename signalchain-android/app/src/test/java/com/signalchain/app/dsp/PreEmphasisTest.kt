package com.signalchain.app.dsp

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sin

class PreEmphasisTest {
    @Test
    fun testPreEmphasisRoundTrip() {
        val n = 16000
        val signal = FloatArray(n) { i -> sin(2.0 * Math.PI * 440.0 * i / 16000.0).toFloat() }
        
        val emp = PreEmphasis.apply(signal)
        val deemp = PreEmphasis.deEmphasize(emp)
        
        for (i in signal.indices) {
            assertEquals("Round trip failed at index $i", signal[i], deemp[i], 1e-5f)
        }
    }
}

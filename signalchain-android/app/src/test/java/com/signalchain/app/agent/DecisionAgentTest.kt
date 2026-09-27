package com.signalchain.app.agent

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random
import kotlin.math.sin

class DecisionAgentTest {
    @Test
    fun testDecisionAgentRouting() {
        // Branch 1: High SNR > 15 -> Light touch
        val decision1 = DecisionAgent.decide(snr = 20.0, stationary = true, activity = 0.5)
        assertEquals("Light touch", decision1.mode)
        assertEquals(false, decision1.params.useSpectralSubtraction)
        assertEquals(false, decision1.params.useEq)
        assertEquals(1.0, decision1.params.compressionRatio, 1e-6)
        
        // Branch 2: Moderate SNR + Stationary noise -> Classical DSP
        val decision2 = DecisionAgent.decide(snr = 10.0, stationary = true, activity = 0.5)
        assertEquals("Classical DSP", decision2.mode)
        assertEquals(true, decision2.params.useSpectralSubtraction)
        assertEquals(false, decision2.params.useMlPostfilter) // default is false
        
        // Branch 3: Low SNR -> Full adaptive
        val decision3 = DecisionAgent.decide(snr = 4.0, stationary = true, activity = 0.5)
        assertEquals("Full adaptive", decision3.mode)
        assertEquals(true, decision3.params.useMlPostfilter)
        assertEquals(1.45, decision3.params.alpha, 1e-6)
        
        // Branch 3 alternative: Non-stationary noise -> Full adaptive
        val decision4 = DecisionAgent.decide(snr = 12.0, stationary = false, activity = 0.5)
        assertEquals("Full adaptive", decision4.mode)
        assertEquals(true, decision4.params.useMlPostfilter)
    }

}

package com.signalchain.app.agent

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sin
import kotlin.random.Random

class DecisionRoutingTest {
    @Test
    fun seededSyntheticAudioReachesAllThreePolicies() {
        val signals = listOf(
            "Light touch" to syntheticSignal("light", 10),
            "Classical DSP" to syntheticSignal("classical", 20),
            "Full adaptive" to syntheticSignal("full", 30)
        )
        signals.forEach { (expectedMode, samples) ->
            val stats = AudioAnalyzer.analyze(samples, 16_000)
            val decision = DecisionAgent.decide(stats.snrDb, stats.stationaryNoise, stats.speechActivityRatio)
            assertEquals(expectedMode, decision.mode)
            assertEquals(expectedMode == "Full adaptive", decision.params.useMlPostfilter)
        }
    }

    private fun syntheticSignal(kind: String, seed: Int): FloatArray {
        val random = Random(seed)
        val rate = 16_000
        return FloatArray(rate * 2) { i ->
            val noise = (random.nextFloat() - 0.5f) * 0.0002f
            val speechGate = if (i in 3_200 until 24_000) 1.0 else 0.0
            val speech = 0.16 * sin(2 * Math.PI * 223 * i / rate) * speechGate
            val value = when (kind) {
                "light" -> 0.018 * sin(2 * Math.PI * 73 * i / rate) + speech
                "classical" -> 0.035 * (sin(2 * Math.PI * 2_113 * i / rate) +
                    sin(2 * Math.PI * 3_719 * i / rate)) + speech
                "full" -> 0.01 * sin(2 * Math.PI * 611 * i / rate)
                else -> error("Unknown signal: $kind")
            }
            (value + noise).toFloat()
        }
    }
}

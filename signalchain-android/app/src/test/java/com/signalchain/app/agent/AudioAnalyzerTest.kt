package com.signalchain.app.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import kotlin.math.abs
import kotlin.math.sin

class AudioAnalyzerTest {
    @Test
    fun emptyNoiseHasExplicitNonStationaryResult() {
        val (cv, stationary) = AudioAnalyzer.noiseStationarity(emptyList())
        assertTrue(cv.isInfinite())
        assertFalse(stationary)
    }

    @Test
    fun analyzerProducesFiniteResultsForNormalAudio() {
        val sampleRate = 16_000
        val samples = FloatArray(sampleRate * 2) { i ->
            val gate = if (i in 3_200 until 24_000) 1.0 else 0.0
            (0.018 * sin(2 * Math.PI * 73 * i / sampleRate) +
                0.16 * sin(2 * Math.PI * 223 * i / sampleRate) * gate).toFloat()
        }
        val result = AudioAnalyzer.analyze(samples, sampleRate)
        assertTrue(abs(result.snrDb).isFinite())
        assertTrue(result.noiseStationarityCv.isFinite())
        assertTrue(result.speechActivityRatio in 0.0..1.0)
    }

    @Test
    fun deterministicSignalsMatchPythonAnalyzerGoldens() {
        val resource = javaClass.classLoader!!.getResourceAsStream("analyzer_parity.json")!!
            .bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        for (name in listOf("steady_tone_speech", "gated_broadband_speech", "quiet_tonal_noise")) {
            val expected = Regex("\\\"$name\\\"\\s*:\\s*\\{([^}]*)}")
                .find(resource)!!.groupValues[1]
            fun number(field: String) = Regex("\\\"$field\\\"\\s*:\\s*([-+0-9.eE]+)")
                .find(expected)!!.groupValues[1].toDouble()
            val stationary = Regex("\\\"stationary_noise\\\"\\s*:\\s*(true|false)")
                .find(expected)!!.groupValues[1].toBoolean()
            val actual = AudioAnalyzer.analyze(syntheticSignal(name), 16_000)
            assertEquals("$name SNR", number("snr_db"), actual.snrDb, 0.02)
            assertEquals("$name CV", number("noise_stationarity_cv"), actual.noiseStationarityCv, 0.0002)
            assertEquals("$name activity", number("speech_activity_ratio"), actual.speechActivityRatio, 0.0001)
            assertEquals("$name stationary", stationary, actual.stationaryNoise)
        }
    }

    private fun syntheticSignal(name: String): FloatArray {
        val sampleRate = 16_000
        return FloatArray(sampleRate * 2) { i ->
            val gate = if (i in 3_200 until 24_000) 1.0 else 0.0
            val speech = 0.16 * sin(2 * Math.PI * 223 * i / sampleRate) * gate
            val steady = 0.018 * sin(2 * Math.PI * 73 * i / sampleRate)
            val variable = 0.025 * sin(2 * Math.PI * 997 * i / sampleRate) * ((i / 800) % 2)
            val broadband = 0.035 * (sin(2 * Math.PI * 2_113 * i / sampleRate) +
                sin(2 * Math.PI * 3_719 * i / sampleRate))
            when (name) {
                "steady_tone_speech" -> (steady + speech).toFloat()
                "gated_broadband_speech" -> (broadband + variable + speech).toFloat()
                "quiet_tonal_noise" -> (0.01 * sin(2 * Math.PI * 611 * i / sampleRate)).toFloat()
                else -> error("Unknown signal: $name")
            }
        }
    }
}

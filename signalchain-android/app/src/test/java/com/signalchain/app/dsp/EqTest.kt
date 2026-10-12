package com.signalchain.app.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.*

class EqTest {

    @Test
    fun testGoldenParity16k() {
        val stream = javaClass.classLoader?.getResourceAsStream("eq_golden.json")
            ?: error("eq_golden.json not found in test resources")
        val text = stream.bufferedReader().use { it.readText() }

        fun extractArray(key: String): FloatArray {
            val start = text.indexOf("\"$key\":")
            require(start != -1) { "Key $key not found" }
            val openBracket = text.indexOf('[', start)
            val closeBracket = text.indexOf(']', openBracket)
            val items = text.substring(openBracket + 1, closeBracket).split(',')
            return FloatArray(items.size) { items[it].trim().toFloat() }
        }

        val input = extractArray("input")
        val goldenOutput = extractArray("output")
        assertEquals(input.size, goldenOutput.size)

        val actualOutput = Eq.apply(input, 16000, 1.0)
        assertEquals(input.size, actualOutput.size)

        var maxAbsError = 0f
        for (i in input.indices) {
            val err = abs(actualOutput[i] - goldenOutput[i])
            if (err > maxAbsError) maxAbsError = err
        }
        assertTrue("Max abs error vs golden must be < 1e-3, was $maxAbsError", maxAbsError < 1e-3f)
    }

    private fun measureGainDb(fs: Int, freq: Double): Double {
        val n = fs * 2 // 2 seconds
        val input = FloatArray(n) { i ->
            sin(2.0 * Math.PI * freq * i / fs).toFloat()
        }
        val output = Eq.apply(input, fs, 1.0)
        val margin = (fs * 0.3).toInt() // skip filter transients
        var maxIn = 0f
        var maxOut = 0f
        for (i in margin until (n - margin)) {
            val aIn = abs(input[i])
            val aOut = abs(output[i])
            if (aIn > maxIn) maxIn = aIn
            if (aOut > maxOut) maxOut = aOut
        }
        return 20.0 * log10(maxOut.toDouble() / maxIn.toDouble())
    }

    @Test
    fun testSineSweepGains16k() {
        val fs = 16000
        val expectedGains = mapOf(
            80.0 to -4.69,
            400.0 to 2.40,
            1500.0 to 5.59,
            3500.0 to 2.94,
            6000.0 to 0.01
        )

        for ((freq, expectedDb) in expectedGains) {
            val actualDb = measureGainDb(fs, freq)
            assertEquals(
                "Gain at ${freq}Hz (${actualDb}dB) should be within 0.5dB of expected (${expectedDb}dB)",
                expectedDb,
                actualDb,
                0.5
            )
        }
    }

    @Test
    fun test48kGainParityWith16k() {
        val gain16k_1500 = measureGainDb(16000, 1500.0)
        val gain48k_1500 = measureGainDb(48000, 1500.0)
        assertEquals(
            "1.5 kHz gain at 48k (${gain48k_1500}dB) vs 16k (${gain16k_1500}dB) should be within 1.0 dB",
            gain16k_1500,
            gain48k_1500,
            1.0
        )

        val gain16k_3500 = measureGainDb(16000, 3500.0)
        val gain48k_3500 = measureGainDb(48000, 3500.0)
        assertEquals(
            "3.5 kHz gain at 48k (${gain48k_3500}dB) vs 16k (${gain16k_3500}dB) should be within 1.0 dB",
            gain16k_3500,
            gain48k_3500,
            1.0
        )
    }
}

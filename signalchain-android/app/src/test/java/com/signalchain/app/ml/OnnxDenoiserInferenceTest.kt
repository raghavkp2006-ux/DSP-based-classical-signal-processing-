package com.signalchain.app.ml

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class OnnxDenoiserInferenceTest {
    @Test
    fun modelByteConstructorMatchesPythonGoldenAndReturnsFiniteMask() {
        val bytes = javaClass.classLoader!!.getResourceAsStream("models/spectral_mask_denoiser.onnx")!!
            .use { it.readBytes() }
        val json = javaClass.classLoader!!.getResourceAsStream("onnx_mask_golden.json")!!
            .bufferedReader().use { it.readText() }
        val input = jsonArray(json, "input")
        val expected = jsonArray(json, "output")
        OnnxDenoiser(bytes).use { denoiser ->
            val actual = denoiser.predictMask(Array(257) { i -> floatArrayOf(input[i]) }).map { it[0] }.toFloatArray()
            assertEquals(input.size, actual.size)
            assertTrue(actual.all { it.isFinite() })
            assertArrayEquals(expected, actual, 1e-4f)

            for ((sampleRate, length) in listOf(16_000 to 1_600, 12_000 to 1_200)) {
                val audio = FloatArray(length) { i -> (0.1 * sin(2 * Math.PI * 440 * i / sampleRate)).toFloat() }
                val processed = denoiser.apply(audio, sampleRate)
                assertEquals("sample rate $sampleRate", audio.size, processed.size)
                assertTrue(processed.all { it.isFinite() })
            }
        }
    }

    @Test
    fun failingModelLoadUsesDspOnlyAudioAndShowsWarning() {
        val dspOutput = floatArrayOf(0.1f, -0.2f, 0.05f)
        val result = MlPostFilter.applyOrFallback(dspOutput) {
            OnnxDenoiser(byteArrayOf(1, 2, 3, 4)).use { it.apply(dspOutput, 16_000) }
        }
        assertArrayEquals(dspOutput, result.value, 0f)
        assertTrue(result.warning != null)
    }

    private fun jsonArray(json: String, name: String): FloatArray {
        val match = Regex("\\\"$name\\\"\\s*:\\s*\\[([^]]*)]").find(json)!!
        return match.groupValues[1].split(',').map { it.trim().toFloat() }.toFloatArray()
    }
}

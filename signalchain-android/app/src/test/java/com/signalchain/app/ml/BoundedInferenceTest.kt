package com.signalchain.app.ml

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class BoundedInferenceTest {
    @Test fun matchesWholeClipIncludingChunkSeamsAndTail() {
        val bytes = javaClass.classLoader!!.getResourceAsStream("models/spectral_mask_denoiser.onnx")!!.use { it.readBytes() }
        OnnxDenoiser(bytes).use { model ->
            for (rate in listOf(16000, 44100, 48000)) {
                val rng = java.util.Random(72)
                val x = FloatArray(rate * 2 + rate / 4) { i ->
                    (.2*sin(2*PI*440*i/rate) + .02*rng.nextGaussian()).toFloat()
                }
                val expected = model.apply(x, rate)
                val actual = model.applyBounded(x, rate)
                assertEquals(x.size, actual.size)
                assertArrayEquals("Whole-clip parity at $rate, including seams", expected, actual, 1e-5f)
                println("Bounded parity rate=$rate maxError=${actual.indices.maxOf { abs(actual[it]-expected[it]) }}")
            }
        }
    }
}

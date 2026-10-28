package com.signalchain.app.dsp

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*
import java.io.File

class EqAlignmentTest {
    @Test fun speechBandHasNoCombNotches() {
        for (sr in listOf(16000, 44100, 48000)) {
            for (hz in listOf(1250.0, 1750.0, 2250.0)) {
                val x = FloatArray(sr / 2) { sin(2 * PI * hz * it / sr).toFloat() }
                val old = Eq.apply(x, sr)
                val y = Eq.applyAligned(x, sr)
                val range = (sr / 10) until (x.size - sr / 10)
                fun gain(a: FloatArray) = 10 * log10(range.sumOf { a[it].toDouble().pow(2) } / range.sumOf { x[it].toDouble().pow(2) })
                println("EQ sr=$sr hz=$hz oldDb=${gain(old)} alignedDb=${gain(y)}")
                assertTrue("Voice band cancelled: $sr / $hz", gain(y) > 3.0)
            }
        }
    }

    @Test fun independentConvolutionIncludingCenterAndTruncatedEdge() {
        val lines = javaClass.classLoader!!.getResourceAsStream("eq_aligned_reference.csv")!!
            .bufferedReader().use { it.readLines() }
        var worst = 0f
        for (line in lines.drop(1)) {
            val cells = line.split(',')
            val sr = cells[0].toInt()
            val gain = cells[1].toDouble()
            val kind = cells[2]
            val input = cells[3].split(';').map { it.toFloat() }.toFloatArray()
            val expected = cells[4].split(';').map { it.toFloat() }.toFloatArray()
            val actual = Eq.applyAligned(input, sr, gain)
            assertEquals(input.size, actual.size)
            assertTrue(actual.all { it.isFinite() })
            assertArrayEquals("$sr/$gain/$kind independent convolution", expected, actual, 1e-5f)
            worst = max(worst, actual.indices.maxOf { abs(actual[it] - expected[it]) })
            if (kind == "middle") {
                val center = input.size / 2
                assertEquals(center, actual.indices.maxByOrNull { abs(actual[it]) }!!.toInt())
                assertEquals("Composite kernel center tap", cells[5].toFloat(), actual[center], 1e-5f)
                for (d in 1..500) assertEquals(actual[center-d], actual[center+d], 1e-6f)
            }
            assertArrayEquals(input, Eq.applyAligned(input, sr, 0.0), 0f)
        }
        println("Independent EQ reference: cases=${lines.size-1}, maxAbsError=$worst, tolerance=1e-5")
    }

    @Test fun fullBandResponseAndLoudInputHeadroom() {
        val out = File("build/eq_validation").also { it.mkdirs() }
        for (sr in listOf(16000, 44100, 48000)) {
            for (gain in listOf(0.6, 1.0)) {
                val impulse = FloatArray(8192); impulse[4096] = 1f
                val old = Eq.apply(impulse, sr, gain)
                val aligned = Eq.applyAligned(impulse, sr, gain)
                val oldSpec = Fft.rfft(DoubleArray(impulse.size) { old[it].toDouble() })
                val newSpec = Fft.rfft(DoubleArray(impulse.size) { aligned[it].toDouble() })
                var minimum = Double.POSITIVE_INFINITY
                File(out, "response_${sr}_${gain}.csv").printWriter().use { w ->
                    w.println("hz,legacy_db,aligned_db,difference_db")
                    for (i in oldSpec.indices) {
                        fun db(c: Complex) = 10 * log10(max(1e-24, c.re*c.re+c.im*c.im))
                        val hz = i * sr.toDouble() / impulse.size
                        val a = db(oldSpec[i]); val b = db(newSpec[i])
                        w.println("$hz,$a,$b,${b-a}")
                        // Intentional HP/LP roll-offs excluded; crossover regions included.
                        if (hz in 300.0..7000.0) minimum = min(minimum, b)
                    }
                }
                assertTrue("Unexpected in-band notch $sr/$gain: $minimum dB", minimum > -0.5)
                // Near-full-scale sine, multitone, and alternating full-scale input.
                val signals = listOf(
                    FloatArray(sr / 2) { (.99*sin(2*PI*1750*it/sr)).toFloat() },
                    FloatArray(sr / 2) { (.33*(sin(2*PI*400*it/sr)+sin(2*PI*1750*it/sr)+sin(2*PI*3500*it/sr))).toFloat() },
                    FloatArray(sr / 2) { if (it % 2 == 0) .99f else -.99f }
                )
                for ((id,x) in signals.withIndex()) {
                    val eq = Eq.applyAligned(x, sr, gain)
                    val quiet = Eq.applyAligned(FloatArray(x.size) { x[it]*.1f }, sr, gain)
                    // A clipped intermediate would violate linearity even though it is finite.
                    assertArrayEquals("Unclipped float EQ", eq, FloatArray(x.size) { quiet[it]*10f }, 1e-5f)
                    val comp = Compressor.compress(eq, sr, if (gain == 1.0) 1.0 else 1.8, if (gain == 1.0) 0.0 else 6.0)
                    val final = Compressor.normalizeAndLimit(comp, x).audio
                    assertTrue(eq.all { it.isFinite() } && comp.all { it.isFinite() })
                    assertTrue(final.all { it.isFinite() && abs(it) <= .990001f })
                    assertEquals(0, final.count { abs(it) >= 1f })
                    println("HEADROOM sr=$sr gain=$gain signal=$id eqPeak=${eq.maxOf { abs(it) }} compressorPeak=${comp.maxOf { abs(it) }} finalPeak=${final.maxOf { abs(it) }} fullScaleClips=0 inBandMinDb=$minimum")
                }
            }
        }
    }
}

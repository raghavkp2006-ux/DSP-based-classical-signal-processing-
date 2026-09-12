package com.signalchain.app

import android.content.Context
import android.content.ContextWrapper
import com.signalchain.app.audio.WavLoader
import com.signalchain.app.audio.WavWriter
import com.signalchain.app.pipeline.PipelineResult
import com.signalchain.app.pipeline.RunPipeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

class PipelineProgressAndWaveformTest {

    @Test
    fun testPipelineResultDefaultArgument() {
        val res = PipelineResult(
            mode = "MILD",
            rationale = "Stationary low noise",
            snrBeforeDb = 10.5,
            snrAfterDb = 18.2,
            outputFilePath = "/cache/enhanced.wav",
            clipsPct = 0.01
        )
        assertEquals("MILD", res.mode)
        assertEquals(0, res.enhancedSamples.size)

        val samples = floatArrayOf(0.1f, -0.2f, 0.5f)
        val resWithSamples = PipelineResult(
            mode = "AGGRESSIVE",
            rationale = "High noise",
            snrBeforeDb = 2.0,
            snrAfterDb = 14.0,
            outputFilePath = "/cache/enhanced2.wav",
            clipsPct = 0.02,
            enhancedSamples = samples
        )
        assertEquals("AGGRESSIVE", resWithSamples.mode)
        assertEquals(3, resWithSamples.enhancedSamples.size)
        assertEquals(0.5f, resWithSamples.enhancedSamples[2], 1e-6f)
    }

    @Test
    fun testWaveformPeakDownsampling() {
        val barCount = 120
        val sampleCount = 48000
        val samples = FloatArray(sampleCount) { i ->
            sin(i * 0.05).toFloat() * 0.8f
        }

        val chunk = max(1, samples.size / barCount)
        val peaks = FloatArray(barCount) { i ->
            val start = i * chunk
            val end = minOf(start + chunk, samples.size)
            if (start >= end) 0f else {
                var m = 0f
                for (j in start until end) m = max(m, abs(samples[j]))
                m
            }
        }

        assertEquals(barCount, peaks.size)
        for (p in peaks) {
            assertTrue("Peak must be in range [0, 1], got $p", p in 0f..1f)
            assertTrue("Sine wave peak must be greater than zero", p > 0.5f)
        }
    }

    @Test
    fun testWaveformEmptyAndShortSamples() {
        val barCount = 120
        val emptySamples = FloatArray(0)
        val chunk = max(1, emptySamples.size / barCount)
        val peaks = FloatArray(barCount) { i ->
            val start = i * chunk
            val end = minOf(start + chunk, emptySamples.size)
            if (start >= end) 0f else {
                var m = 0f
                for (j in start until end) m = max(m, abs(emptySamples[j]))
                m
            }
        }
        assertEquals(barCount, peaks.size)
        for (p in peaks) {
            assertEquals(0f, p, 0f)
        }
    }

    @Test
    fun testRunPipelineCallbackAndNumericalRegression() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "signalchain-test-${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            // Generate 1 second of synthetic speech + noise WAV
            val sampleRate = 16000
            val numSamples = sampleRate * 1 // 1 second
            val testSamples = FloatArray(numSamples) { i ->
                val speech = if (i % 3200 < 1600) (sin(2.0 * Math.PI * 300.0 * i / sampleRate) * 0.6).toFloat() else 0f
                val noise = ((i % 17) / 17.0f - 0.5f) * 0.1f
                speech + noise
            }

            val inputWav = File(tempDir, "test_input.wav")
            val d = java.io.DataOutputStream(java.io.BufferedOutputStream(java.io.FileOutputStream(inputWav)))
            fun i(v: Int) { d.writeInt(Integer.reverseBytes(v)) }
            d.writeBytes("RIFF")
            i(36 + testSamples.size * 2)
            d.writeBytes("WAVEfmt ")
            i(16)
            d.writeShort(1) // fmt = 1
            d.writeShort(1) // ch = 1
            i(sampleRate)
            i(sampleRate * 2)
            d.writeShort(2)
            d.writeShort(16) // bits = 16
            d.writeBytes("data")
            i(testSamples.size * 2)
            val bb = java.nio.ByteBuffer.allocate(testSamples.size * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            for (s in testSamples) {
                bb.putShort((s.coerceIn(-1f, 1f) * 32767).toInt().toShort())
            }
            d.write(bb.array())
            d.close()
            assertTrue("Input WAV must exist", inputWav.exists())

            // Create test Context wrapper that returns tempDir as cacheDir
            val mockContext = object : ContextWrapper(null) {
                override fun getCacheDir(): File = tempDir
                override fun getApplicationContext(): Context = this
            }

            val stages = mutableListOf<String>()
            val pipeline = RunPipeline(mockContext)

            // 1. Run with progress callback
            val resultWithCallback = pipeline.run(inputWav.absolutePath) { stage ->
                stages.add(stage)
            }

            // Verify the 6 stages in exact sequence
            assertEquals(
                listOf(
                    "Loading audio",
                    "Analyzing signal",
                    "Reducing noise",
                    "Applying EQ and compression",
                    "Running ML post-filter",
                    "Finalizing"
                ),
                stages
            )

            // 2. Run without callback (overload)
            val resultWithoutCallback = pipeline.run(inputWav.absolutePath)

            // Regression check: verify numerical and structural parity
            assertEquals(resultWithCallback.mode, resultWithoutCallback.mode)
            assertEquals(resultWithCallback.rationale, resultWithoutCallback.rationale)
            assertEquals(resultWithCallback.snrBeforeDb, resultWithoutCallback.snrBeforeDb, 1e-4)
            assertEquals(resultWithCallback.snrAfterDb, resultWithoutCallback.snrAfterDb, 1e-4)
            assertEquals(resultWithCallback.clipsPct, resultWithoutCallback.clipsPct, 1e-4)

            // Verify enhanced audio outputs
            assertTrue(File(resultWithCallback.outputFilePath).exists())
            assertTrue(File(resultWithoutCallback.outputFilePath).exists())
            assertTrue("Enhanced samples must not be empty", resultWithCallback.enhancedSamples.isNotEmpty())
            assertTrue("Enhanced samples must match length", resultWithCallback.enhancedSamples.size >= numSamples)

            // Verify output WAV file is written
            val outputFile = File(resultWithCallback.outputFilePath)
            assertTrue("Output WAV must be larger than RIFF header", outputFile.length() > 44)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}

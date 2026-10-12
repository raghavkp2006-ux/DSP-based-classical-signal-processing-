package com.signalchain.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class WavLoaderTest {

    private fun createWavFile(
        file: File,
        format: Int, // 1 for PCM, 3 for IEEE float
        bitsPerSample: Int,
        numChannels: Int,
        sampleRate: Int,
        samples: FloatArray
    ) {
        val bytesPerSample = bitsPerSample / 8
        val dataSize = samples.size * numChannels * bytesPerSample
        val fileSize = 36 + dataSize

        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        // RIFF header
        buffer.put("RIFF".toByteArray())
        buffer.putInt(fileSize)
        buffer.put("WAVE".toByteArray())

        // fmt chunk
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16) // subchunk1 size
        buffer.putShort(format.toShort())
        buffer.putShort(numChannels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * numChannels * bytesPerSample) // byte rate
        buffer.putShort((numChannels * bytesPerSample).toShort()) // block align
        buffer.putShort(bitsPerSample.toShort())

        // data chunk
        buffer.put("data".toByteArray())
        buffer.putInt(dataSize)

        for (sample in samples) {
            for (c in 0 until numChannels) {
                when {
                    format == 1 && bitsPerSample == 16 -> {
                        val v = (sample.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                        buffer.putShort(v)
                    }
                    format == 1 && bitsPerSample == 32 -> {
                        val v = (sample.coerceIn(-1f, 1f) * 2147483647.0).toLong().toInt()
                        buffer.putInt(v)
                    }
                    format == 3 && bitsPerSample == 32 -> {
                        buffer.putFloat(sample)
                    }
                    else -> error("Unsupported test format ($format, $bitsPerSample)")
                }
            }
        }

        FileOutputStream(file).use { it.write(buffer.array()) }
    }

    @Test
    fun testPcm16WavRoundTrip() {
        val temp = File.createTempFile("test_pcm16", ".wav")
        try {
            val rate = 16000
            val samples = FloatArray(1600) { i -> (i / 1600.0 * 0.8).toFloat() }
            createWavFile(temp, format = 1, bitsPerSample = 16, numChannels = 1, sampleRate = rate, samples = samples)

            val loaded = WavLoader.load(temp.absolutePath)
            assertEquals(rate, loaded.sampleRate)
            assertEquals(samples.size, loaded.samples.size)

            // Normalized to peak = 1.0
            val maxSample = loaded.samples.maxOf { abs(it) }
            assertEquals(1.0f, maxSample, 1e-4f)
        } finally {
            temp.delete()
        }
    }

    @Test
    fun testPcm32IntWavRoundTrip() {
        val temp = File.createTempFile("test_pcm32_int", ".wav")
        try {
            val rate = 16000
            // Non-zero values that would be IEEE float NaN/denorm if misinterpreted
            val samples = FloatArray(1600) { i -> (kotlin.math.sin(2.0 * Math.PI * 440.0 * i / rate) * 0.75).toFloat() }
            createWavFile(temp, format = 1, bitsPerSample = 32, numChannels = 1, sampleRate = rate, samples = samples)

            val loaded = WavLoader.load(temp.absolutePath)
            assertEquals(rate, loaded.sampleRate)
            assertEquals(samples.size, loaded.samples.size)

            val maxSample = loaded.samples.maxOf { abs(it) }
            assertEquals(1.0f, maxSample, 1e-4f)

            // Verify waveform shape matches input (correlation / error check)
            val peakOrig = samples.maxOf { abs(it) }
            for (i in samples.indices) {
                val expectedNorm = samples[i] / peakOrig
                assertEquals("Sample $i mismatch in 32-bit int PCM", expectedNorm, loaded.samples[i], 1e-4f)
            }
        } finally {
            temp.delete()
        }
    }

    @Test
    fun testFloat32WavRoundTrip() {
        val temp = File.createTempFile("test_float32", ".wav")
        try {
            val rate = 16000
            val samples = FloatArray(1600) { i -> (kotlin.math.sin(2.0 * Math.PI * 440.0 * i / rate) * 0.5).toFloat() }
            createWavFile(temp, format = 3, bitsPerSample = 32, numChannels = 1, sampleRate = rate, samples = samples)

            val loaded = WavLoader.load(temp.absolutePath)
            assertEquals(rate, loaded.sampleRate)
            assertEquals(samples.size, loaded.samples.size)

            val peakOrig = samples.maxOf { abs(it) }
            for (i in samples.indices) {
                val expectedNorm = samples[i] / peakOrig
                assertEquals("Sample $i mismatch in 32-bit float", expectedNorm, loaded.samples[i], 1e-4f)
            }
        } finally {
            temp.delete()
        }
    }
}

package com.signalchain.app.audio

import java.io.*

object WavWriter {
    fun write(path: String, x: FloatArray, rate: Int) {
        DataOutputStream(BufferedOutputStream(FileOutputStream(path))).use { d ->
            // Write a 32-bit int in little-endian
            fun writeLE32(v: Int) {
                d.write(v and 0xFF)
                d.write((v shr 8) and 0xFF)
                d.write((v shr 16) and 0xFF)
                d.write((v shr 24) and 0xFF)
            }
            // Write a 16-bit short in little-endian
            fun writeLE16(v: Int) {
                d.write(v and 0xFF)
                d.write((v shr 8) and 0xFF)
            }

            val dataSize = x.size * 2  // 16-bit = 2 bytes per sample
            val fileSize = 36 + dataSize

            // RIFF header
            d.writeBytes("RIFF")
            writeLE32(fileSize)
            d.writeBytes("WAVE")

            // fmt sub-chunk
            d.writeBytes("fmt ")
            writeLE32(16)         // sub-chunk size
            writeLE16(1)          // audio format: 1 = PCM
            writeLE16(1)          // num channels: 1 = mono
            writeLE32(rate)       // sample rate
            writeLE32(rate * 2)   // byte rate = sampleRate * numChannels * bitsPerSample/8
            writeLE16(2)          // block align = numChannels * bitsPerSample/8
            writeLE16(16)         // bits per sample

            // data sub-chunk
            d.writeBytes("data")
            writeLE32(dataSize)

            // Audio samples
            for (sample in x) {
                val v = (sample.coerceIn(-1f, 1f) * 32767f).toInt()
                writeLE16(v)
            }
        }
    }
}

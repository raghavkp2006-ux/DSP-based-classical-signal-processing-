package com.signalchain.app.audio

import java.io.*
import java.nio.*
import kotlin.math.*

object WavLoader {
    data class LoadedAudio(val samples: FloatArray, val sampleRate: Int)

    fun load(path: String, targetSampleRate: Int = 0): LoadedAudio {
        val f = RandomAccessFile(path, "r")
        try {
            // Read and validate RIFF header
            val riffTag = readTag(f)
            require(riffTag == "RIFF") { "Only WAV files are supported." }
            f.skipBytes(4) // file size
            val waveTag = readTag(f)
            require(waveTag == "WAVE") { "Invalid WAV file." }

            var fmt = 0
            var ch = 0
            var rate = 0
            var bits = 0
            var data = ByteArray(0)

            while (f.filePointer < f.length()) {
                val id = readTag(f)
                val size = readLE32(f)
                when (id) {
                    "fmt " -> {
                        fmt = readLE16(f)       // audio format (1=PCM, 3=IEEE float)
                        ch = readLE16(f)        // number of channels
                        rate = readLE32(f)      // sample rate
                        f.skipBytes(4)          // byte rate (skip)
                        f.skipBytes(2)          // block align (skip)
                        bits = readLE16(f)      // bits per sample
                        if (size > 16) f.skipBytes(size - 16)
                    }
                    "data" -> {
                        data = ByteArray(size)
                        f.readFully(data)
                    }
                    else -> f.skipBytes(size)
                }
            }

            require(fmt == 1 || fmt == 3) { "Only PCM WAV files are supported." }
            require(ch > 0 && (bits == 16 || bits == 32)) { "Only PCM16/PCM32 WAV files are supported." }

            val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            val bytesPerFrame = ch * (bits / 8)
            val frames = data.size / bytesPerFrame
            val x = FloatArray(frames)
            for (i in 0 until frames) {
                var sum = 0.0
                for (c in 0 until ch) {
                    sum += if (bits == 16) bb.short / 32768.0 else bb.float.toDouble()
                }
                x[i] = (sum / ch).toFloat()
            }

            val peak = x.maxOfOrNull { abs(it) } ?: 0f
            if (peak > 1e-10f) {
                for (i in x.indices) x[i] = (x[i] / peak).toFloat()
            }

            require(x.size >= 0.03 * rate) { "Audio is too short; upload at least 30 ms of audio." }
            return LoadedAudio(x, rate)
        } finally {
            f.close()
        }
    }

    private fun readTag(f: RandomAccessFile): String {
        val bytes = ByteArray(4)
        f.readFully(bytes)
        return String(bytes)
    }

    /** Read a 32-bit little-endian integer */
    private fun readLE32(f: RandomAccessFile): Int {
        val b0 = f.read()
        val b1 = f.read()
        val b2 = f.read()
        val b3 = f.read()
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    /** Read a 16-bit little-endian unsigned short */
    private fun readLE16(f: RandomAccessFile): Int {
        val b0 = f.read()
        val b1 = f.read()
        return b0 or (b1 shl 8)
    }
}


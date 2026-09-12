package com.signalchain.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import kotlinx.coroutines.*
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioPlayer {
    private var player: MediaPlayer? = null
    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var activeFis: FileInputStream? = null

    fun play(file: File, onError: (String) -> Unit = {}, onCompletion: () -> Unit = {}) {
        stop()
        if (!file.exists() || file.length() == 0L) {
            onError("Audio file does not exist or is empty")
            return
        }

        // For WAV files generated or handled by SignalChain, direct AudioTrack playback is instantaneous,
        // has zero latency, and never suffers from stagefright/MediaPlayer status=0x1 native errors.
        if (file.name.endsWith(".wav", ignoreCase = true) || isRiffWav(file)) {
            playViaAudioTrack(file, onError, onCompletion)
            return
        }

        // For other formats (e.g. mp3/aac if played directly), use MediaPlayer with open FileInputStream
        try {
            val fis = FileInputStream(file)
            activeFis = fis
            val mp = MediaPlayer()
            player = mp
            mp.setDataSource(fis.fd)
            mp.setOnCompletionListener {
                stop()
                onCompletion()
            }
            mp.setOnErrorListener { _, what, extra ->
                android.util.Log.e("SignalChainAudioPlayer", "MediaPlayer error: what=$what extra=$extra, falling back to AudioTrack")
                stop()
                playViaAudioTrack(file, onError, onCompletion)
                true
            }
            mp.prepare()
            mp.start()
        } catch (e: Exception) {
            android.util.Log.w("SignalChainAudioPlayer", "MediaPlayer failed (${e.message}), trying AudioTrack fallback", e)
            stop()
            playViaAudioTrack(file, onError, onCompletion)
        }
    }

    private fun isRiffWav(file: File): Boolean {
        return try {
            if (file.length() < 12) false
            else {
                RandomAccessFile(file, "r").use { raf ->
                    val magic = ByteArray(4)
                    raf.readFully(magic)
                    String(magic) == "RIFF"
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun playViaAudioTrack(file: File, onError: (String) -> Unit, onCompletion: () -> Unit) {
        playbackJob = scope.launch {
            var track: AudioTrack? = null
            try {
                val raf = RandomAccessFile(file, "r")
                val riff = ByteArray(4)
                raf.readFully(riff)
                if (String(riff) != "RIFF") {
                    withContext(Dispatchers.Main) { onError("Unsupported audio format for playback") }
                    raf.close()
                    return@launch
                }
                raf.skipBytes(4) // file size
                val wave = ByteArray(4)
                raf.readFully(wave)
                if (String(wave) != "WAVE") {
                    withContext(Dispatchers.Main) { onError("Invalid audio format") }
                    raf.close()
                    return@launch
                }

                var sampleRate = 44100
                var channels = 1
                var bitsPerSample = 16
                var dataOffset = 0L
                var dataLength = 0L

                while (raf.filePointer < raf.length()) {
                    val chunkId = ByteArray(4)
                    raf.readFully(chunkId)
                    val chunkSize = readLE32(raf)
                    val idStr = String(chunkId)
                    when (idStr) {
                        "fmt " -> {
                            readLE16(raf)  // audio format (skip)
                            channels = readLE16(raf)
                            sampleRate = readLE32(raf)
                            raf.skipBytes(4) // byte rate
                            raf.skipBytes(2) // block align
                            bitsPerSample = readLE16(raf)
                            if (chunkSize > 16) raf.skipBytes(chunkSize - 16)
                        }
                        "data" -> {
                            dataOffset = raf.filePointer
                            dataLength = chunkSize.toLong()
                            break
                        }
                        else -> {
                            raf.skipBytes(chunkSize)
                        }
                    }
                }

                if (dataOffset == 0L || dataLength <= 0L) {
                    withContext(Dispatchers.Main) { onError("Audio data not found in file") }
                    raf.close()
                    return@launch
                }

                // Ensure valid values for AudioTrack
                if (channels <= 0) channels = 1
                if (sampleRate <= 0) sampleRate = 44100
                if (bitsPerSample != 16 && bitsPerSample != 32) bitsPerSample = 16

                val channelConfig = if (channels == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
                val encoding = if (bitsPerSample == 16) AudioFormat.ENCODING_PCM_16BIT else AudioFormat.ENCODING_PCM_FLOAT
                val minBufSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, encoding)
                val bufferSize = maxOf(minBufSize * 2, 8192)

                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(encoding)
                            .setSampleRate(sampleRate)
                            .setChannelMask(channelConfig)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack = track
                track.play()

                raf.seek(dataOffset)
                val buffer = ByteArray(bufferSize)
                var remaining = dataLength
                while (isActive && remaining > 0) {
                    val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                    val read = raf.read(buffer, 0, toRead)
                    if (read <= 0) break
                    track.write(buffer, 0, read)
                    remaining -= read
                }
                raf.close()

                if (isActive) {
                    withContext(Dispatchers.Main) { onCompletion() }
                }
            } catch (e: Exception) {
                if (isActive) {
                    android.util.Log.e("SignalChainAudioPlayer", "AudioTrack playback error", e)
                    withContext(Dispatchers.Main) { onError(e.message ?: "Audio playback error") }
                }
            } finally {
                try {
                    track?.stop()
                    track?.release()
                } catch (_: Exception) {}
                audioTrack = null
            }
        }
    }

    /** Read a 32-bit little-endian integer from RandomAccessFile */
    private fun readLE32(raf: RandomAccessFile): Int {
        val b0 = raf.read()
        val b1 = raf.read()
        val b2 = raf.read()
        val b3 = raf.read()
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    /** Read a 16-bit little-endian unsigned short from RandomAccessFile */
    private fun readLE16(raf: RandomAccessFile): Int {
        val b0 = raf.read()
        val b1 = raf.read()
        return b0 or (b1 shl 8)
    }

    fun stop() {
        try {
            playbackJob?.cancel()
            playbackJob = null
            audioTrack?.let { t ->
                try { t.stop() } catch (_: Exception) {}
                try { t.release() } catch (_: Exception) {}
            }
            audioTrack = null
            player?.let { p ->
                if (p.isPlaying) {
                    p.stop()
                }
                p.reset()
                p.release()
            }
        } catch (_: Exception) {
        } finally {
            try { activeFis?.close() } catch (_: Exception) {}
            activeFis = null
            player = null
        }
    }

    fun isPlaying(): Boolean {
        return try {
            (player?.isPlaying == true) || (audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING)
        } catch (_: Exception) {
            false
        }
    }

    fun release() {
        stop()
        scope.cancel()
    }
}

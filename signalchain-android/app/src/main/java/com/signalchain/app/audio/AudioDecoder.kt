package com.signalchain.app.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder

object AudioDecoder {
    private const val TIMEOUT_US = 10_000L

    /** Decodes any audio file/URI the device's codecs support into mono float PCM. */
    fun decodeToMonoPcm(context: Context, uri: Uri): WavLoader.LoadedAudio {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)

        var trackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val candidate = extractor.getTrackFormat(i)
            val mime = candidate.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                trackIndex = i
                format = candidate
                break
            }
        }
        require(trackIndex >= 0 && format != null) {
            "This file doesn't contain a recognizable audio track."
        }
        extractor.selectTrack(trackIndex)

        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        var channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val pcmChunks = ArrayList<ShortArray>()
        val bufferInfo = MediaCodec.BufferInfo()
        var sawInputEos = false
        var sawOutputEos = false

        try {
            while (!sawOutputEos) {
                if (!sawInputEos) {
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(
                                inputIndex, 0, sampleSize, extractor.sampleTime, 0
                            )
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                when {
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = codec.outputFormat
                        channelCount = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    outputIndex >= 0 -> {
                        val outputBuffer = codec.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            outputBuffer.order(ByteOrder.LITTLE_ENDIAN)
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            val shorts = outputBuffer.asShortBuffer()
                            val chunk = ShortArray(shorts.remaining())
                            shorts.get(chunk)
                            pcmChunks.add(chunk)
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            sawOutputEos = true
                        }
                    }
                }
            }
        } finally {
            codec.stop()
            codec.release()
            extractor.release()
        }

        val totalShorts = pcmChunks.sumOf { it.size }
        require(totalShorts > 0) { "No decodable audio data found in this file." }
        val interleaved = ShortArray(totalShorts)
        var position = 0
        for (chunk in pcmChunks) {
            chunk.copyInto(interleaved, position)
            position += chunk.size
        }

        val channels = channelCount.coerceAtLeast(1)
        val frameCount = interleaved.size / channels
        val mono = FloatArray(frameCount)
        for (i in 0 until frameCount) {
            var sum = 0.0
            for (channel in 0 until channels) {
                sum += interleaved[i * channels + channel] / 32768.0
            }
            mono[i] = (sum / channels).toFloat()
        }

        require(mono.size >= 0.03 * sampleRate) {
            "Audio is too short; upload at least 30 ms of audio."
        }
        return WavLoader.LoadedAudio(mono, sampleRate)
    }
}

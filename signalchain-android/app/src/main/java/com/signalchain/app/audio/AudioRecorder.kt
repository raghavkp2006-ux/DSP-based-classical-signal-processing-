package com.signalchain.app.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Records mono 16-bit PCM audio from the device microphone and writes it
 * directly to a WAV file compatible with the SignalChain pipeline.
 *
 * Usage:
 *   val recorder = AudioRecorder()
 *   recorder.startRecording(outputFile, sampleRate)   // launches capture
 *   // … user presses stop …
 *   val result = recorder.stopRecording()              // returns AudioRecordResult
 */
class AudioRecorder {

    data class AudioRecordResult(
        val file: File,
        val sampleRate: Int,
        val samples: FloatArray,
        val durationSec: Float
    )

    companion object {
        const val DEFAULT_SAMPLE_RATE = 16000
    }

    private var recorder: AudioRecord? = null
    @Volatile private var isRecording = false
    private var capturedSamples: FloatArray = FloatArray(0)
    private var outputFile: File? = null
    private var activeSampleRate: Int = DEFAULT_SAMPLE_RATE

    fun isCurrentlyRecording(): Boolean = isRecording

    /**
     * Starts recording audio in a coroutine. Call this from a coroutine scope.
     * The function suspends until [stopRecording] is called from another coroutine/thread.
     */
    suspend fun startRecording(output: File, sampleRate: Int = DEFAULT_SAMPLE_RATE) {
        outputFile = output
        activeSampleRate = sampleRate

        withContext(Dispatchers.IO) {
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = maxOf(minBufSize, sampleRate * 2) // at least 1 second buffer

            recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (recorder?.state != AudioRecord.STATE_INITIALIZED) {
                recorder?.release()
                recorder = null
                throw IllegalStateException("AudioRecord failed to initialize. Check RECORD_AUDIO permission.")
            }

            val shortBuffer = ShortArray(1024)
            val allSamples = mutableListOf<Float>()
            isRecording = true

            try {
                recorder?.startRecording()
                while (isRecording && isActive) {
                    val read = recorder?.read(shortBuffer, 0, shortBuffer.size) ?: -1
                    if (read > 0) {
                        for (i in 0 until read) {
                            allSamples.add(shortBuffer[i] / 32768f)
                        }
                    }
                }
            } finally {
                try { recorder?.stop() } catch (_: Exception) {}
                recorder?.release()
                recorder = null
            }

            capturedSamples = allSamples.toFloatArray()

            // Write WAV file for pipeline consumption
            WavWriter.write(output.absolutePath, capturedSamples, sampleRate)
        }
    }

    /**
     * Stops the active recording. After this returns, the WAV file is ready
     * and [getResult] can be called.
     */
    fun stopRecording(): AudioRecordResult? {
        if (!isRecording) return null
        isRecording = false

        // Give the recording coroutine a moment to flush
        // The caller should wait for the coroutine to complete
        return null // actual result retrieved via getResult() after coroutine completes
    }

    /**
     * Returns the result after recording is complete (coroutine has finished).
     */
    fun getResult(): AudioRecordResult? {
        val file = outputFile ?: return null
        if (capturedSamples.isEmpty()) return null
        val duration = capturedSamples.size.toFloat() / activeSampleRate
        return AudioRecordResult(
            file = file,
            sampleRate = activeSampleRate,
            samples = capturedSamples,
            durationSec = duration
        )
    }

    fun release() {
        isRecording = false
        try { recorder?.stop() } catch (_: Exception) {}
        recorder?.release()
        recorder = null
    }
}

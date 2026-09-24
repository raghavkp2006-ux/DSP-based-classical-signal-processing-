package com.signalchain.app.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.signalchain.app.audio.AudioDecoder
import com.signalchain.app.audio.WavLoader
import com.signalchain.app.audio.WavWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

fun formatRecordingTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}

suspend fun loadAndPrepareAudio(context: Context, uri: Uri): SelectedAudioInfo = withContext(Dispatchers.IO) {
    val (name, size) = queryFileMetadata(context, uri)
    val temp = File.createTempFile("signalchain-input-", ".wav", context.cacheDir)
    context.contentResolver.openInputStream(uri)?.use { input ->
        temp.outputStream().use { out -> input.copyTo(out) }
    } ?: error("Failed to open audio stream from selected URI")

    val maxBytes = 50L * 1024 * 1024
    val actualSize = if (size > 0) size else temp.length()
    require(actualSize in 1..maxBytes) { "File is empty or larger than the 50 MB limit." }

    val isRiffWav = temp.length() >= 12 && temp.inputStream().use { s ->
        val magic = ByteArray(4)
        s.read(magic) == 4 && String(magic) == "RIFF"
    }

    var loadedAudio: WavLoader.LoadedAudio? = null
    var detectedFormat = "Audio File"

    // If it looks like a WAV file, try standard WavLoader first
    if (isRiffWav) {
        try {
            loadedAudio = WavLoader.load(temp.absolutePath)
            detectedFormat = "PCM WAV (Native)"
        } catch (e: Exception) {
            android.util.Log.w("SignalChain", "WavLoader could not parse RIFF as standard PCM WAV (${e.message}), falling back to MediaCodec decoder")
        }
    }

    // If not standard PCM WAV or if WavLoader failed (e.g. MP3, AAC, M4A, OGG, FLAC, floating-point/compressed WAV):
    if (loadedAudio == null) {
        try {
            loadedAudio = AudioDecoder.decodeToMonoPcm(context, uri)
            detectedFormat = "Decoded via MediaCodec"
        } catch (e: Exception) {
            // Also try decoding from the cached temp file directly if URI decoding had issues
            try {
                loadedAudio = AudioDecoder.decodeToMonoPcm(context, Uri.fromFile(temp))
                detectedFormat = "Decoded via MediaCodec"
            } catch (_: Exception) {
                // Throw original error with friendly message
                throw e
            }
        }
    }

    val validAudio = requireNotNull(loadedAudio) { "Unable to decode audio format. Please check if file is valid." }

    // Always normalize and save to a clean, canonical PCM 16-bit mono WAV file
    // This ensures that RunPipeline.run() and AudioPlayer.play() ALWAYS succeed without format errors!
    val canonicalWav = File.createTempFile("signalchain-canonical-", ".wav", context.cacheDir)
    WavWriter.write(canonicalWav.absolutePath, validAudio.samples, validAudio.sampleRate)
    val duration = validAudio.samples.size.toFloat() / validAudio.sampleRate

    SelectedAudioInfo(
        name = name,
        sizeBytes = actualSize,
        format = detectedFormat,
        sampleRate = validAudio.sampleRate,
        durationSec = duration,
        samples = validAudio.samples,
        filePath = canonicalWav.absolutePath
    )
}

fun queryFileMetadata(context: Context, uri: Uri): Pair<String, Long> {
    var name = "audio_file"
    var size = 0L
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                if (sizeIndex >= 0) size = cursor.getLong(sizeIndex)
            }
        }
    } catch (_: Exception) {
    }
    return Pair(name, size)
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) "%.1f MB".format(mb) else "%.1f KB".format(kb)
}

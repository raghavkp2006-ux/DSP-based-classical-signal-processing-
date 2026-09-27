package com.signalchain.app.util

enum class UserFacingError(val userMessage: String) {
    UNSUPPORTED_FORMAT("Unsupported audio format — try a standard WAV, MP3, or M4A file."),
    FILE_TOO_LARGE("The selected file is too large to process on-device. Please try a shorter clip."),
    DECODE_FAILED("Couldn't read that audio file — the file might be corrupted or in an unsupported format."),
    PROCESSING_TIMED_OUT("Processing took too long and timed out. Please try a shorter audio clip."),
    ML_FALLBACK("Enhanced with classical processing only (ML step unavailable)."),
    RECORDING_FAILED("Microphone recording failed. Please check permissions and try again."),
    PLAYBACK_FAILED("Audio playback error. The file may be invalid."),
    SHARE_FAILED("Failed to share the file. Please try again."),
    PIPELINE_FAILED("Pipeline enhancement failed. The file may be unsupported or corrupted."),
    UNKNOWN_ERROR("An unexpected error occurred. Please try again.")
}

fun Exception.toUserFacingError(): UserFacingError {
    val msg = this.message?.lowercase() ?: ""
    return when {
        msg.contains("format") || msg.contains("mime") -> UserFacingError.UNSUPPORTED_FORMAT
        msg.contains("large") || msg.contains("memory") || msg.contains("alloc") -> UserFacingError.FILE_TOO_LARGE
        msg.contains("decode") || msg.contains("extractor") || msg.contains("riff") -> UserFacingError.DECODE_FAILED
        msg.contains("timeout") -> UserFacingError.PROCESSING_TIMED_OUT
        msg.contains("record") || msg.contains("mic") -> UserFacingError.RECORDING_FAILED
        msg.contains("play") || msg.contains("audiotrack") || msg.contains("mediaplayer") -> UserFacingError.PLAYBACK_FAILED
        msg.contains("share") -> UserFacingError.SHARE_FAILED
        msg.contains("onnx") || msg.contains("ml") -> UserFacingError.ML_FALLBACK
        msg.contains("pipeline") -> UserFacingError.PIPELINE_FAILED
        else -> UserFacingError.UNKNOWN_ERROR
    }
}

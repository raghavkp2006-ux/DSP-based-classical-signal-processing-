package com.signalchain.app.ui

import com.signalchain.app.pipeline.PipelineResult

data class SelectedAudioInfo(
    val name: String,
    val sizeBytes: Long,
    val format: String,
    val sampleRate: Int,
    val durationSec: Float,
    val samples: FloatArray,
    val filePath: String
)

data class RunHistoryItem(
    val id: Long = System.currentTimeMillis(),
    val timestamp: String,
    val fileInfo: SelectedAudioInfo,
    val result: PipelineResult
)

enum class PlayingTrack {
    NONE, INPUT_PREVIEW, ORIGINAL, ENHANCED
}

enum class InputMode {
    FILE, MICROPHONE
}

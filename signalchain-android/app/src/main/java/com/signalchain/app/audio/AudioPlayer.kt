package com.signalchain.app.audio

import android.media.MediaPlayer
import java.io.File

class AudioPlayer {
    private var player: MediaPlayer? = null

    fun play(file: File, onCompletion: () -> Unit = {}) {
        stop()
        player = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnCompletionListener {
                release()
                onCompletion()
            }
            prepare()
            start()
        }
    }

    fun stop() {
        try {
            if (player?.isPlaying == true) {
                player?.stop()
            }
        } catch (_: Exception) {
        } finally {
            player?.release()
            player = null
        }
    }

    fun isPlaying(): Boolean {
        return try {
            player?.isPlaying ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun release() {
        stop()
    }
}

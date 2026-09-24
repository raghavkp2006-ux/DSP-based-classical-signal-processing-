package com.signalchain.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import kotlin.math.max

/**
 * Renders an amplitude-envelope waveform with optional playback-position
 * overlay. Each bar is drawn as a rounded-corner rectangle centered on
 * the vertical midpoint.
 *
 * @param samples     Raw PCM samples (mono float array).
 * @param modifier    Layout modifier.
 * @param color       Bar colour.
 * @param barCount    Number of envelope bars to render.
 * @param progress    Playback progress (0.0–1.0). When > 0, bars before
 *                    the playhead are tinted with [activeColor] and a thin
 *                    playhead line is drawn.
 * @param activeColor Colour for bars that have already been "played".
 * @param onSeek      Called with a fraction 0..1 when the user taps/drags
 *                    on the waveform to seek.
 */
@Composable
fun WaveformView(
    samples: FloatArray,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    barCount: Int = 120,
    progress: Float = 0f,
    activeColor: Color = color,
    onSeek: ((Float) -> Unit)? = null,
) {
    val peaks = remember(samples, barCount) {
        if (samples.isEmpty()) {
            FloatArray(barCount) { 0f }
        } else {
            val chunk = max(1, samples.size / barCount)
            FloatArray(barCount) { i ->
                val start = i * chunk
                val end = minOf(start + chunk, samples.size)
                if (start >= end) 0f else {
                    var m = 0f
                    for (j in start until end) m = max(m, abs(samples[j]))
                    m
                }
            }
        }
    }

    val seekModifier = if (onSeek != null) {
        modifier.pointerInput(Unit) {
            detectTapGestures { offset ->
                val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                onSeek(fraction)
            }
        }
    } else {
        modifier
    }

    Canvas(seekModifier) {
        val barWidth = size.width / barCount
        val gap = barWidth * 0.25f
        val drawWidth = barWidth - gap
        val cornerR = CornerRadius(drawWidth / 2f, drawWidth / 2f)
        val playheadX = progress.coerceIn(0f, 1f) * size.width

        peaks.forEachIndexed { i, amp ->
            val h = (amp.coerceIn(0f, 1f) * size.height * 0.85f).coerceAtLeast(2.5f)
            val x = i * barWidth + gap / 2
            val y = (size.height - h) / 2f
            val isPlayed = progress > 0f && (x + drawWidth / 2) <= playheadX

            drawRoundRect(
                color = if (isPlayed) activeColor else color.copy(alpha = 0.45f),
                topLeft = Offset(x, y),
                size = Size(drawWidth, h),
                cornerRadius = cornerR
            )
        }

        // Playhead line
        if (progress > 0f && progress < 1f) {
            drawLine(
                color = activeColor,
                start = Offset(playheadX, 0f),
                end = Offset(playheadX, size.height),
                strokeWidth = 2f
            )
        }
    }
}

package com.signalchain.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max

@Composable
fun WaveformView(
    samples: FloatArray,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val barCount = 120
    val peaks = remember(samples) {
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
    Canvas(modifier) {
        val barWidth = size.width / barCount
        peaks.forEachIndexed { i, amp ->
            val h = (amp.coerceIn(0f, 1f) * size.height).coerceAtLeast(2f)
            drawLine(
                color = color,
                start = Offset(i * barWidth + barWidth / 2, size.height / 2 - h / 2),
                end = Offset(i * barWidth + barWidth / 2, size.height / 2 + h / 2),
                strokeWidth = (barWidth * 0.7f).coerceAtLeast(1f)
            )
        }
    }
}

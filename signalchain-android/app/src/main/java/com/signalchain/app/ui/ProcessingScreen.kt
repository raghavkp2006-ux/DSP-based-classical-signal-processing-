package com.signalchain.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pipeline stages in execution order — these match the `onProgress()` callback
 * strings emitted by [com.signalchain.app.pipeline.RunPipeline.run].
 */
private val PIPELINE_STAGES = listOf(
    PipelineStage("Loading audio",              "Loading and decoding input audio"),
    PipelineStage("Analyzing signal",           "Measuring SNR, speech activity, and noise profile"),
    PipelineStage("Reducing noise",             "Spectral subtraction + Wiener filter"),
    PipelineStage("Applying EQ and compression","Equalizer and dynamic-range compression"),
    PipelineStage("Running ML post-filter",     "CNN denoiser (ONNX inference on-device)"),
    PipelineStage("Finalizing",                 "Writing enhanced WAV file"),
)

private data class PipelineStage(
    val name: String,
    val description: String,
)

@Composable
fun ProcessingScreen(
    currentStage: String,
) {
    // Determine active stage index by matching the currentStage text
    val activeIndex = remember(currentStage) {
        val match = PIPELINE_STAGES.indexOfFirst {
            currentStage.contains(it.name, ignoreCase = true)
        }
        if (match >= 0) match else 0
    }

    val progress = (activeIndex + 1).toFloat() / PIPELINE_STAGES.size
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 500, easing = EaseInOutCubic),
        label = "progressAnim"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ─── Header ──────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Processing Audio",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Step ${activeIndex + 1} of ${PIPELINE_STAGES.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Percentage badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "${(animatedProgress * 100).toInt()}%",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // ─── Determinate Progress Bar ──────────────────────
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )

            // ─── Stage Steps ─────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PIPELINE_STAGES.forEachIndexed { index, stage ->
                    val status = when {
                        index < activeIndex -> StageStatus.COMPLETED
                        index == activeIndex -> StageStatus.ACTIVE
                        else -> StageStatus.PENDING
                    }
                    StageRow(
                        stage = stage,
                        status = status,
                        isLast = index == PIPELINE_STAGES.lastIndex
                    )
                }
            }
        }
    }
}

private enum class StageStatus { COMPLETED, ACTIVE, PENDING }

@Composable
private fun StageRow(
    stage: PipelineStage,
    status: StageStatus,
    isLast: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        // ─── Step indicator (circle + connecting line) ───
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (status) {
                StageStatus.COMPLETED -> {
                    Surface(
                        modifier = Modifier.size(24.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "✓",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                StageStatus.ACTIVE -> {
                    // Pulsing active indicator
                    val infiniteTransition = rememberInfiniteTransition(label = "stagePulse")
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = EaseInOutCubic),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "stageAlpha"
                    )
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha * 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha))
                        )
                    }
                }
                StageStatus.PENDING -> {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                }
            }

            // Connecting line
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(16.dp)
                        .background(
                            when (status) {
                                StageStatus.COMPLETED -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                StageStatus.ACTIVE -> MaterialTheme.colorScheme.outlineVariant
                                StageStatus.PENDING -> MaterialTheme.colorScheme.outlineVariant
                            }
                        )
                )
            }
        }

        // ─── Stage text ─────────────────────────────────
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stage.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = when (status) {
                    StageStatus.ACTIVE -> FontWeight.Bold
                    StageStatus.COMPLETED -> FontWeight.Medium
                    StageStatus.PENDING -> FontWeight.Normal
                },
                color = when (status) {
                    StageStatus.ACTIVE -> MaterialTheme.colorScheme.onSurface
                    StageStatus.COMPLETED -> MaterialTheme.colorScheme.onSurfaceVariant
                    StageStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                }
            )
            if (status == StageStatus.ACTIVE) {
                Text(
                    text = stage.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

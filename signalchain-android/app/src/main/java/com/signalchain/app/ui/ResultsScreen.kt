package com.signalchain.app.ui

import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.signalchain.app.audio.AudioPlayer
import com.signalchain.app.pipeline.PipelineResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ResultsScreen(
    result: PipelineResult,
    originalAudio: SelectedAudioInfo?,
    playingTrack: PlayingTrack,
    player: AudioPlayer,
    snackbarHostState: SnackbarHostState,
    onPlayingTrackChanged: (PlayingTrack) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Poll player progress for waveform sync
    var playbackProgress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playingTrack) {
        if (playingTrack == PlayingTrack.ORIGINAL || playingTrack == PlayingTrack.ENHANCED) {
            while (true) {
                playbackProgress = player.progressFraction
                delay(50L) // ~20 fps update
            }
        } else {
            playbackProgress = 0f
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // ─── SNR Improvement Hero Card ───────────────────────
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with mode badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Enhancement Complete",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    val (badgeBg, badgeFg) = when {
                        result.mode.contains("adaptive", ignoreCase = true) ||
                                result.mode.contains("aggressive", ignoreCase = true) ->
                            Pair(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                        result.mode.contains("dsp", ignoreCase = true) ||
                                result.mode.contains("mild", ignoreCase = true) ->
                            Pair(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                        else ->
                            Pair(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = badgeBg
                    ) {
                        Text(
                            text = result.mode,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = badgeFg
                        )
                    }
                }

                // SNR metrics — big number hero display
                val delta = result.snrAfterDb - result.snrBeforeDb
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Before
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "BEFORE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "%.1f".format(result.snrBeforeDb),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "dB SNR",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Arrow
                        Text(
                            text = "→",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        // After
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "AFTER",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "%.1f".format(result.snrAfterDb),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "dB SNR",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Delta badge
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (delta >= 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                        ) {
                            Text(
                                text = "%+.1f dB".format(delta),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                // Agent rationale (collapsible-style)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("🤖", fontSize = 14.sp)
                            Text(
                                text = "Agent-Guided Pipeline",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = result.rationale,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // ─── Audio Comparison Card (stacked waveforms) ──────
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Audio Comparison",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // ── Original (Before) ────────────────────────
                AudioTrackCard(
                    label = "Original",
                    sublabel = "Unprocessed input",
                    isPlaying = playingTrack == PlayingTrack.ORIGINAL,
                    samples = originalAudio?.samples ?: FloatArray(0),
                    waveformColor = MaterialTheme.colorScheme.outline,
                    activeWaveformColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    progress = if (playingTrack == PlayingTrack.ORIGINAL) playbackProgress else 0f,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    onPlayToggle = {
                        if (playingTrack == PlayingTrack.ORIGINAL) {
                            player.stop()
                            onPlayingTrackChanged(PlayingTrack.NONE)
                        } else {
                            val path = originalAudio?.filePath
                            if (path != null) {
                                onPlayingTrackChanged(PlayingTrack.ORIGINAL)
                                player.play(
                                    file = File(path),
                                    onError = { errMsg ->
                                        onPlayingTrackChanged(PlayingTrack.NONE)
                                        scope.launch {
                                            snackbarHostState.showSnackbar(errMsg)
                                        }
                                    },
                                    onCompletion = {
                                        onPlayingTrackChanged(PlayingTrack.NONE)
                                    }
                                )
                            }
                        }
                    }
                )

                // ── Enhanced (After) ─────────────────────────
                AudioTrackCard(
                    label = "Enhanced",
                    sublabel = "Processed via ${result.mode}",
                    isPlaying = playingTrack == PlayingTrack.ENHANCED,
                    samples = result.enhancedSamples,
                    waveformColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    activeWaveformColor = MaterialTheme.colorScheme.primary,
                    progress = if (playingTrack == PlayingTrack.ENHANCED) playbackProgress else 0f,
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                    isPrimary = true,
                    onPlayToggle = {
                        if (playingTrack == PlayingTrack.ENHANCED) {
                            player.stop()
                            onPlayingTrackChanged(PlayingTrack.NONE)
                        } else {
                            onPlayingTrackChanged(PlayingTrack.ENHANCED)
                            player.play(
                                file = File(result.outputFilePath),
                                onError = { errMsg ->
                                    onPlayingTrackChanged(PlayingTrack.NONE)
                                    scope.launch {
                                        snackbarHostState.showSnackbar(errMsg)
                                    }
                                },
                                onCompletion = {
                                    onPlayingTrackChanged(PlayingTrack.NONE)
                                }
                            )
                        }
                    }
                )
            }
        }

        // ─── Export / Share ──────────────────────────────────
        Button(
            onClick = {
                try {
                    val file = File(result.outputFilePath)
                    val contentUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        file
                    )
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "audio/wav"
                        putExtra(Intent.EXTRA_STREAM, contentUri)
                        putExtra(Intent.EXTRA_SUBJECT, "Enhanced Audio — SignalChain")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share or Save Enhanced Audio"))
                } catch (e: Exception) {
                    scope.launch {
                        snackbarHostState.showSnackbar("Failed to share file: ${e.message}")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = "📤  Export / Share Enhanced Audio",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

/**
 * A compact audio track card with label, play/stop button, and synced
 * waveform visualization.
 */
@Composable
private fun AudioTrackCard(
    label: String,
    sublabel: String,
    isPlaying: Boolean,
    samples: FloatArray,
    waveformColor: Color,
    activeWaveformColor: Color,
    progress: Float,
    containerColor: Color,
    isPrimary: Boolean = false,
    onPlayToggle: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Play/Stop circle button
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = if (isPrimary)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                        onClick = onPlayToggle
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (isPlaying) "⏹" else "▶",
                                fontSize = 16.sp,
                                color = if (isPrimary)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Column {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isPrimary)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = sublabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Playing indicator
                if (isPlaying) {
                    val infiniteTransition = rememberInfiniteTransition(label = "playing")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(500),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "playAlpha"
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isPrimary)
                            MaterialTheme.colorScheme.primary.copy(alpha = alpha * 0.2f)
                        else
                            MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = "Playing…",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = if (isPrimary)
                                MaterialTheme.colorScheme.primary.copy(alpha = alpha)
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                        )
                    }
                }
            }

            // Waveform with progress overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isPrimary)
                            MaterialTheme.colorScheme.surface
                        else
                            MaterialTheme.colorScheme.surface
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                WaveformView(
                    samples = samples,
                    modifier = Modifier.fillMaxSize(),
                    color = waveformColor,
                    progress = progress,
                    activeColor = activeWaveformColor,
                )
            }
        }
    }
}

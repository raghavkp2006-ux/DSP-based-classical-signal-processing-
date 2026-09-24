package com.signalchain.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.signalchain.app.audio.AudioPlayer
import com.signalchain.app.pipeline.PipelineResult
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Result & Decision Mode
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Enhancement Result",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                val (badgeBg, badgeFg) = when {
                    result.mode.contains("adaptive", ignoreCase = true) || result.mode.contains("aggressive", ignoreCase = true) ->
                        Pair(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                    result.mode.contains("dsp", ignoreCase = true) || result.mode.contains("mild", ignoreCase = true) ->
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

            // Explicit Decision Agent / Pipeline Execution Breakdown Card
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "🤖 Processing Type: ",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Agent-Guided Pipeline",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "• Execution Decision: ${result.mode} (Selected by DecisionAgent based on SNR, stationarity, and speech activity ratio)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "• Agent Rationale: ${result.rationale}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Metrics stats row
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("SNR Before", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("%.1f dB".format(result.snrBeforeDb), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    }
                    Text("→", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("SNR After", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("%.1f dB".format(result.snrAfterDb), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    }
                    val delta = result.snrAfterDb - result.snrBeforeDb
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (delta >= 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                    ) {
                        Text(
                            text = "%+.1f dB".format(delta),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // ─── Direct Audio Comparison Section ─────────
            Text(
                text = "🔊 Listen to Audio Before & After Processing",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            // 1. Before Processing Audio Player
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Before Processing (Original)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Raw unenhanced input audio",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = {
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
                            },
                            modifier = Modifier.height(36.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = if (playingTrack == PlayingTrack.ORIGINAL) "⏹ Stop" else "▶ Play Original",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        val origSamples = originalAudio?.samples ?: FloatArray(0)
                        WaveformView(
                            samples = origSamples,
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // 2. After Processing Audio Player
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "After Processing (Enhanced)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Processed via ${result.mode}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Button(
                            onClick = {
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
                            },
                            modifier = Modifier.height(36.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = if (playingTrack == PlayingTrack.ENHANCED) "⏹ Stop" else "▶ Play Enhanced",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        WaveformView(
                            samples = result.enhancedSamples,
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Share / Export Action
            FilledTonalButton(
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
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Export / Share Enhanced WAV")
            }
        }
    }
}

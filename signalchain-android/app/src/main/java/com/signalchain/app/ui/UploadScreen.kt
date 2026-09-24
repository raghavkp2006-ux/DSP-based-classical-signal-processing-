package com.signalchain.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.signalchain.app.audio.AudioPlayer
import com.signalchain.app.audio.AudioRecorder
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun UploadScreen(
    selectedAudio: SelectedAudioInfo?,
    isProcessing: Boolean,
    isPreparingFile: Boolean,
    playingTrack: PlayingTrack,
    player: AudioPlayer,
    audioRecorder: AudioRecorder,
    snackbarHostState: SnackbarHostState,
    onAudioSelected: (SelectedAudioInfo) -> Unit,
    onAudioCleared: () -> Unit,
    onStartEnhancement: () -> Unit,
    onPlayingTrackChanged: (PlayingTrack) -> Unit,
    onPreparingFileChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Mic recording state
    var inputMode by remember { mutableStateOf(InputMode.FILE) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingJob by remember { mutableStateOf<Job?>(null) }
    var recordingElapsedSec by remember { mutableIntStateOf(0) }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (!granted) {
            scope.launch {
                snackbarHostState.showSnackbar("Microphone permission is required to record audio")
            }
        }
    }

    // Recording timer effect
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingElapsedSec = 0
            while (isRecording) {
                kotlinx.coroutines.delay(1000L)
                if (isRecording) recordingElapsedSec++
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        player.stop()
        onPlayingTrackChanged(PlayingTrack.NONE)
        onPreparingFileChanged(true)
        scope.launch {
            try {
                val prepared = loadAndPrepareAudio(context, uri)
                onAudioSelected(prepared)
            } catch (e: Exception) {
                snackbarHostState.showSnackbar(
                    message = e.message ?: "Failed to read selected audio file"
                )
            } finally {
                onPreparingFileChanged(false)
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Input Audio",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            // Mode toggle tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                horizontalArrangement = Arrangement.Center
            ) {
                InputMode.entries.forEach { mode ->
                    val isSelected = inputMode == mode
                    val bgColor by animateColorAsState(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else Color.Transparent,
                        label = "tabBg"
                    )
                    val textColor by animateColorAsState(
                        if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "tabText"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(bgColor)
                            .clickable(enabled = !isProcessing && !isRecording) {
                                inputMode = mode
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (mode) {
                                InputMode.FILE -> "📁  Upload File"
                                InputMode.MICROPHONE -> "🎙  Record Mic"
                            },
                            color = textColor,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            // ─── FILE MODE ─────────────────────────────────
            if (inputMode == InputMode.FILE) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { picker.launch(arrayOf("audio/*", "application/ogg")) },
                        enabled = !isProcessing && !isPreparingFile
                    ) {
                        Text("Pick Audio File")
                    }

                    if (selectedAudio != null && !isProcessing) {
                        Button(
                            onClick = { onStartEnhancement() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Enhance Audio")
                        }
                    }
                }

                if (isPreparingFile) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Decoding and validating audio…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // ─── MIC MODE ──────────────────────────────────
            if (inputMode == InputMode.MICROPHONE) {
                if (!hasMicPermission) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Microphone permission is needed to record audio directly from your device.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }) {
                            Text("Grant Microphone Permission")
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (isRecording) {
                            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                            val pulseScale by infiniteTransition.animateFloat(
                                initialValue = 1f,
                                targetValue = 1.3f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(600, easing = EaseInOutCubic),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "pulseScale"
                            )
                            val pulseAlpha by infiniteTransition.animateFloat(
                                initialValue = 0.6f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(600, easing = EaseInOutCubic),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "pulseAlpha"
                            )

                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE53935).copy(alpha = pulseAlpha * 0.2f))
                                    .border(2.dp, Color(0xFFE53935).copy(alpha = pulseAlpha), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFE53935))
                                )
                            }

                            Text(
                                text = formatRecordingTime(recordingElapsedSec),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE53935)
                            )

                            Text(
                                text = "Recording…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Button(
                                onClick = {
                                    isRecording = false
                                    audioRecorder.stopRecording()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFE53935)
                                ),
                                modifier = Modifier.fillMaxWidth(0.6f)
                            ) {
                                Text("⏹  Stop Recording")
                            }
                        } else {
                            Text(
                                text = "Tap to start recording from your device microphone",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            FilledTonalButton(
                                onClick = {
                                    player.stop()
                                    onPlayingTrackChanged(PlayingTrack.NONE)
                                    onAudioCleared()
                                    isRecording = true
                                    val outFile = File.createTempFile(
                                        "signalchain-mic-",
                                        ".wav",
                                        context.cacheDir
                                    )
                                    recordingJob = scope.launch {
                                        try {
                                            audioRecorder.startRecording(
                                                output = outFile,
                                                sampleRate = AudioRecorder.DEFAULT_SAMPLE_RATE
                                            )
                                            val result = audioRecorder.getResult()
                                            if (result != null && result.samples.isNotEmpty()) {
                                                onAudioSelected(
                                                    SelectedAudioInfo(
                                                        name = "Mic Recording",
                                                        sizeBytes = result.file.length(),
                                                        format = "PCM WAV (Microphone)",
                                                        sampleRate = result.sampleRate,
                                                        durationSec = result.durationSec,
                                                        samples = result.samples,
                                                        filePath = result.file.absolutePath
                                                    )
                                                )
                                            } else {
                                                snackbarHostState.showSnackbar("Recording was too short or empty")
                                            }
                                        } catch (e: Exception) {
                                            snackbarHostState.showSnackbar(
                                                e.message ?: "Recording failed"
                                            )
                                        } finally {
                                            isRecording = false
                                        }
                                    }
                                },
                                enabled = !isProcessing,
                                modifier = Modifier.fillMaxWidth(0.6f)
                            ) {
                                Text("🎙  Start Recording")
                            }

                            if (selectedAudio != null && !isProcessing &&
                                selectedAudio.format.contains("Microphone")
                            ) {
                                Button(
                                    onClick = { onStartEnhancement() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.fillMaxWidth(0.6f)
                                ) {
                                    Text("Enhance Audio")
                                }
                            }
                        }
                    }
                }
            }

            // ─── SELECTED AUDIO INFO & PREVIEW PLAYER ───────
            selectedAudio?.let { info ->
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = info.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = "${info.format} • ${formatFileSize(info.sizeBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Direct Play/Stop button right on the loaded input audio!
                        FilledTonalButton(
                            onClick = {
                                if (playingTrack == PlayingTrack.INPUT_PREVIEW) {
                                    player.stop()
                                    onPlayingTrackChanged(PlayingTrack.NONE)
                                } else {
                                    onPlayingTrackChanged(PlayingTrack.INPUT_PREVIEW)
                                    player.play(
                                        file = File(info.filePath),
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
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = if (playingTrack == PlayingTrack.INPUT_PREVIEW) "⏹ Stop" else "▶ Listen Audio",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Duration: %.2f s".format(info.durationSec),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Rate: ${info.sampleRate} Hz",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Interactive Waveform of the Input Audio
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        WaveformView(
                            samples = info.samples,
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

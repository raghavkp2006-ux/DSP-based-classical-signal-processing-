package com.signalchain.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // ─── Mode Toggle (File / Mic) ──────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Segmented control for input mode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
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
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(12.dp))
                                .background(bgColor)
                                .clickable(enabled = !isProcessing && !isRecording) {
                                    inputMode = mode
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (mode) {
                                    InputMode.FILE -> "📁  Upload File"
                                    InputMode.MICROPHONE -> "🎙  Record"
                                },
                                color = textColor,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // ─── FILE MODE ─────────────────────────────────────────
        if (inputMode == InputMode.FILE) {
            AnimatedVisibility(
                visible = selectedAudio == null && !isPreparingFile,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                // Empty state — big drop zone
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Dashed border container
                        val borderColor = MaterialTheme.colorScheme.outlineVariant
                        androidx.compose.foundation.Canvas(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(RoundedCornerShape(16.dp))
                        ) {
                            drawRoundRect(
                                color = borderColor,
                                style = Stroke(
                                    width = 2.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(
                                        floatArrayOf(12.dp.toPx(), 8.dp.toPx()),
                                        0f
                                    )
                                ),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx())
                            )
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                    enabled = !isProcessing
                                ) {
                                    picker.launch(arrayOf("audio/*", "application/ogg"))
                                }
                                .padding(vertical = 40.dp, horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Upload icon
                            Surface(
                                modifier = Modifier.size(64.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "🎵",
                                        fontSize = 28.sp
                                    )
                                }
                            }

                            Text(
                                text = "Tap to select an audio file",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = "WAV • MP3 • AAC • OGG • FLAC • M4A",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )

                            Text(
                                text = "Maximum file size: 50 MB",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Loading state while decoding
            AnimatedVisibility(
                visible = isPreparingFile,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(40.dp),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Decoding and validating audio…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // File selected — info card + process CTA
            AnimatedVisibility(
                visible = selectedAudio != null && !isPreparingFile,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                selectedAudio?.let { info ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // File info header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // File icon
                                Surface(
                                    modifier = Modifier.size(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("🎵", fontSize = 22.sp)
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = info.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = info.format,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Change file button
                                TextButton(
                                    onClick = { picker.launch(arrayOf("audio/*", "application/ogg")) },
                                    enabled = !isProcessing
                                ) {
                                    Text("Change", fontWeight = FontWeight.SemiBold)
                                }
                            }

                            // Metadata chips row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MetadataChip(
                                    label = "Duration",
                                    value = "%.1f s".format(info.durationSec)
                                )
                                MetadataChip(
                                    label = "Size",
                                    value = formatFileSize(info.sizeBytes)
                                )
                                MetadataChip(
                                    label = "Rate",
                                    value = "${info.sampleRate} Hz"
                                )
                            }

                            // Waveform preview
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                WaveformView(
                                    samples = info.samples,
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                                )
                            }

                            // Action buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Preview playback
                                OutlinedButton(
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
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = if (playingTrack == PlayingTrack.INPUT_PREVIEW) "⏹  Stop" else "▶  Preview",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                // Process CTA
                                Button(
                                    onClick = { onStartEnhancement() },
                                    enabled = !isProcessing,
                                    modifier = Modifier
                                        .weight(1.5f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text(
                                        text = "✨  Enhance Audio",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ─── MIC MODE ──────────────────────────────────────────
        if (inputMode == InputMode.MICROPHONE) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                if (!hasMicPermission) {
                    // Permission request
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(64.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🎙", fontSize = 28.sp)
                            }
                        }

                        Text(
                            text = "Microphone access needed",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Grant permission to record audio directly from your device.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Grant Permission", fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        if (isRecording) {
                            Spacer(modifier = Modifier.height(8.dp))

                            // Animated recording indicator
                            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                            val pulseScale by infiniteTransition.animateFloat(
                                initialValue = 1f,
                                targetValue = 1.25f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(700, easing = EaseInOutCubic),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "pulseScale"
                            )
                            val pulseAlpha by infiniteTransition.animateFloat(
                                initialValue = 0.5f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(700, easing = EaseInOutCubic),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "pulseAlpha"
                            )

                            Box(
                                modifier = Modifier
                                    .size(88.dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE53935).copy(alpha = pulseAlpha * 0.15f))
                                    .border(2.5.dp, Color(0xFFE53935).copy(alpha = pulseAlpha), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFE53935))
                                )
                            }

                            Text(
                                text = formatRecordingTime(recordingElapsedSec),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE53935)
                            )

                            Text(
                                text = "Recording in progress…",
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
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth(0.65f)
                                    .height(48.dp)
                            ) {
                                Text("⏹  Stop Recording", fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                        } else {
                            // Idle mic state
                            Spacer(modifier = Modifier.height(8.dp))

                            Surface(
                                modifier = Modifier.size(64.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("🎙", fontSize = 28.sp)
                                }
                            }

                            Text(
                                text = "Record from microphone",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Capture audio directly and enhance it on-device",
                                style = MaterialTheme.typography.bodySmall,
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
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth(0.65f)
                                    .height(48.dp)
                            ) {
                                Text("🎙  Start Recording", fontWeight = FontWeight.SemiBold)
                            }

                            // After recording — show selected audio + enhance button
                            if (selectedAudio != null && !isProcessing &&
                                selectedAudio.format.contains("Microphone")
                            ) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = selectedAudio.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "%.1f s • ${formatFileSize(selectedAudio.sizeBytes)}".format(selectedAudio.durationSec),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Button(
                                        onClick = { onStartEnhancement() },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.height(44.dp)
                                    ) {
                                        Text("✨  Enhance", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Small metadata chip used in the file info card */
@Composable
private fun MetadataChip(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

package com.signalchain.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.signalchain.app.audio.AudioDecoder
import com.signalchain.app.audio.AudioPlayer
import com.signalchain.app.audio.AudioRecorder
import com.signalchain.app.audio.WavLoader
import com.signalchain.app.audio.WavWriter
import com.signalchain.app.pipeline.PipelineResult
import com.signalchain.app.pipeline.RunPipeline
import com.signalchain.app.ui.WaveformView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SignalChainScreen()
                }
            }
        }
    }
}

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignalChainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val player = remember { AudioPlayer() }
    val audioRecorder = remember { AudioRecorder() }

    var selectedAudio by remember { mutableStateOf<SelectedAudioInfo?>(null) }
    var isPreparingFile by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var currentStage by remember { mutableStateOf("") }
    var activeResult by remember { mutableStateOf<PipelineResult?>(null) }
    var playingTrack by remember { mutableStateOf(PlayingTrack.NONE) }
    val history = remember { mutableStateListOf<RunHistoryItem>() }

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

    DisposableEffect(Unit) {
        onDispose {
            player.release()
            audioRecorder.release()
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        player.stop()
        playingTrack = PlayingTrack.NONE
        isPreparingFile = true
        scope.launch {
            try {
                val prepared = loadAndPrepareAudio(context, uri)
                selectedAudio = prepared
                activeResult = null
            } catch (e: Exception) {
                snackbarHostState.showSnackbar(
                    message = e.message ?: "Failed to read selected audio file"
                )
            } finally {
                isPreparingFile = false
            }
        }
    }

    // Shared enhance function
    fun startEnhancement() {
        val audio = selectedAudio ?: return
        isProcessing = true
        currentStage = "Starting pipeline…"
        player.stop()
        playingTrack = PlayingTrack.NONE

        scope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    RunPipeline(context).run(audio.filePath) { stage ->
                        scope.launch(Dispatchers.Main) {
                            currentStage = stage
                        }
                    }
                }
                activeResult = res
                val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                history.add(0, RunHistoryItem(timestamp = timeStr, fileInfo = audio, result = res))
            } catch (e: Exception) {
                snackbarHostState.showSnackbar(
                    message = e.message ?: "Pipeline enhancement failed"
                )
            } finally {
                isProcessing = false
                currentStage = ""
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "SignalChain",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = "ON-DEVICE DSP",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ─── 1. Input Mode & Selection Card ─────────────────────
            item {
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
                                        onClick = { startEnhancement() },
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
                                                playingTrack = PlayingTrack.NONE
                                                activeResult = null
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
                                                            selectedAudio = SelectedAudioInfo(
                                                                name = "Mic Recording",
                                                                sizeBytes = result.file.length(),
                                                                format = "PCM WAV (Microphone)",
                                                                sampleRate = result.sampleRate,
                                                                durationSec = result.durationSec,
                                                                samples = result.samples,
                                                                filePath = result.file.absolutePath
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
                                            selectedAudio?.format?.contains("Microphone") == true
                                        ) {
                                            Button(
                                                onClick = { startEnhancement() },
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
                                                playingTrack = PlayingTrack.NONE
                                            } else {
                                                playingTrack = PlayingTrack.INPUT_PREVIEW
                                                player.play(
                                                    file = File(info.filePath),
                                                    onError = { errMsg ->
                                                        playingTrack = PlayingTrack.NONE
                                                        scope.launch {
                                                            snackbarHostState.showSnackbar(errMsg)
                                                        }
                                                    },
                                                    onCompletion = {
                                                        playingTrack = PlayingTrack.NONE
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

            // ─── 2. Live Progress Card ──────────────────────────────
            if (isProcessing) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = currentStage.ifBlank { "Enhancing audio…" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // ─── 3. Result & Comparison Card ────────────────────────
            activeResult?.let { res ->
                val currentAudio = selectedAudio
                item {
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
                                    res.mode.contains("adaptive", ignoreCase = true) || res.mode.contains("aggressive", ignoreCase = true) ->
                                        Pair(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                                    res.mode.contains("dsp", ignoreCase = true) || res.mode.contains("mild", ignoreCase = true) ->
                                        Pair(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                                    else ->
                                        Pair(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = badgeBg
                                ) {
                                    Text(
                                        text = res.mode,
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
                                        text = "• Execution Decision: ${res.mode} (Selected by DecisionAgent based on SNR, stationarity, and speech activity ratio)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Text(
                                        text = "• Agent Rationale: ${res.rationale}",
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
                                        Text("%.1f dB".format(res.snrBeforeDb), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                    }
                                    Text("→", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("SNR After", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("%.1f dB".format(res.snrAfterDb), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                    }
                                    val delta = res.snrAfterDb - res.snrBeforeDb
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
                                                    playingTrack = PlayingTrack.NONE
                                                } else {
                                                    val path = currentAudio?.filePath
                                                    if (path != null) {
                                                        playingTrack = PlayingTrack.ORIGINAL
                                                        player.play(
                                                            file = File(path),
                                                            onError = { errMsg ->
                                                                playingTrack = PlayingTrack.NONE
                                                                scope.launch {
                                                                    snackbarHostState.showSnackbar(errMsg)
                                                                }
                                                            },
                                                            onCompletion = {
                                                                playingTrack = PlayingTrack.NONE
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
                                        val origSamples = currentAudio?.samples ?: FloatArray(0)
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
                                                text = "Processed via ${res.mode}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                if (playingTrack == PlayingTrack.ENHANCED) {
                                                    player.stop()
                                                    playingTrack = PlayingTrack.NONE
                                                } else {
                                                    playingTrack = PlayingTrack.ENHANCED
                                                    player.play(
                                                        file = File(res.outputFilePath),
                                                        onError = { errMsg ->
                                                            playingTrack = PlayingTrack.NONE
                                                            scope.launch {
                                                                snackbarHostState.showSnackbar(errMsg)
                                                            }
                                                        },
                                                        onCompletion = {
                                                            playingTrack = PlayingTrack.NONE
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
                                            samples = res.enhancedSamples,
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
                                        val file = File(res.outputFilePath)
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
            }

            // ─── 4. Session Run History ─────────────────────────────
            if (history.isNotEmpty()) {
                item {
                    Text(
                        text = "Session History",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(history, key = { it.id }) { item ->
                    val isSelected = activeResult?.outputFilePath == item.result.outputFilePath
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                player.stop()
                                playingTrack = PlayingTrack.NONE
                                selectedAudio = item.fileInfo
                                activeResult = item.result
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.fileInfo.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                                Text(
                                    text = "${item.result.mode} • ${item.timestamp}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            val delta = item.result.snrAfterDb - item.result.snrBeforeDb
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
                }
            }
        }
    }
}

// ─── Helper Functions ─────────────────────────────────────────────

fun formatRecordingTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}

suspend fun loadAndPrepareAudio(context: Context, uri: Uri): SelectedAudioInfo = withContext(Dispatchers.IO) {
    val (name, size) = queryFileMetadata(context, uri)
    val temp = File.createTempFile("signalchain-input-", ".wav", context.cacheDir)
    context.contentResolver.openInputStream(uri)?.use { input ->
        temp.outputStream().use { out -> input.copyTo(out) }
    } ?: error("Failed to open audio stream from selected URI")

    val maxBytes = 50L * 1024 * 1024
    val actualSize = if (size > 0) size else temp.length()
    require(actualSize in 1..maxBytes) { "File is empty or larger than the 50 MB limit." }

    val isRiffWav = temp.length() >= 12 && temp.inputStream().use { s ->
        val magic = ByteArray(4)
        s.read(magic) == 4 && String(magic) == "RIFF"
    }

    var loadedAudio: WavLoader.LoadedAudio? = null
    var detectedFormat = "Audio File"

    // If it looks like a WAV file, try standard WavLoader first
    if (isRiffWav) {
        try {
            loadedAudio = WavLoader.load(temp.absolutePath)
            detectedFormat = "PCM WAV (Native)"
        } catch (e: Exception) {
            android.util.Log.w("SignalChain", "WavLoader could not parse RIFF as standard PCM WAV (${e.message}), falling back to MediaCodec decoder")
        }
    }

    // If not standard PCM WAV or if WavLoader failed (e.g. MP3, AAC, M4A, OGG, FLAC, floating-point/compressed WAV):
    if (loadedAudio == null) {
        try {
            loadedAudio = AudioDecoder.decodeToMonoPcm(context, uri)
            detectedFormat = "Decoded via MediaCodec"
        } catch (e: Exception) {
            // Also try decoding from the cached temp file directly if URI decoding had issues
            try {
                loadedAudio = AudioDecoder.decodeToMonoPcm(context, Uri.fromFile(temp))
                detectedFormat = "Decoded via MediaCodec"
            } catch (_: Exception) {
                // Throw original error with friendly message
                throw e
            }
        }
    }

    val validAudio = requireNotNull(loadedAudio) { "Unable to decode audio format. Please check if file is valid." }

    // Always normalize and save to a clean, canonical PCM 16-bit mono WAV file
    // This ensures that RunPipeline.run() and AudioPlayer.play() ALWAYS succeed without format errors!
    val canonicalWav = File.createTempFile("signalchain-canonical-", ".wav", context.cacheDir)
    WavWriter.write(canonicalWav.absolutePath, validAudio.samples, validAudio.sampleRate)
    val duration = validAudio.samples.size.toFloat() / validAudio.sampleRate

    SelectedAudioInfo(
        name = name,
        sizeBytes = actualSize,
        format = detectedFormat,
        sampleRate = validAudio.sampleRate,
        durationSec = duration,
        samples = validAudio.samples,
        filePath = canonicalWav.absolutePath
    )
}

fun queryFileMetadata(context: Context, uri: Uri): Pair<String, Long> {
    var name = "audio_file"
    var size = 0L
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                if (sizeIndex >= 0) size = cursor.getLong(sizeIndex)
            }
        }
    } catch (_: Exception) {
    }
    return Pair(name, size)
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) "%.1f MB".format(mb) else "%.1f KB".format(kb)
}

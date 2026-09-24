package com.signalchain.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.signalchain.app.audio.AudioPlayer
import com.signalchain.app.audio.AudioRecorder
import com.signalchain.app.pipeline.PipelineResult
import com.signalchain.app.pipeline.RunPipeline
import com.signalchain.app.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    DisposableEffect(Unit) {
        onDispose {
            player.release()
            audioRecorder.release()
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "SignalChain",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = "ON-DEVICE",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
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
            // ─── 1. Input Mode & Selection ──────────────────
            item {
                UploadScreen(
                    selectedAudio = selectedAudio,
                    isProcessing = isProcessing,
                    isPreparingFile = isPreparingFile,
                    playingTrack = playingTrack,
                    player = player,
                    audioRecorder = audioRecorder,
                    snackbarHostState = snackbarHostState,
                    onAudioSelected = { audio ->
                        selectedAudio = audio
                        activeResult = null
                    },
                    onAudioCleared = {
                        activeResult = null
                    },
                    onStartEnhancement = { startEnhancement() },
                    onPlayingTrackChanged = { playingTrack = it },
                    onPreparingFileChanged = { isPreparingFile = it },
                )
            }

            // ─── 2. Live Progress ───────────────────────────
            if (isProcessing) {
                item {
                    ProcessingScreen(
                        currentStage = currentStage,
                    )
                }
            }

            // ─── 3. Results & Comparison ────────────────────
            activeResult?.let { res ->
                item {
                    ResultsScreen(
                        result = res,
                        originalAudio = selectedAudio,
                        playingTrack = playingTrack,
                        player = player,
                        snackbarHostState = snackbarHostState,
                        onPlayingTrackChanged = { playingTrack = it },
                    )
                }
            }

            // ─── 4. Session History ─────────────────────────
            if (history.isNotEmpty()) {
                item {
                    HistorySection(
                        history = history.toList(),
                        activeResultPath = activeResult?.outputFilePath,
                        onItemSelected = { item ->
                            player.stop()
                            playingTrack = PlayingTrack.NONE
                            selectedAudio = item.fileInfo
                            activeResult = item.result
                        },
                    )
                }
            }
        }
    }
}

package com.signalchain.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.signalchain.app.audio.AudioDecoder
import com.signalchain.app.audio.AudioPlayer
import com.signalchain.app.audio.WavLoader
import com.signalchain.app.audio.WavWriter
import com.signalchain.app.pipeline.PipelineResult
import com.signalchain.app.pipeline.RunPipeline
import com.signalchain.app.ui.WaveformView
import kotlinx.coroutines.Dispatchers
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
    NONE, ORIGINAL, ENHANCED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignalChainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val player = remember { AudioPlayer() }

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
            // 1. File Selection Card
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
                                    onClick = {
                                        val audio = selectedAudio ?: return@Button
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
                                    },
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

                        selectedAudio?.let { info ->
                            HorizontalDivider()
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = info.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
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
                                    Text(
                                        text = formatFileSize(info.sizeBytes),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "Format: ${info.format}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // 2. Live Progress Card
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

            // 3. Result & Comparison Card
            activeResult?.let { res ->
                val currentAudio = selectedAudio
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
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Mode & Rationale Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Enhancement Result",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )

                                val (badgeBg, badgeFg) = when (res.mode.uppercase()) {
                                    "AGGRESSIVE" -> Pair(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                                    "MILD" -> Pair(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                                    else -> Pair(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
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

                            Text(
                                text = res.rationale,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

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

                            // Waveform Stack
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                // Original Waveform
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Original Audio",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        OutlinedButton(
                                            onClick = {
                                                if (playingTrack == PlayingTrack.ORIGINAL) {
                                                    player.stop()
                                                    playingTrack = PlayingTrack.NONE
                                                } else {
                                                    val path = currentAudio?.filePath
                                                    if (path != null) {
                                                        playingTrack = PlayingTrack.ORIGINAL
                                                        player.play(File(path)) {
                                                            playingTrack = PlayingTrack.NONE
                                                        }
                                                    }
                                                }
                                            },
                                            modifier = Modifier.height(32.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                        ) {
                                            Text(if (playingTrack == PlayingTrack.ORIGINAL) "⏹ Stop" else "▶ Play Original")
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(64.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
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

                                // Enhanced Waveform
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Enhanced Audio",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Button(
                                            onClick = {
                                                if (playingTrack == PlayingTrack.ENHANCED) {
                                                    player.stop()
                                                    playingTrack = PlayingTrack.NONE
                                                } else {
                                                    playingTrack = PlayingTrack.ENHANCED
                                                    player.play(File(res.outputFilePath)) {
                                                        playingTrack = PlayingTrack.NONE
                                                    }
                                                }
                                            },
                                            modifier = Modifier.height(32.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                        ) {
                                            Text(if (playingTrack == PlayingTrack.ENHANCED) "⏹ Stop" else "▶ Play Enhanced")
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(64.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
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

            // 4. Session Run History
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

suspend fun loadAndPrepareAudio(context: Context, uri: Uri): SelectedAudioInfo = withContext(Dispatchers.IO) {
    val (name, size) = queryFileMetadata(context, uri)
    val temp = File.createTempFile("signalchain-input-", ".wav", context.cacheDir)
    context.contentResolver.openInputStream(uri)?.use { input ->
        temp.outputStream().use { out -> input.copyTo(out) }
    } ?: error("Failed to open audio stream from selected URI")

    val maxBytes = 50L * 1024 * 1024
    val actualSize = if (size > 0) size else temp.length()
    require(actualSize in 1..maxBytes) { "File is empty or larger than the 50 MB limit." }

    val isWav = temp.length() >= 12 && temp.inputStream().use { s ->
        val magic = ByteArray(4)
        s.read(magic) == 4 && String(magic) == "RIFF"
    }

    if (isWav) {
        val loaded = WavLoader.load(temp.absolutePath)
        val duration = loaded.samples.size.toFloat() / loaded.sampleRate
        SelectedAudioInfo(
            name = name,
            sizeBytes = actualSize,
            format = "PCM WAV (Native)",
            sampleRate = loaded.sampleRate,
            durationSec = duration,
            samples = loaded.samples,
            filePath = temp.absolutePath
        )
    } else {
        val decoded = AudioDecoder.decodeToMonoPcm(context, uri)
        val decodedWav = File.createTempFile("signalchain-decoded-", ".wav", context.cacheDir)
        WavWriter.write(decodedWav.absolutePath, decoded.samples, decoded.sampleRate)
        val duration = decoded.samples.size.toFloat() / decoded.sampleRate
        SelectedAudioInfo(
            name = name,
            sizeBytes = actualSize,
            format = "Decoded via MediaCodec",
            sampleRate = decoded.sampleRate,
            durationSec = duration,
            samples = decoded.samples,
            filePath = decodedWav.absolutePath
        )
    }
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

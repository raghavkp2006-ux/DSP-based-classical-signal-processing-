package com.signalchain.app.pipeline
import com.signalchain.app.agent.*;import com.signalchain.app.audio.*;import com.signalchain.app.dsp.*;import com.signalchain.app.ml.MlPostFilter;import com.signalchain.app.ml.OnnxDenoiser;import android.content.Context;import java.io.File
data class PipelineResult(
    val mode: String,
    val rationale: String,
    val snrBeforeDb: Double,
    val snrAfterDb: Double,
    val outputFilePath: String,
    val clipsPct: Double,
    val enhancedSamples: FloatArray = FloatArray(0),
    val warning: String? = null
)
class RunPipeline(private val context: Context) {
    fun run(inputPath: String, onProgress: (String) -> Unit = {}): PipelineResult {
        onProgress("Loading audio")
        val raw = WavLoader.load(inputPath)
        val core = PipelineCore.process(raw.samples, raw.sampleRate, { OnnxDenoiser(context) }, onProgress)
        android.util.Log.i("SignalChain", "AnalysisResult(snrDb=${core.snrDb}, noiseStationarityCv=${core.noiseStationarityCv}, stationaryNoise=${core.stationaryNoise}, speechActivityRatio=${core.speechActivityRatio}); chosenMode=${core.mode}")
        onProgress("Finalizing")
        val exportDir = File(context.cacheDir, "exports")
        check(exportDir.exists() || exportDir.mkdirs()) { "Unable to create export directory" }
        val out = File(exportDir, "signalchain-enhanced-${System.currentTimeMillis()}.wav")
        try {
            WavWriter.write(out.absolutePath, core.enhancedSamples, raw.sampleRate)
        } catch (e: Exception) {
            out.delete()
            throw e
        }
        return PipelineResult(
            core.mode,
            core.rationale,
            core.snrBeforeDb,
            core.snrAfterDb,
            out.absolutePath,
            core.clipsPct,
            enhancedSamples = core.enhancedSamples,
            warning = core.warning
        )
    }

    fun run(inputPath: String): PipelineResult = run(inputPath) {}
}

package com.signalchain.app.pipeline
import com.signalchain.app.agent.*;import com.signalchain.app.audio.*;import com.signalchain.app.dsp.*;import com.signalchain.app.ml.OnnxDenoiser;import android.content.Context;import java.io.File
data class PipelineResult(
    val mode: String,
    val rationale: String,
    val snrBeforeDb: Double,
    val snrAfterDb: Double,
    val outputFilePath: String,
    val clipsPct: Double,
    val enhancedSamples: FloatArray = FloatArray(0)
)
class RunPipeline(private val context: Context) {
    fun run(inputPath: String, onProgress: (String) -> Unit = {}): PipelineResult {
        onProgress("Loading audio")
        val raw = WavLoader.load(inputPath)
        val x = raw.samples
        val fp = FrameParams.create(x.size, raw.sampleRate)
        val fixed = Vad.detect(PreEmphasis.apply(x), fp)
        val before = SnrEstimator.estimateSnr(x, fp, fixed.isSpeech)
        onProgress("Analyzing signal")
        val a = AudioAnalyzer.analyze(x, raw.sampleRate)
        val d = DecisionAgent.decide(a.snrDb, a.stationaryNoise, a.speechActivityRatio)
        onProgress("Reducing noise")
        val pre = PreEmphasis.apply(x, d.params.preEmphCoeff)
        val v = Vad.detect(pre, fp, d.params.vadHangoverFrames)
        val noise = NoisePsd.estimate(pre, fp, v.isSpeech)
        var y = PreEmphasis.deEmphasize(SpectralSubtraction.apply(pre, fp, noise, x.size, d.params.alpha, d.params.beta, d.params.gainSmooth, d.params.useSpectralSubtraction), d.params.preEmphCoeff)
        onProgress("Applying EQ and compression")
        y = Eq.apply(y, raw.sampleRate, if (d.params.useEq) d.params.eqGain else 0.0)
        y = Compressor.compress(y, raw.sampleRate, d.params.compressionRatio, d.params.compressionMakeupDb)
        var normalized = Compressor.normalizeAndLimit(y, x)
        onProgress("Running ML post-filter")
        if (d.params.useMlPostfilter) {
            try {
                normalized = Compressor.normalizeAndLimit(OnnxDenoiser(context).apply(normalized.audio, raw.sampleRate), x)
            } catch (e: Exception) {
                android.util.Log.e("SignalChain", "ML post-filter failed, falling back to DSP-only output", e)
            }
        }
        onProgress("Finalizing")
        val out = File(context.cacheDir, "signalchain-enhanced-${System.currentTimeMillis()}.wav")
        WavWriter.write(out.absolutePath, normalized.audio, raw.sampleRate)
        return PipelineResult(
            d.mode,
            d.rationale,
            before,
            SnrEstimator.estimateSnr(normalized.audio, fp, fixed.isSpeech),
            out.absolutePath,
            normalized.clipsPct,
            enhancedSamples = normalized.audio
        )
    }

    fun run(inputPath: String): PipelineResult = run(inputPath) {}
}

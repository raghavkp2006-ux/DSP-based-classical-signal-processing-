package com.signalchain.app.pipeline

import com.signalchain.app.agent.*
import com.signalchain.app.dsp.*
import com.signalchain.app.ml.*

data class CoreResult(val mode:String,val rationale:String,val snrDb:Double,val noiseStationarityCv:Double,val stationaryNoise:Boolean,val speechActivityRatio:Double,val snrBeforeDb:Double,val snrAfterDb:Double,val enhancedSamples:FloatArray,val clipsPct:Double,val warning:String?)

object PipelineCore {
    fun process(samples:FloatArray,sampleRate:Int,denoiserFactory:(() -> OnnxDenoiser)?,onProgress:(String)->Unit={},onStage:((String,FloatArray)->Unit)?=null):CoreResult =
        processInternal(samples,sampleRate,denoiserFactory,onProgress,onStage,false)

    /** Evaluation-only arm; the production entry point always uses the aligned EQ. */
    internal fun processForEvaluation(samples:FloatArray,sampleRate:Int,denoiserFactory:(() -> OnnxDenoiser)?,legacyEq:Boolean=false,onStage:((String,FloatArray)->Unit)?=null):CoreResult =
        processInternal(samples,sampleRate,denoiserFactory,{},onStage,legacyEq)

    private fun processInternal(samples:FloatArray,sampleRate:Int,denoiserFactory:(() -> OnnxDenoiser)?,onProgress:(String)->Unit,onStage:((String,FloatArray)->Unit)?,legacyEq:Boolean):CoreResult {
        val x=samples
        onStage?.invoke("00_input", x)
        val fp=FrameParams.create(x.size,sampleRate)
        val fixed=Vad.detect(PreEmphasis.apply(x),fp)
        val before=SnrEstimator.estimateSnr(x,fp,fixed.isSpeech)
        onProgress("Analyzing signal")
        val a=AudioAnalyzer.analyze(x,sampleRate)
        val d=DecisionAgent.decide(a.snrDb,a.stationaryNoise,a.speechActivityRatio)
        onProgress("Reducing noise")
        val pre=PreEmphasis.apply(x,d.params.preEmphCoeff)
        onStage?.invoke("01_preemph", pre)
        val v=Vad.detect(pre,fp,d.params.vadHangoverFrames)
        onStage?.invoke("02_vad", FloatArray(v.isSpeech.size) { if(v.isSpeech[it]) 1f else 0f })
        val noise=NoisePsd.estimate(pre,fp,v.isSpeech)
        onStage?.invoke("02_noise_psd", noise)
        val spectral=SpectralSubtraction.apply(pre,fp,noise,x.size,d.params.alpha,d.params.beta,d.params.gainSmooth,d.params.useSpectralSubtraction)
        onStage?.invoke("03_spectral", spectral)
        var y=PreEmphasis.deEmphasize(spectral,d.params.preEmphCoeff)
        onStage?.invoke("03_specsub_deemph", y)
        onProgress("Applying EQ and compression")
        val eqGain=if(d.params.useEq) d.params.eqGain else 0.0
        y=if(legacyEq) Eq.apply(y,sampleRate,eqGain) else Eq.applyAligned(y,sampleRate,eqGain)
        onStage?.invoke("04_eq", y)
        y=Compressor.compress(y,sampleRate,d.params.compressionRatio,d.params.compressionMakeupDb)
        onStage?.invoke("05_compressor", y)
        var normalized=Compressor.normalizeAndLimit(y,x)
        onStage?.invoke("06_normalized", normalized.audio)
        onProgress("Running ML post-filter")
        var warning:String?=null
        if(d.params.useMlPostfilter) {
            val post=MlPostFilter.applyOrFallback(normalized) {
                val ml = requireNotNull(denoiserFactory)().use { it.applyBounded(normalized.audio,sampleRate) }
                onStage?.invoke("07_ml_postfilter", ml)
                Compressor.normalizeAndLimit(ml,x)
            }
            normalized=post.value
            warning=post.warning
        }
        onStage?.invoke("08_final", normalized.audio)
        return CoreResult(d.mode,d.rationale,a.snrDb,a.noiseStationarityCv,a.stationaryNoise,a.speechActivityRatio,before,SnrEstimator.estimateSnr(normalized.audio,fp,fixed.isSpeech),normalized.audio,normalized.clipsPct,warning)
    }
}

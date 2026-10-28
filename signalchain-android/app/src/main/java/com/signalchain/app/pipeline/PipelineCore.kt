package com.signalchain.app.pipeline

import com.signalchain.app.agent.*
import com.signalchain.app.dsp.*
import com.signalchain.app.ml.*

data class CoreResult(val mode:String,val rationale:String,val snrDb:Double,val noiseStationarityCv:Double,val stationaryNoise:Boolean,val speechActivityRatio:Double,val snrBeforeDb:Double,val snrAfterDb:Double,val enhancedSamples:FloatArray,val clipsPct:Double,val warning:String?)

object PipelineCore {
    fun process(samples:FloatArray,sampleRate:Int,denoiserFactory:(() -> OnnxDenoiser)?,onProgress:(String)->Unit={}):CoreResult {
        val x=samples
        val fp=FrameParams.create(x.size,sampleRate)
        val fixed=Vad.detect(PreEmphasis.apply(x),fp)
        val before=SnrEstimator.estimateSnr(x,fp,fixed.isSpeech)
        onProgress("Analyzing signal")
        val a=AudioAnalyzer.analyze(x,sampleRate)
        val d=DecisionAgent.decide(a.snrDb,a.stationaryNoise,a.speechActivityRatio)
        onProgress("Reducing noise")
        val pre=PreEmphasis.apply(x,d.params.preEmphCoeff)
        val v=Vad.detect(pre,fp,d.params.vadHangoverFrames)
        val noise=NoisePsd.estimate(pre,fp,v.isSpeech)
        var y=PreEmphasis.deEmphasize(SpectralSubtraction.apply(pre,fp,noise,x.size,d.params.alpha,d.params.beta,d.params.gainSmooth,d.params.useSpectralSubtraction),d.params.preEmphCoeff)
        onProgress("Applying EQ and compression")
        y=Eq.applyAligned(y,sampleRate,if(d.params.useEq) d.params.eqGain else 0.0)
        y=Compressor.compress(y,sampleRate,d.params.compressionRatio,d.params.compressionMakeupDb)
        var normalized=Compressor.normalizeAndLimit(y,x)
        onProgress("Running ML post-filter")
        var warning:String?=null
        if(d.params.useMlPostfilter) {
            val post=MlPostFilter.applyOrFallback(normalized) {
                Compressor.normalizeAndLimit(requireNotNull(denoiserFactory)().use { it.applyBounded(normalized.audio,sampleRate) },x)
            }
            normalized=post.value
            warning=post.warning
        }
        return CoreResult(d.mode,d.rationale,a.snrDb,a.noiseStationarityCv,a.stationaryNoise,a.speechActivityRatio,before,SnrEstimator.estimateSnr(normalized.audio,fp,fixed.isSpeech),normalized.audio,normalized.clipsPct,warning)
    }
}

package com.signalchain.app.pipeline

import com.signalchain.app.agent.*
import com.signalchain.app.dsp.*
import com.signalchain.app.ml.*
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.sin

/** Captures the exact inline production chain before extraction. */
class PipelineSnapshotTest {
    @Test fun capture() {
        val fs=16000
        for (name in listOf("steady", "noisy")) {
            val rng=java.util.Random(42)
            val x=FloatArray(fs*2) { i ->
                val gate=if (i in 3200 until 24000) 1.0 else 0.0
                if (name=="steady") (0.018*sin(2*Math.PI*73*i/fs)+0.16*sin(2*Math.PI*223*i/fs)*gate).toFloat()
                else ((sin(2*Math.PI*300*i/fs)*0.3*gate)+rng.nextGaussian()*0.15).toFloat()
            }
            val fp=FrameParams.create(x.size,fs)
            val a=AudioAnalyzer.analyze(x,fs)
            val d=DecisionAgent.decide(a.snrDb,a.stationaryNoise,a.speechActivityRatio)
            assertEquals(if (name=="steady") "Light touch" else "Full adaptive", d.mode)
            val p=d.params
            val pre=PreEmphasis.apply(x,p.preEmphCoeff)
            val vad=Vad.detect(pre,fp,p.vadHangoverFrames)
            val noise=NoisePsd.estimate(pre,fp,vad.isSpeech)
            val sub=SpectralSubtraction.apply(pre,fp,noise,x.size,p.alpha,p.beta,p.gainSmooth,p.useSpectralSubtraction)
            val de=PreEmphasis.deEmphasize(sub,p.preEmphCoeff)
            val eq=Eq.applyAligned(de,fs,if(p.useEq) p.eqGain else 0.0)
            var result=Compressor.normalizeAndLimit(Compressor.compress(eq,fs,p.compressionRatio,p.compressionMakeupDb),x)
            if(p.useMlPostfilter) {
                val bytes=javaClass.classLoader!!.getResourceAsStream("models/spectral_mask_denoiser.onnx")!!.use{it.readBytes()}
                val filtered=MlPostFilter.applyOrFallback(result) { Compressor.normalizeAndLimit(OnnxDenoiser(bytes).use{it.applyBounded(result.audio,fs)},x) }
                assertNull(filtered.warning)
                result=filtered.value
            }
            val buf=ByteBuffer.allocate(result.audio.size*4).order(ByteOrder.LITTLE_ENDIAN)
            result.audio.forEach{buf.putFloat(it)}
            val hash=MessageDigest.getInstance("SHA-256").digest(buf.array()).joinToString(""){"%02x".format(it)}
            assertEquals(if (name=="steady") "217034ad62d7d8771eb5607cefa8cad41733a9eaee7d0a512d266c325d73b21d" else "4b16540a4d2eb8025afc5265de1c2e06ff278b808770f0c021615fa739e72db7", hash)
            println("SNAPSHOT $name mode=${d.mode} hash=$hash")
        }
    }
}

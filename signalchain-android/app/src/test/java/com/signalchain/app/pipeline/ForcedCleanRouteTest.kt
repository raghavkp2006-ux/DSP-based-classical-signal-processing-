package com.signalchain.app.pipeline
import com.signalchain.app.agent.*
import com.signalchain.app.audio.*
import com.signalchain.app.dsp.*
import com.signalchain.app.ml.OnnxDenoiser
import org.junit.Test
import java.io.File
class ForcedCleanRouteTest {
 @Test fun exportForcedRoutes() {
  val root=File(System.getenv("SIGNALCHAIN_ROOT") ?: return)
  val bytes=javaClass.classLoader!!.getResourceAsStream("models/spectral_mask_denoiser.onnx")!!.use{it.readBytes()}
  for(ds in listOf("original","padded")) {
   val data=File(root,if(ds=="original")"ablation_testset/clean" else "ablation_testset_padded/clean")
   for(f in data.listFiles()!!.filter{it.extension=="wav"}.sortedBy{it.name}) {
    val raw=WavLoader.load(f.absolutePath);val x=raw.samples;val fs=raw.sampleRate;val fp=FrameParams.create(x.size,fs)
    for(mode in listOf("classical","full")) {
     val p=if(mode=="classical") DecisionAgent.decide(10.0,true,1.0).params else DecisionAgent.decide(0.0,false,1.0).params
     val pre=PreEmphasis.apply(x,p.preEmphCoeff);val vad=Vad.detect(pre,fp,p.vadHangoverFrames);val noise=NoisePsd.estimate(pre,fp,vad.isSpeech)
     val spectral=SpectralSubtraction.apply(pre,fp,noise,x.size,p.alpha,p.beta,p.gainSmooth,p.useSpectralSubtraction)
     val de=PreEmphasis.deEmphasize(spectral,p.preEmphCoeff);val eq=Eq.applyAligned(de,fs,if(p.useEq)p.eqGain else 0.0)
     val comp=Compressor.compress(eq,fs,p.compressionRatio,p.compressionMakeupDb);var n=Compressor.normalizeAndLimit(comp,x)
     if(p.useMlPostfilter){val ml=OnnxDenoiser(bytes).use{it.applyBounded(n.audio,fs)};n=Compressor.normalizeAndLimit(ml,x)}
     val out=File(root,"ablation_output/stftfix_${ds}/forced_kotlin_${mode}").also{it.mkdirs()};WavWriter.write(File(out,f.name).absolutePath,n.audio,fs)
     println("FORCED_CLEAN $ds ${f.name} $mode")
    }
   }
  }
 }
}

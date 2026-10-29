package com.signalchain.app.pipeline
import com.signalchain.app.agent.*
import com.signalchain.app.audio.*
import com.signalchain.app.dsp.*
import com.signalchain.app.ml.OnnxDenoiser
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import kotlin.math.abs
class HangoverSensitivityExportTest {
 @Test fun export() {
  val root=File(System.getenv("SIGNALCHAIN_ROOT") ?: return)
  val bytes=javaClass.classLoader!!.getResourceAsStream("models/spectral_mask_denoiser.onnx")!!.use{it.readBytes()}
  for(ds in listOf("original","padded")) {
   val data=File(root,if(ds=="original")"ablation_testset" else "ablation_testset_padded")
   val prefix=System.getenv("SIGNALCHAIN_SENSITIVITY_PREFIX") ?: "sensitivity"
   val base=File(root,"ablation_output/${prefix}_$ds").also{it.mkdirs()}
   for((idx,line) in File(data,"metadata.csv").readLines().drop(1).withIndex()) {
    val name=line.split(',')[5].replace('\\','/').substringAfterLast('/');val wav=WavLoader.load(File(data,"noisy/$name").absolutePath);val x=wav.samples;val fs=wav.sampleRate
    val a=AudioAnalyzer.analyze(x,fs);val p=DecisionAgent.decide(a.snrDb,a.stationaryNoise,a.speechActivityRatio).params
    val fp=FrameParams.create(x.size,fs);val pre=PreEmphasis.apply(x,p.preEmphCoeff)
    var cached:FloatArray?=null
    val multiples=if(System.getenv("SIGNALCHAIN_NOMINAL_4X_ONLY")=="1") listOf(1,4) else (1..4).toList()
    for(mult in multiples) {
     val speech=Vad.detect(pre,fp,p.vadHangoverFrames*mult).isSpeech
     val dir=File(base,"kotlin_$mult").also{it.mkdirs()};File(dir,name+".vad").writeText(speech.joinToString(""){if(it) "1" else "0"})
     val y:FloatArray
     if(mult>1 && p.vadHangoverFrames==0) { y=cached!! } else {
      val noise=NoisePsd.estimate(pre,fp,speech)
      val spectral=SpectralSubtraction.apply(pre,fp,noise,x.size,p.alpha,p.beta,p.gainSmooth,p.useSpectralSubtraction)
      val de=PreEmphasis.deEmphasize(spectral,p.preEmphCoeff)
      val eq=Eq.applyAligned(de,fs,if(p.useEq)p.eqGain else 0.0)
      val comp=Compressor.compress(eq,fs,p.compressionRatio,p.compressionMakeupDb)
      var n=Compressor.normalizeAndLimit(comp,x)
      if(p.useMlPostfilter) {val ml=OnnxDenoiser(bytes).use{it.applyBounded(n.audio,fs)};n=Compressor.normalizeAndLimit(ml,x)}
      y=n.audio
      if(mult==1) {
       val prod=PipelineCore.process(x,fs,{OnnxDenoiser(bytes)});assertNull(prod.warning)
       assertEquals("evaluation matches production $name",0.0,y.indices.maxOf{abs(y[it]-prod.enhancedSamples[it]).toDouble()},1e-7)
       cached=y
      }
     }
     WavWriter.write(File(dir,name).absolutePath,y,fs)
    }
    println("HANGOVER $ds ${idx+1}/40 $name nominal=${p.vadHangoverFrames}")
   }
  }
 }
}

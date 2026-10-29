package com.signalchain.app.pipeline

import com.signalchain.app.agent.*
import com.signalchain.app.audio.*
import com.signalchain.app.dsp.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.*

/** Evaluation-only factorial ablation; production DSP remains unchanged. */
class ClassicalStagesAblationTest {
 private fun ablate(x:FloatArray,fp:FrameParams,noise:FloatArray,p:AgentParams,subtract:Boolean,wiener:Boolean):FloatArray {
  if(!subtract && !wiener) return x.copyOf()
  val n=x.size;val pad=fp.frameLen;val frames=ceil((n+pad).toDouble()/fp.hopLen).toInt()+1
  val out=DoubleArray(n+2*pad+fp.hopLen+fp.fftSize);val weight=DoubleArray(out.size);val prev=DoubleArray(noise.size){1.0}
  for(i in 0 until frames){
   val idx=i*fp.hopLen
   val a=DoubleArray(fp.fftSize){k->val source=idx+k-pad;if(k<fp.frameLen && source in 0 until n) x[source]*fp.win[k] else 0.0}
   val spectrum=Fft.rfft(a)
   val filtered=Array(spectrum.size){k->
    val power=spectrum[k].re*spectrum[k].re+spectrum[k].im*spectrum[k].im
    val clean=if(subtract) max(power-p.alpha*noise[k],p.beta*power) else power
    val post=max(power/(noise[k]+1e-10)-1,0.0)
    val gain=if(wiener) max(p.gainSmooth*prev[k]+(1-p.gainSmooth)*post/(post+1),.25) else 1.0
    prev[k]=gain;val mag=sqrt(clean)*gain;val phase=atan2(spectrum[k].im,spectrum[k].re)
    Complex(mag*cos(phase),mag*sin(phase))
   }
   val z=Fft.irfft(filtered,fp.fftSize)
   for(k in 0 until fp.frameLen){val w=fp.win[k];out[idx+k]+=z[k]*w;weight[idx+k]+=w*w}
  }
  return FloatArray(n){(out[pad+it]/max(weight[pad+it],1e-6)).toFloat()}
 }
 @Test fun exportClassicalFactorial(){
  val root=File(System.getenv("SIGNALCHAIN_ROOT") ?: return);val data=File(root,"ablation_testset_padded")
  val records=File(data,"metadata.csv").readLines().drop(1).map{it.split(',')}
  val modes=File(root,"ablation_output/stftfix_padded/kotlin/kotlin_modes.csv").readLines().drop(1).map{it.split(',')}
  var count=0
  for(i in records.indices){
   if(modes[i][3]!="Classical DSP")continue
   val filename=records[i][5].replace('\\','/').substringAfterLast('/');val raw=WavLoader.load(File(data,"noisy/$filename").absolutePath);val x=raw.samples;val fs=raw.sampleRate
   val a=AudioAnalyzer.analyze(x,fs);val decision=DecisionAgent.decide(a.snrDb,a.stationaryNoise,a.speechActivityRatio);assertEquals("Classical DSP",decision.mode);val p=decision.params
   val fp=FrameParams.create(x.size,fs);val pre=PreEmphasis.apply(x,p.preEmphCoeff);val vad=Vad.detect(pre,fp,p.vadHangoverFrames);val noise=NoisePsd.estimate(pre,fp,vad.isSpeech)
   val prod=PipelineCore.process(x,fs,null);assertNull(prod.warning)
   for((label,sub,wiener) in listOf(Triple("subtraction_only",true,false),Triple("wiener_only",false,true),Triple("both",true,true),Triple("neither",false,false))){
    val spec=ablate(pre,fp,noise,p,sub,wiener);val de=PreEmphasis.deEmphasize(spec,p.preEmphCoeff);val eq=Eq.applyAligned(de,fs,if(p.useEq)p.eqGain else 0.0)
    val comp=Compressor.compress(eq,fs,p.compressionRatio,p.compressionMakeupDb);val result=Compressor.normalizeAndLimit(comp,x).audio
    if(label=="both")assertArrayEquals("both must match production $filename",prod.enhancedSamples,result,1e-7f)
    val dest=File(root,"ablation_output/classical_ablation/$label").also{it.mkdirs()};WavWriter.write(File(dest,filename).absolutePath,result,fs)
   }
   count++;println("CLASSICAL_ABLATION $count $filename")
  }
  assertEquals(12,count)
 }
}

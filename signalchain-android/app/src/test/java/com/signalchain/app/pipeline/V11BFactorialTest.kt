package com.signalchain.app.pipeline

import com.signalchain.app.agent.*
import com.signalchain.app.audio.*
import com.signalchain.app.dsp.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/** Evaluation-only factorial ablation; production DSP remains unchanged. */
class V11BFactorialTest {
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
    val clean=if(subtract) max(power-min(p.alpha,SpectralSubtraction.MAX_OVER_SUBTRACTION_FACTOR)*noise[k],max(p.beta,SpectralSubtraction.MIN_SPECTRAL_SUBTRACTION_GAIN.pow(2))*power) else power
    val post=max(power/(noise[k]+1e-10)-1,0.0)
    val gain=if(wiener) max(p.gainSmooth*prev[k]+(1-p.gainSmooth)*post/(post+1),SpectralSubtraction.MIN_WIENER_GAIN) else 1.0
    prev[k]=gain;val mag=sqrt(clean)*gain;val phase=atan2(spectrum[k].im,spectrum[k].re)
    Complex(mag*cos(phase),mag*sin(phase))
   }
   val z=Fft.irfft(filtered,fp.fftSize)
   for(k in 0 until fp.frameLen){val w=fp.win[k];out[idx+k]+=z[k]*w;weight[idx+k]+=w*w}
  }
  return FloatArray(n){(out[pad+it]/max(weight[pad+it],1e-6)).toFloat()}
 }
 private fun floatWav(file:File,x:FloatArray,rate:Int){
  file.parentFile!!.mkdirs();val b=ByteBuffer.allocate(44+x.size*4).order(ByteOrder.LITTLE_ENDIAN)
  fun tag(s:String){b.put(s.toByteArray(Charsets.US_ASCII))};tag("RIFF");b.putInt(36+x.size*4);tag("WAVE");tag("fmt ");b.putInt(16);b.putShort(3);b.putShort(1);b.putInt(rate);b.putInt(rate*4);b.putShort(4);b.putShort(32);tag("data");b.putInt(x.size*4);x.forEach{b.putFloat(it)};file.writeBytes(b.array())
 }
 @Test fun runFactorialAndOracle(){
  val root=File(System.getenv("SIGNALCHAIN_ROOT") ?: return)
  for(ds in listOf("original","padded")){
   val data=File(root,if(ds=="original")"ablation_testset" else "ablation_testset_padded")
   val records=File(data,"metadata.csv").readLines().drop(1).map{it.split(',')}
   val modes=File(root,"ablation_output/stftfix_$ds/kotlin/kotlin_modes.csv").readLines().drop(1).map{it.split(',')};var count=0
   for(i in records.indices){
    if(modes[i][3]!="Classical DSP")continue
    val filename=records[i][5].replace('\\','/').substringAfterLast('/');val raw=WavLoader.load(File(data,"noisy/$filename").absolutePath);val x=raw.samples;val fs=raw.sampleRate
    val a=AudioAnalyzer.analyze(x,fs);val decision=DecisionAgent.decide(a.snrDb,a.stationaryNoise,a.speechActivityRatio);assertEquals("Classical DSP",decision.mode);val p=decision.params
    val fp=FrameParams.create(x.size,fs);val pre=PreEmphasis.apply(x,p.preEmphCoeff);val vad=Vad.detect(pre,fp,p.vadHangoverFrames);val noise=NoisePsd.estimate(pre,fp,vad.isSpeech)
    val psdDir=File(root,"ablation_output/v11b_factorial/$ds/psd").also{it.mkdirs()};File(psdDir,File(data,"noisy/$filename").name+".csv").writeText(noise.joinToString("\n"))
    val prod=PipelineCore.process(x,fs,null);assertNull(prod.warning)
    fun down(input:FloatArray):FloatArray {val de=PreEmphasis.deEmphasize(input,p.preEmphCoeff);val eq=Eq.applyAligned(de,fs,if(p.useEq)p.eqGain else 0.0);val comp=Compressor.compress(eq,fs,p.compressionRatio,p.compressionMakeupDb);return Compressor.normalizeAndLimit(comp,x).audio}
    fun export(label:String,y:FloatArray){floatWav(File(root,"ablation_output/v11b_factorial/$ds/$label/$filename"),y,fs)}
    for((label,sub,wiener) in listOf(Triple("off",false,false),Triple("wiener",false,true),Triple("subtraction",true,false),Triple("both",true,true))){
     val spec=ablate(pre,fp,noise,p,sub,wiener);val off=PreEmphasis.deEmphasize(spec,p.preEmphCoeff);val on=down(spec)
     if(label=="off"){
      assertArrayEquals("spectral/downstream bypass identity $filename",x,off,1e-6f)
      val pcm=File(root,"ablation_output/v11b_factorial/$ds/off_off_pcm/$filename");pcm.parentFile!!.mkdirs();WavWriter.write(pcm.absolutePath,off,fs)
     }
     if(label=="both")assertArrayEquals("all-on production match $filename",prod.enhancedSamples,on,1e-7f)
     export(label+"_off",off);export(label+"_on",on)
    }
    if(ds=="original"){
     val file=File(root,"ablation_output/v11b_oracle_psd/$filename.csv");val truth=file.readLines().map{it.toFloat()}.toFloatArray();assertEquals(noise.size,truth.size)
     for((label,sub) in listOf("oracle_wiener" to false,"oracle_both" to true)){
      val spec=ablate(pre,fp,truth,p,sub,true);export(label+"_on",down(spec));export(label+"_off",PreEmphasis.deEmphasize(spec,p.preEmphCoeff))
     }
    }
    count++;println("V11B_FACTORIAL $ds $count $filename")
   }
   assertEquals(if(ds=="original")14 else 12,count)
  }
 }
}

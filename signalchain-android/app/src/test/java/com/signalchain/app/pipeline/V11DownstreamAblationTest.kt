package com.signalchain.app.pipeline
import com.signalchain.app.agent.*
import com.signalchain.app.audio.*
import com.signalchain.app.dsp.*
import com.signalchain.app.ml.OnnxDenoiser
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*
import org.junit.experimental.categories.Category
/** Evaluation-only downstream ablation. Saves float WAVs to avoid PCM clipping. */
@Category(EvaluationTest::class)
class V11DownstreamAblationTest {
 data class Flags(val eq:Boolean,val dynamics:Boolean,val limiter:Boolean,val norm:Boolean,val ml:Boolean)
 private fun limitNorm(x:FloatArray,raw:FloatArray,norm:Boolean,limit:Boolean):FloatArray {
  var y=x.copyOf()
  if(norm){val peak=y.maxOfOrNull{abs(it)}?.toDouble()?:0.0;y=FloatArray(y.size){(y[it]/(peak+1e-10)*.99).toFloat()}}
  if(limit)for(i in y.indices)y[i]=(y[i]/max(1.0,abs(y[i].toDouble())/.98)).toFloat()
  if(norm){val orig=sqrt(SignalEnergy.meanSquare(raw));val cur=sqrt(SignalEnergy.meanSquare(y));if(cur>0){val g=min(orig/cur,10.0);for(i in y.indices)y[i]=(y[i]*g).toFloat();val peak=y.maxOfOrNull{abs(it)}?.toDouble()?:0.0;for(i in y.indices)y[i]=(y[i]/(peak+1e-10)*.99).toFloat()}}
  return y
 }
 private fun floatWav(file:File,x:FloatArray,rate:Int){
  file.parentFile!!.mkdirs();val b=ByteBuffer.allocate(44+x.size*4).order(ByteOrder.LITTLE_ENDIAN)
  fun tag(s:String){b.put(s.toByteArray(Charsets.US_ASCII))};tag("RIFF");b.putInt(36+x.size*4);tag("WAVE");tag("fmt ");b.putInt(16);b.putShort(3);b.putShort(1);b.putInt(rate);b.putInt(rate*4);b.putShort(4);b.putShort(32);tag("data");b.putInt(x.size*4);x.forEach{b.putFloat(it)};file.writeBytes(b.array())
 }
 @Test fun exportPaddedDownstream(){
  val rootValue=System.getenv("SIGNALCHAIN_ROOT")
        assumeTrue("Requires SIGNALCHAIN_ROOT and local evaluation data", !rootValue.isNullOrBlank())
        val root=File(rootValue!!);val data=File(root,"ablation_testset_padded");assumeTrue("Evaluation metadata is absent: $data",File(data,"metadata.csv").isFile)
  val records=File(data,"metadata.csv").readLines().drop(1).map{it.split(',')}
  val modes=File(root,"ablation_output/stftfix_padded/kotlin/kotlin_modes.csv").readLines().drop(1).map{it.split(',')}
  val bytes=javaClass.classLoader!!.getResourceAsStream("models/spectral_mask_denoiser.onnx")!!.use{it.readBytes()}
  for(i in records.indices){
   val filename=records[i][5].replace('\\','/').substringAfterLast('/');val wav=WavLoader.load(File(data,"noisy/$filename").absolutePath);val x=wav.samples;val sr=wav.sampleRate
   val a=AudioAnalyzer.analyze(x,sr);val d=DecisionAgent.decide(a.snrDb,a.stationaryNoise,a.speechActivityRatio);val p=d.params;assertEquals(modes[i][3],d.mode)
   var spectral:FloatArray?=null;val prod=PipelineCore.process(x,sr,{OnnxDenoiser(bytes)},onStage={k,v->if(k=="03_specsub_deemph")spectral=v.copyOf()});assertNull(prod.warning)
   val start=requireNotNull(spectral);val all=Flags(p.useEq,p.compressionRatio>1||p.compressionMakeupDb!=0.0,true,true,p.useMlPostfilter);val none=Flags(false,false,false,false,false)
   val variants=linkedMapOf("production" to all,"off_eq" to all.copy(eq=false),"off_dynamics" to all.copy(dynamics=false),"off_limiter" to all.copy(limiter=false),"off_normalization" to all.copy(norm=false),"off_onnx" to all.copy(ml=false),"all_off" to none,"only_eq" to none.copy(eq=all.eq),"only_dynamics" to none.copy(dynamics=all.dynamics),"only_limiter" to none.copy(limiter=true),"only_normalization" to none.copy(norm=true),"only_onnx" to none.copy(ml=all.ml))
   var model:OnnxDenoiser?=null
   try {
    for((name,flags) in variants){
     var y=start.copyOf();if(flags.eq)y=Eq.applyAligned(y,sr,p.eqGain)
     if(flags.dynamics)y=Compressor.compress(y,sr,p.compressionRatio,p.compressionMakeupDb)
     y=limitNorm(y,x,flags.norm,flags.limiter)
     if(flags.ml){if(model==null)model=OnnxDenoiser(bytes);y=model.applyBounded(y,sr);y=limitNorm(y,x,flags.norm,flags.limiter)}
     if(name=="production")assertArrayEquals("production match $filename",prod.enhancedSamples,y,1e-7f)
     floatWav(File(root,"ablation_output/v11_downstream/$name/$filename"),y,sr)
    }
   } finally {model?.close()}
   println("V11_DOWNSTREAM ${i+1}/${records.size} ${d.mode} $filename")
  }
 }
}

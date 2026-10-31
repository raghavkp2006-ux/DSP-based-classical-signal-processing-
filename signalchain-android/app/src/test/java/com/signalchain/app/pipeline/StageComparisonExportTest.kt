package com.signalchain.app.pipeline
import com.signalchain.app.audio.WavLoader
import com.signalchain.app.ml.OnnxDenoiser
import org.junit.Assume.assumeTrue
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.experimental.categories.Category
@Category(EvaluationTest::class)
class StageComparisonExportTest {
 @Test fun exportClassicalAndLightStages(){
  val env=System.getenv("SIGNALCHAIN_ABLATION_METADATA"); assumeTrue("Requires SIGNALCHAIN_ABLATION_METADATA and local evaluation data", !env.isNullOrBlank()); assumeTrue("Metadata file is absent",File(env!!).isFile)
  val meta=File(env!!);val root=meta.parentFile.parentFile!!
  val rows=meta.readLines().drop(1).map{it.split(',')};val modes=File(root,"ablation_output/kotlin/kotlin_modes.csv").readLines().drop(1).map{it.split(',')}
  val chosen=modes.indices.filter { modes[it][3]=="Classical DSP" }.take(3)+modes.indices.filter { modes[it][0]=="holdout_00_7976-110124-0013" && modes[it][1]=="bursty" && modes[it][2]=="15" }
  assertEquals(4,chosen.size)
  val bytes=javaClass.classLoader!!.getResourceAsStream("models/spectral_mask_denoiser.onnx")!!.use{it.readBytes()}
  for (i in chosen){
   val name=rows[i][5].replace('\\','/').substringAfterLast('/');val raw=WavLoader.load(File(meta.parentFile,"noisy/$name").absolutePath)
   val dir=File(root,"ablation_output/stage_compare/${name.removeSuffix(".wav")}").also{it.mkdirs()}
   val result=PipelineCore.processForEvaluation(raw.samples,raw.sampleRate,{OnnxDenoiser(bytes)},true,onStage={label,x->
    val b=ByteBuffer.allocate(44+x.size*4).order(ByteOrder.LITTLE_ENDIAN)
    fun tag(t:String){b.put(t.toByteArray(Charsets.US_ASCII))};tag("RIFF");b.putInt(36+x.size*4);tag("WAVE");tag("fmt ");b.putInt(16);b.putShort(3);b.putShort(1);b.putInt(raw.sampleRate);b.putInt(raw.sampleRate*4);b.putShort(4);b.putShort(32);tag("data");b.putInt(x.size*4);x.forEach{b.putFloat(it)};File(dir,"kotlin_$label.wav").writeBytes(b.array())
   })
   assertEquals(modes[i][3],result.mode);assertNull(result.warning);println("STAGE_COMPARE file=$name mode=${result.mode}")
  }
 }
}

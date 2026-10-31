package com.signalchain.app.pipeline
import org.junit.experimental.categories.Category
import com.signalchain.app.audio.*
import com.signalchain.app.ml.OnnxDenoiser
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
@Category(EvaluationTest::class)
class CleanReferenceExportTest {
 @Test fun exportClean() {
  val rootValue=System.getenv("SIGNALCHAIN_ROOT")
        assumeTrue("Requires SIGNALCHAIN_ROOT and local evaluation data", !rootValue.isNullOrBlank())
        val root=File(rootValue!!)
  val bytes=javaClass.classLoader!!.getResourceAsStream("models/spectral_mask_denoiser.onnx")!!.use{it.readBytes()}
  for (ds in listOf("original","padded")) {
   val data=File(root,if(ds=="original")"ablation_testset/clean" else "ablation_testset_padded/clean")
   assumeTrue("Clean evaluation data is absent: $data",data.isDirectory)
   val out=File(root,"ablation_output/postfix_$ds/clean_kotlin_aligned").also{it.mkdirs()}
   for(f in data.listFiles()!!.filter{it.extension=="wav"}.sortedBy{it.name}) {
    val x=WavLoader.load(f.absolutePath);val y=PipelineCore.process(x.samples,x.sampleRate,{OnnxDenoiser(bytes)})
    check(y.warning==null);WavWriter.write(File(out,f.name).absolutePath,y.enhancedSamples,x.sampleRate)
    println("CLEAN ${ds} ${f.name} ${y.mode}")
   }
  }
 }
}

package com.signalchain.app.pipeline
import com.signalchain.app.audio.*
import com.signalchain.app.ml.OnnxDenoiser
import org.junit.Test
import java.io.File
class CleanReferenceExportTest {
 @Test fun exportClean() {
  val root=File(System.getenv("SIGNALCHAIN_ROOT") ?: return)
  val bytes=javaClass.classLoader!!.getResourceAsStream("models/spectral_mask_denoiser.onnx")!!.use{it.readBytes()}
  for (ds in listOf("original","padded")) {
   val data=File(root,if(ds=="original")"ablation_testset/clean" else "ablation_testset_padded/clean")
   val out=File(root,"ablation_output/postfix_$ds/clean_kotlin_aligned").also{it.mkdirs()}
   for(f in data.listFiles()!!.filter{it.extension=="wav"}.sortedBy{it.name}) {
    val x=WavLoader.load(f.absolutePath);val y=PipelineCore.process(x.samples,x.sampleRate,{OnnxDenoiser(bytes)})
    check(y.warning==null);WavWriter.write(File(out,f.name).absolutePath,y.enhancedSamples,x.sampleRate)
    println("CLEAN ${ds} ${f.name} ${y.mode}")
   }
  }
 }
}

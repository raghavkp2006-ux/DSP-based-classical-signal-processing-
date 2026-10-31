package com.signalchain.app.pipeline
import org.junit.experimental.categories.Category
import com.signalchain.app.agent.*
import com.signalchain.app.audio.*
import com.signalchain.app.dsp.*
import com.signalchain.app.ml.OnnxDenoiser
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
@Category(EvaluationTest::class)
class V11BCounterfactualTest {
 @Test fun exportLightAndNoHum(){
  val rootValue=System.getenv("SIGNALCHAIN_ROOT")
        assumeTrue("Requires SIGNALCHAIN_ROOT and local evaluation data", !rootValue.isNullOrBlank())
        val root=File(rootValue!!);val bytes=javaClass.classLoader!!.getResourceAsStream("models/spectral_mask_denoiser.onnx")!!.use{it.readBytes()}
  val scales=File(root,"ablation_output/v11b_hum_scale.csv").readLines().drop(1).map{it.split(',')}.associate{(it[0]+"/"+it[1]) to it[2].toFloat()}
  for(ds in listOf("original","padded")){
   val data=File(root,if(ds=="original")"ablation_testset" else "ablation_testset_padded");assumeTrue("Evaluation metadata is absent: $data",File(data,"metadata.csv").isFile);val records=File(data,"metadata.csv").readLines().drop(1).map{it.split(',')};val modes=File(root,"ablation_output/stftfix_$ds/kotlin/kotlin_modes.csv").readLines().drop(1).map{it.split(',')}
   var lightCount=0;var humCount=0
   for(i in records.indices){
    val row=records[i];val filename=row[5].replace('\\','/').substringAfterLast('/');val raw=WavLoader.load(File(data,"noisy/$filename").absolutePath);val x=raw.samples;val fs=raw.sampleRate
    if(row[3].toInt()>=10){
     val pre=PreEmphasis.apply(x);val de=PreEmphasis.deEmphasize(pre);val y=Compressor.normalizeAndLimit(de,x).audio
     val out=File(root,"ablation_output/v11b_counterfactual/$ds/light/$filename");out.parentFile!!.mkdirs();WavWriter.write(out.absolutePath,y,fs);lightCount++
    }
    if(row[2]!="brown_hum"||modes[i][3]!="Full adaptive")continue
    val a=AudioAnalyzer.analyze(x,fs);val decision=DecisionAgent.decide(a.snrDb,a.stationaryNoise,a.speechActivityRatio);assertEquals("Full adaptive",decision.mode);val p=decision.params
    val cleanName=row[4].replace('\\','/').substringAfterLast('/');val clean=WavLoader.load(File(data,"clean/$cleanName").absolutePath);val scale=requireNotNull(scales["$ds/$filename"]);val c=FloatArray(clean.samples.size){clean.samples[it]*scale}
    val fp=FrameParams.create(c.size,fs);val pre=PreEmphasis.apply(c,p.preEmphCoeff);val vad=Vad.detect(pre,fp,p.vadHangoverFrames);val noise=NoisePsd.estimate(pre,fp,vad.isSpeech)
    val spectral=SpectralSubtraction.apply(pre,fp,noise,c.size,p.alpha,p.beta,p.gainSmooth,p.useSpectralSubtraction);val de=PreEmphasis.deEmphasize(spectral,p.preEmphCoeff)
    val eq=Eq.applyAligned(de,fs,if(p.useEq)p.eqGain else 0.0);val comp=Compressor.compress(eq,fs,p.compressionRatio,p.compressionMakeupDb);var y=Compressor.normalizeAndLimit(comp,c).audio
    if(p.useMlPostfilter){val ml=OnnxDenoiser(bytes).use{it.applyBounded(y,fs)};y=Compressor.normalizeAndLimit(ml,c).audio}
    val out=File(root,"ablation_output/v11b_counterfactual/$ds/no_brown/$filename");out.parentFile!!.mkdirs();WavWriter.write(out.absolutePath,y,fs);humCount++
   }
   assertEquals(16,lightCount);assertEquals(if(ds=="original")5 else 3,humCount);println("V11B_COUNTERFACTUAL $ds light=$lightCount no_brown=$humCount")
  }
 }
}

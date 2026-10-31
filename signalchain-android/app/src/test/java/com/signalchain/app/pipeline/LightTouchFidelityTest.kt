package com.signalchain.app.pipeline
import org.junit.experimental.categories.Category
import com.signalchain.app.audio.*
import com.signalchain.app.dsp.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.*

@Category(EvaluationTest::class)
class LightTouchFidelityTest {
    private fun measure(reference:FloatArray,output:FloatArray):String {
        require(reference.size==output.size)
        var xx=0.0;var xy=0.0
        for(i in reference.indices){xx+=reference[i].toDouble()*reference[i];xy+=reference[i].toDouble()*output[i]}
        val scale=xy/xx;var error=0.0;var maxError=0.0
        for(i in reference.indices){val diff=output[i]-scale*reference[i];error+=diff*diff;maxError=max(maxError,abs(diff))}
        val snr=10*log10(scale*scale*xx/error)
        return "gain=$scale snr_db=$snr residual_db=${-snr} max_abs_deviation=$maxError"
    }
    @Test fun lightTouchStageFidelity() {
        val fs=16000
        val x=FloatArray(fs*2){i->val gate=if(i in 3200 until 24000) 1.0 else 0.0;(0.018*sin(2*Math.PI*73*i/fs)+0.16*sin(2*Math.PI*223*i/fs)*gate).toFloat()}
        val core=PipelineCore.process(x,fs,null)
        assertEquals("Light touch",core.mode)
        val fp=FrameParams.create(x.size,fs);val pre=PreEmphasis.apply(x);val vad=Vad.detect(pre,fp);val noise=NoisePsd.estimate(pre,fp,vad.isSpeech)
        val reconstructed=SpectralSubtraction.apply(pre,fp,noise,x.size,1.0,1.0,.7,false)
        val de=PreEmphasis.deEmphasize(reconstructed);val eq=Eq.applyAligned(de,fs,0.0);val comp=Compressor.compress(eq,fs,1.0,0.0);val normal=Compressor.normalizeAndLimit(comp,x).audio
        println("FIDELITY pre_de_only ${measure(x,PreEmphasis.deEmphasize(pre))}")
        println("FIDELITY stft_no_subtraction_vs_pre ${measure(pre,reconstructed)}")
        for ((name,y) in listOf("deemphasis" to de,"eq_off" to eq,"compress_off" to comp,"normalize" to normal,"core" to core.enhancedSamples)) println("FIDELITY $name ${measure(x,y)}")
        assertArrayEquals(normal,core.enhancedSamples,0f)
        val file=File("build/light_fidelity/synthetic.wav");file.parentFile!!.mkdirs();WavWriter.write(file.absolutePath,normal,fs)
        val bytes=file.readBytes();val bb=java.nio.ByteBuffer.wrap(bytes,44,bytes.size-44).order(java.nio.ByteOrder.LITTLE_ENDIAN);val pcm=FloatArray(x.size){bb.short/32768f}
        println("FIDELITY pcm16 ${measure(x,pcm)}")
        val env=System.getenv("SIGNALCHAIN_ABLATION_METADATA")
        assumeTrue("Requires SIGNALCHAIN_ABLATION_METADATA and local evaluation data", !env.isNullOrBlank())
        assumeTrue("Metadata file is absent", File(env!!).isFile)
        val meta=File(env);val root=meta.parentFile.parentFile!!;val rows=meta.readLines().drop(1)
        val modes=File(root,"ablation_output/kotlin/kotlin_modes.csv").readLines().drop(1).map{it.split(',')}
        val out=File(root,"ablation_output/light_touch_candidate").also{it.mkdirs()};var count=0
        for((index,line) in rows.withIndex()) {
            if(modes[index][3]!="Light touch") continue
            val name=line.split(',')[5].replace('\\','/').substringAfterLast('/')
            val raw=WavLoader.load(File(meta.parentFile,"noisy/$name").absolutePath)
            val r=PipelineCore.process(raw.samples,raw.sampleRate,null);assertEquals("Light touch",r.mode)
            val candidate=r.enhancedSamples
            WavWriter.write(File(out,name).absolutePath,candidate,raw.sampleRate)
            println("FIDELITY_FILE $name current ${measure(raw.samples,r.enhancedSamples)} candidate ${measure(raw.samples,candidate)}")
            count++
        }
        assertEquals(8,count)
    }
}

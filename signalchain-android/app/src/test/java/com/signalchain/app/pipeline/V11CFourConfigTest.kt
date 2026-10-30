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

/** Test-only paired C0-C3 exporter; production continues to select C3 by default. */
class V11CFourConfigTest {
    private fun writeFloatWav(file: File, x: FloatArray, rate: Int) {
        file.parentFile!!.mkdirs()
        val b=ByteBuffer.allocate(44+x.size*4).order(ByteOrder.LITTLE_ENDIAN)
        fun tag(s:String){b.put(s.toByteArray(Charsets.US_ASCII))}
        tag("RIFF");b.putInt(36+x.size*4);tag("WAVE");tag("fmt ");b.putInt(16);b.putShort(3);b.putShort(1)
        b.putInt(rate);b.putInt(rate*4);b.putShort(4);b.putShort(32);tag("data");b.putInt(x.size*4);x.forEach{b.putFloat(it)}
        file.writeBytes(b.array())
    }

    private fun classical(x:FloatArray,fs:Int,legacy:Boolean,newLimits:Boolean):FloatArray {
        val a=AudioAnalyzer.analyze(x,fs)
        val d=DecisionAgent.decide(a.snrDb,a.stationaryNoise,a.speechActivityRatio)
        require(d.mode=="Classical DSP")
        val p=d.params;val fp=FrameParams.create(x.size,fs);val pre=PreEmphasis.apply(x,p.preEmphCoeff)
        val vad=Vad.detect(pre,fp,p.vadHangoverFrames)
        val noise=NoisePsd.estimate(pre,fp,vad.isSpeech,legacy)
        val spectral=SpectralSubtraction.apply(pre,fp,noise,x.size,p.alpha,p.beta,p.gainSmooth,p.useSpectralSubtraction,useNewLimits=newLimits)
        val de=PreEmphasis.deEmphasize(spectral,p.preEmphCoeff)
        val eq=Eq.applyAligned(de,fs,if(p.useEq)p.eqGain else 0.0)
        val comp=Compressor.compress(eq,fs,p.compressionRatio,p.compressionMakeupDb)
        return Compressor.normalizeAndLimit(comp,x).audio
    }

    @Test fun exportPairedConfigurations() {
        val root=File(System.getenv("SIGNALCHAIN_ROOT") ?: return)
        for(ds in listOf("original","padded")) {
            val data=File(root,if(ds=="original")"ablation_testset" else "ablation_testset_padded")
            val records=File(data,"metadata.csv").readLines().drop(1).map{it.split(',')}
            val modes=File(root,"ablation_output/stftfix_$ds/kotlin/kotlin_modes.csv").readLines().drop(1).map{it.split(',')}
            var count=0
            for(i in records.indices) {
                if(modes[i][3]!="Classical DSP")continue
                val name=records[i][5].replace('\\','/').substringAfterLast('/')
                val raw=WavLoader.load(File(data,"noisy/$name").absolutePath);val x=raw.samples;val fs=raw.sampleRate
                val configs=listOf(Triple("C0",true,false),Triple("C1",false,false),Triple("C2",true,true),Triple("C3",false,true))
                for((label,legacy,limits) in configs) {
                    val y=classical(x,fs,legacy,limits)
                    writeFloatWav(File(root,"ablation_output/v11c_configs/$ds/$label/$name"),y,fs)
                    if(label=="C3") {
                        val fp=FrameParams.create(x.size,fs);val pre=PreEmphasis.apply(x)
                        val vad=Vad.detect(pre,fp)
                        val psd=NoisePsd.estimate(pre,fp,vad.isSpeech)
                        val psdFile=File(root,"ablation_output/v11c_configs/$ds/C3_psd/$name.csv").also{it.parentFile!!.mkdirs()}
                        psdFile.writeText(psd.joinToString("\n"))
                        val prod=PipelineCore.process(x,fs,null)
                        assertEquals("C3 production parity $name",0.0,y.indices.maxOf{abs(y[it]-prod.enhancedSamples[it]).toDouble()},1e-7)
                    }
                }
                count++
            }
            assertEquals(if(ds=="original")14 else 12,count)
        }
    }
}

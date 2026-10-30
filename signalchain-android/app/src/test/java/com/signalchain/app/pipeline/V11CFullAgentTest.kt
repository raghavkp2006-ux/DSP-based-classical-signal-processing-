package com.signalchain.app.pipeline

import com.signalchain.app.audio.*
import com.signalchain.app.ml.OnnxDenoiser
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class V11CFullAgentTest {
    private fun writeFloatWav(file:File,x:FloatArray,rate:Int) {
        file.parentFile!!.mkdirs();val b=ByteBuffer.allocate(44+x.size*4).order(ByteOrder.LITTLE_ENDIAN)
        fun tag(s:String){b.put(s.toByteArray(Charsets.US_ASCII))}
        tag("RIFF");b.putInt(36+x.size*4);tag("WAVE");tag("fmt ");b.putInt(16);b.putShort(3);b.putShort(1)
        b.putInt(rate);b.putInt(rate*4);b.putShort(4);b.putShort(32);tag("data");b.putInt(x.size*4);x.forEach{b.putFloat(it)}
        file.writeBytes(b.array())
    }
    @Test fun exportFullAgentForBothSets() {
        val root=File(System.getenv("SIGNALCHAIN_ROOT") ?: return)
        val model=javaClass.classLoader!!.getResourceAsStream("models/spectral_mask_denoiser.onnx")!!.use{it.readBytes()}
        for(ds in listOf("original","padded")) {
            val data=File(root,if(ds=="original")"ablation_testset" else "ablation_testset_padded")
            val records=File(data,"metadata.csv").readLines().drop(1).map{it.split(',')}
            val rows=mutableListOf("file_id,noise_type,snr_db,mode,warning")
            for(line in records) {
                val name=line[5].replace('\\','/').substringAfterLast('/')
                val raw=WavLoader.load(File(data,"noisy/$name").absolutePath)
                val result=PipelineCore.process(raw.samples,raw.sampleRate,{OnnxDenoiser(model)})
                writeFloatWav(File(root,"ablation_output/v11c_full_agent/$ds/$name"),result.enhancedSamples,raw.sampleRate)
                rows.add("${line[0]},${line[2]},${line[3]},${result.mode},${result.warning.orEmpty()}")
                println("V11C_FULL_AGENT $ds ${rows.size-1}/40 $name ${result.mode}")
            }
            File(root,"ablation_output/v11c_full_agent/$ds/modes.csv").also{it.parentFile!!.mkdirs();it.writeText(rows.joinToString("\n"))}
            assertEquals(41,rows.size)
        }
    }
}

package com.signalchain.app.pipeline
import org.junit.experimental.categories.Category
import com.signalchain.app.audio.*
import com.signalchain.app.ml.OnnxDenoiser
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@Category(EvaluationTest::class)
class V11CFullAgentTest {
    private fun writeFloatWav(file:File,x:FloatArray,rate:Int) {
        file.parentFile!!.mkdirs();val b=ByteBuffer.allocate(44+x.size*4).order(ByteOrder.LITTLE_ENDIAN)
        fun tag(s:String){b.put(s.toByteArray(Charsets.US_ASCII))}
        tag("RIFF");b.putInt(36+x.size*4);tag("WAVE");tag("fmt ");b.putInt(16);b.putShort(3);b.putShort(1)
        b.putInt(rate);b.putInt(rate*4);b.putShort(4);b.putShort(32);tag("data");b.putInt(x.size*4);x.forEach{b.putFloat(it)}
        file.writeBytes(b.array())
    }
    @Test fun exportFullAgentForBothSets() {
        val rootValue=System.getenv("SIGNALCHAIN_ROOT")
        assumeTrue("Requires SIGNALCHAIN_ROOT and local evaluation data", !rootValue.isNullOrBlank())
        val root=File(rootValue!!)
        val model=javaClass.classLoader!!.getResourceAsStream("models/spectral_mask_denoiser.onnx")!!.use{it.readBytes()}
        for(ds in listOf("original","padded")) {
            val data=File(root,if(ds=="original")"ablation_testset" else "ablation_testset_padded")
            assumeTrue("Evaluation metadata is absent: $data",File(data,"metadata.csv").isFile)
            val records=File(data,"metadata.csv").readLines().drop(1).map{it.split(',')}
            for(config in listOf("C1","C3")) {
                val rows=mutableListOf("file_id,noise_type,snr_db,mode,warning")
                for(line in records) {
                    val name=line[5].replace('\\','/').substringAfterLast('/')
                    val raw=WavLoader.load(File(data,"noisy/$name").absolutePath)
                    val result=PipelineCore.process(raw.samples,raw.sampleRate,{OnnxDenoiser(model)})
                    writeFloatWav(File(root,"ablation_output/v111_full_agent/$ds/$config/$name"),result.enhancedSamples,raw.sampleRate)
                    rows.add("${line[0]},${line[2]},${line[3]},${result.mode},${result.warning.orEmpty()}")
                    println("V111_FULL_AGENT $ds $config ${rows.size-1}/40 $name ${result.mode}")
                }
                File(root,"ablation_output/v111_full_agent/$ds/$config/modes.csv").also{it.parentFile!!.mkdirs();it.writeText(rows.joinToString("\n"))}
                assertEquals(41,rows.size)
            }
        }
    }
}

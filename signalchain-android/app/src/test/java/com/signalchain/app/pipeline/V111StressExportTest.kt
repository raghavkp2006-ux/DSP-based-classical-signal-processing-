package com.signalchain.app.pipeline
import org.junit.experimental.categories.Category
import com.signalchain.app.dsp.*
import com.signalchain.app.agent.AgentParams
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@Category(EvaluationTest::class)
class V111StressExportTest {
    private fun readFloatWav(file: File): Pair<FloatArray,Int> {
        val b=ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        require(String(ByteArray(4).also{b.get(it)},Charsets.US_ASCII)=="RIFF")
        b.position(24);val rate=b.int
        b.position(12);var dataOffset=-1;var bytes=0
        while(b.remaining()>=8 && dataOffset<0) {
            val tag=String(ByteArray(4).also{b.get(it)},Charsets.US_ASCII);val size=b.int
            if(tag=="data"){dataOffset=b.position();bytes=size}else b.position(b.position()+size+(size and 1))
        }
        require(dataOffset>=0)
        require(bytes%4==0)
        b.position(dataOffset)
        val x=FloatArray(bytes/4){b.float}
        return x to rate
    }
    private fun writeFloatWav(file:File,x:FloatArray,rate:Int) {
        file.parentFile!!.mkdirs();val b=ByteBuffer.allocate(44+x.size*4).order(ByteOrder.LITTLE_ENDIAN)
        fun tag(s:String){b.put(s.toByteArray(Charsets.US_ASCII))}
        tag("RIFF");b.putInt(36+x.size*4);tag("WAVE");tag("fmt ");b.putInt(16);b.putShort(3);b.putShort(1)
        b.putInt(rate);b.putInt(rate*4);b.putShort(4);b.putShort(32);tag("data");b.putInt(x.size*4);x.forEach{b.putFloat(it)}
        file.writeBytes(b.array())
    }
    @Test fun exportEstimatorStressCases() {
        val rootValue=System.getenv("SIGNALCHAIN_ROOT")
        assumeTrue("Requires SIGNALCHAIN_ROOT and local evaluation data", !rootValue.isNullOrBlank())
        val root=File(rootValue!!)
        val input=File(root,"ablation_output/v111_stress/inputs")
        assumeTrue("Generated stress data is absent",File(input,"metadata.csv").isFile)
        val rows=File(input,"metadata.csv").readLines().drop(1).map{it.split(',')}
        val out=File(root,"ablation_output/v111_stress/exports")
        val psd=mutableListOf("name,config,values")
        val params=AgentParams(alpha=1.2,beta=.1,gainSmooth=.7,useMlPostfilter=false,useEq=false,compressionRatio=1.0,compressionMakeupDb=0.0)
        for(row in rows) {
            val name=row[0];val (noisy,sr)=readFloatWav(File(input,row[6]));val fp=FrameParams.create(noisy.size,sr)
            val pre=PreEmphasis.apply(noisy,params.preEmphCoeff);val vad=Vad.detect(pre,fp,params.vadHangoverFrames)
            for((config,legacy) in listOf("legacy" to true,"current" to false)) {
                val estimate=NoisePsd.estimate(pre,fp,vad.isSpeech,legacy)
                psd.add("$name,$config,${estimate.joinToString(";")}")
                val filtered=SpectralSubtraction.apply(pre,fp,estimate,noisy.size,params.alpha,params.beta,params.gainSmooth,true)
                val audio=PreEmphasis.deEmphasize(filtered,params.preEmphCoeff)
                writeFloatWav(File(out,"$config/$name.wav"),audio,sr)
            }
        }
        out.mkdirs();File(out,"psd.csv").writeText(psd.joinToString("\n"))
        assertEquals(24,rows.size)
    }
}

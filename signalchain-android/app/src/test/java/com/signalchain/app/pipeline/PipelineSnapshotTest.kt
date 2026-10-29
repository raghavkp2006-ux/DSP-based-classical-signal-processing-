package com.signalchain.app.pipeline

import com.signalchain.app.agent.*
import com.signalchain.app.dsp.*
import com.signalchain.app.ml.*
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.sin

/** Detects unintended changes in current production pipeline output. */
class PipelineSnapshotTest {
    @Test fun capture() {
        val fs=16000
        for (name in listOf("steady", "noisy", "classical")) {
            val rng=java.util.Random(42)
            val x=FloatArray(fs*2) { i ->
                val gate=if (i in 3200 until 24000) 1.0 else 0.0
                if (name=="classical") (0.035*(sin(2*Math.PI*2113*i/fs)+sin(2*Math.PI*3719*i/fs)) + 0.025*sin(2*Math.PI*997*i/fs)*((i/800)%2) + 0.16*sin(2*Math.PI*223*i/fs)*gate).toFloat()
                else if (name=="steady") (0.018*sin(2*Math.PI*73*i/fs)+0.16*sin(2*Math.PI*223*i/fs)*gate).toFloat()
                else ((sin(2*Math.PI*300*i/fs)*0.3*gate)+rng.nextGaussian()*0.15).toFloat()
            }
            val bytes=javaClass.classLoader!!.getResourceAsStream("models/spectral_mask_denoiser.onnx")!!.use{it.readBytes()}
            val result=PipelineCore.process(x,fs,{OnnxDenoiser(bytes)})
            assertEquals(when(name) { "steady" -> "Light touch"; "classical" -> "Classical DSP"; else -> "Full adaptive" }, result.mode)
            if (name == "classical") assertTrue(result.snrDb >= 5 && result.snrDb <= 15 && result.stationaryNoise)
            assertNull(result.warning)
            val buf=ByteBuffer.allocate(result.enhancedSamples.size*4).order(ByteOrder.LITTLE_ENDIAN)
            result.enhancedSamples.forEach{buf.putFloat(it)}
            val hash=MessageDigest.getInstance("SHA-256").digest(buf.array()).joinToString(""){"%02x".format(it)}
            assertEquals(when(name) { "steady" -> "ac51fecdbf586873b3f08b405ced95c879390d57d607b78a5500a712ac92b03c"; "classical" -> "410b3e02ef9b9d0e55ccb84ef7d45c4b136d2fce31fda1130177d8384be535f6"; else -> "6e7675bdb73fc47d0f9e59d1620405b597e6fba584a03f35562bb039ad953239" }, hash)
            println("SNAPSHOT $name mode=${result.mode} hash=$hash")
        }
    }
}

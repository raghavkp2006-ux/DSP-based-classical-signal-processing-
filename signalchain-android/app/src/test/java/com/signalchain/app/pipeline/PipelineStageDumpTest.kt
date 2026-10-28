package com.signalchain.app.pipeline

import com.signalchain.app.agent.AudioAnalyzer
import com.signalchain.app.agent.DecisionAgent
import com.signalchain.app.audio.WavLoader
import com.signalchain.app.audio.WavWriter
import com.signalchain.app.dsp.*
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.sin

class PipelineStageDumpTest {

    @Test
    fun testStageDumpsOnSyntheticNoisyClip() {
        val sampleRate = 16000
        val durationSec = 2.0
        val n = (sampleRate * durationSec).toInt()

        // Speech component: 440 Hz tone with harmonics
        // Noise component: pseudo-random noise
        val rng = java.util.Random(42)
        val audio = FloatArray(n) { i ->
            val t = i / sampleRate.toDouble()
            val speech = (sin(2 * Math.PI * 300 * t) + 0.5 * sin(2 * Math.PI * 600 * t) + 0.3 * sin(2 * Math.PI * 1200 * t)).toFloat()
            val isSpeechFrame = (i in (n / 4)..(3 * n / 4))
            val speechSig = if (isSpeechFrame) speech * 0.6f else 0.0f
            val noise = (rng.nextGaussian() * 0.15).toFloat()
            (speechSig + noise).coerceIn(-1f, 1f)
        }

        val outDir = File("build/stage_dumps")
        outDir.mkdirs()

        // Dump 00: raw input
        WavWriter.write(File(outDir, "00_input.wav").absolutePath, audio, sampleRate)

        val fp = FrameParams.create(audio.size, sampleRate)
        val a = AudioAnalyzer.analyze(audio, sampleRate)
        val d = DecisionAgent.decide(a.snrDb, a.stationaryNoise, a.speechActivityRatio)

        // Stage 1: Pre-emphasis
        val pre = PreEmphasis.apply(audio, d.params.preEmphCoeff)
        WavWriter.write(File(outDir, "01_preemph.wav").absolutePath, pre, sampleRate)

        // Stage 2: VAD & Noise PSD
        val v = Vad.detect(pre, fp, d.params.vadHangoverFrames)
        val noise = NoisePsd.estimate(pre, fp, v.isSpeech)

        // Stage 3: Spectral Subtraction + De-emphasis
        val sub = SpectralSubtraction.apply(
            pre, fp, noise, audio.size,
            d.params.alpha, d.params.beta, d.params.gainSmooth, d.params.useSpectralSubtraction
        )
        val deemph = PreEmphasis.deEmphasize(sub, d.params.preEmphCoeff)
        WavWriter.write(File(outDir, "03_specsub_deemph.wav").absolutePath, deemph, sampleRate)

        // Stage 4: Voice-band EQ
        val eq = Eq.apply(deemph, sampleRate, if (d.params.useEq) d.params.eqGain else 0.0)
        WavWriter.write(File(outDir, "04_eq.wav").absolutePath, eq, sampleRate)

        // Stage 5: Compression
        val comp = Compressor.compress(eq, sampleRate, d.params.compressionRatio, d.params.compressionMakeupDb)
        WavWriter.write(File(outDir, "05_compressor.wav").absolutePath, comp, sampleRate)

        // Stage 6: Normalize & Limit
        val normalized = Compressor.normalizeAndLimit(comp, audio)
        WavWriter.write(File(outDir, "06_normalized.wav").absolutePath, normalized.audio, sampleRate)

        assertTrue(File(outDir, "06_normalized.wav").exists())
    }

    @Test fun fullAdaptiveCoreDumpsRealOnnxStages() {
        val fs=16000
        val rng=java.util.Random(42)
        val audio=FloatArray(fs*2) { i ->
            val gate=if (i in 3200 until 24000) 1.0 else 0.0
            (sin(2*Math.PI*300*i/fs)*0.3*gate+rng.nextGaussian()*0.15).toFloat()
        }
        val bytes=javaClass.classLoader!!.getResourceAsStream("models/spectral_mask_denoiser.onnx")!!.use { it.readBytes() }
        val out=File("build/stage_dumps/full_adaptive").also { it.mkdirs() }
        val stages=mutableSetOf<String>()
        val result=PipelineCore.process(audio,fs,{com.signalchain.app.ml.OnnxDenoiser(bytes)},onStage={ name, samples ->
            assertTrue("$name finite",samples.all { it.isFinite() })
            stages.add(name)
            val file=File(out,"$name.wav")
            if (name=="08_final") WavWriter.write(file.absolutePath,samples,fs) else writeFloatWav(file,samples,fs)
        })
        org.junit.Assert.assertEquals("Full adaptive",result.mode)
        org.junit.Assert.assertNull(result.warning)
        org.junit.Assert.assertEquals(audio.size,result.enhancedSamples.size)
        assertTrue(result.enhancedSamples.all { it.isFinite() && kotlin.math.abs(it)<=1f })
        assertTrue(stages.containsAll(listOf("00_input","01_preemph","03_specsub_deemph","04_eq","05_compressor","06_normalized","07_ml_postfilter","08_final")))
        println("STAGE_DUMP stages=${stages.size} samples=${audio.size} warning=${result.warning}")
    }

    /** IEEE float WAV for diagnostics only. Production export stays 16-bit PCM. */
    private fun writeFloatWav(file: File, samples: FloatArray, rate: Int) {
        val buf=java.nio.ByteBuffer.allocate(44+samples.size*4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        fun tag(x:String) { buf.put(x.toByteArray(Charsets.US_ASCII)) }
        tag("RIFF"); buf.putInt(36+samples.size*4); tag("WAVE"); tag("fmt "); buf.putInt(16)
        buf.putShort(3); buf.putShort(1); buf.putInt(rate); buf.putInt(rate*4); buf.putShort(4); buf.putShort(32)
        tag("data"); buf.putInt(samples.size*4); samples.forEach { buf.putFloat(it) }; file.writeBytes(buf.array())
    }

    @Test fun floatWriterPreservesOutOfRangeSamples() {
        val samples=floatArrayOf(-2f,-1f,0f,1f,2f)
        val file=File("build/stage_dumps/float_writer_test.wav"); file.parentFile.mkdirs()
        writeFloatWav(file,samples,16000)
        val b=java.nio.ByteBuffer.wrap(file.readBytes()).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        org.junit.Assert.assertEquals(3,b.getShort(20).toInt())
        org.junit.Assert.assertEquals(32,b.getShort(34).toInt())
        for(i in samples.indices) org.junit.Assert.assertEquals(samples[i],b.getFloat(44+i*4),0f)
    }
}

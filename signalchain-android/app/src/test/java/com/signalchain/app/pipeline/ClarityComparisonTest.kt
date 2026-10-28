package com.signalchain.app.pipeline

import com.signalchain.app.agent.*
import com.signalchain.app.audio.*
import com.signalchain.app.dsp.*
import com.signalchain.app.ml.OnnxDenoiser
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/** Opt-in reproduction on a local recording; no private audio is committed. */
class ClarityComparisonTest {
    @Test fun exportBeforeAndAfterWithRealModel() {
        val path = System.getenv("SIGNALCHAIN_CLARITY_WAV")
        assumeTrue(path != null)
        val raw = WavLoader.load(path!!)
        val x = raw.samples
        val fs = raw.sampleRate
        val fp = FrameParams.create(x.size, fs)
        val a = AudioAnalyzer.analyze(x, fs)
        val d = DecisionAgent.decide(a.snrDb, a.stationaryNoise, a.speechActivityRatio)
        println("CLARITY samples=${x.size} rate=$fs analysis=$a mode=${d.mode}")
        val p = d.params
        val pre = PreEmphasis.apply(x, p.preEmphCoeff)
        val v = Vad.detect(pre, fp, p.vadHangoverFrames)
        val noise = NoisePsd.estimate(pre, fp, v.isSpeech)
        val sub = SpectralSubtraction.apply(pre, fp, noise, x.size, p.alpha, p.beta, p.gainSmooth, p.useSpectralSubtraction)
        val de = PreEmphasis.deEmphasize(sub, p.preEmphCoeff)
        val out = File(File(path).parentFile, "comparison").also { it.mkdirs() }
        WavWriter.write(File(out, "01_before_eq.wav").path, de, fs)
        val bytes = javaClass.classLoader!!.getResourceAsStream("models/spectral_mask_denoiser.onnx")!!.use { it.readBytes() }
        for (aligned in listOf(false, true)) {
            val name = if (aligned) "aligned" else "legacy"
            val eq = if (aligned) Eq.applyAligned(de, fs, if (p.useEq) p.eqGain else 0.0) else Eq.apply(de, fs, if (p.useEq) p.eqGain else 0.0)
            WavWriter.write(File(out, "02_${name}_eq.wav").path, eq, fs)
            val comp = Compressor.compress(eq, fs, p.compressionRatio, p.compressionMakeupDb)
            var final = Compressor.normalizeAndLimit(comp, x).audio
            if (p.useMlPostfilter) final = OnnxDenoiser(bytes).use { Compressor.normalizeAndLimit(it.applyBounded(final, fs), x).audio }
            assertEquals(x.size, final.size)
            assertTrue(final.all { it.isFinite() && abs(it) <= 1f })
            WavWriter.write(File(out, "03_${name}_final.wav").path, final, fs)
            println("CLARITY $name samples=${final.size} peak=${final.maxOf { abs(it) }} ML=${p.useMlPostfilter}")
        }
    }
}

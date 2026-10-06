package com.signalchain.app.agent

import com.signalchain.app.dsp.FrameParams
import com.signalchain.app.dsp.PreEmphasis
import com.signalchain.app.dsp.Vad
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt

data class AnalysisResult(
    val snrDb: Double,
    val noiseStationarityCv: Double,
    val stationaryNoise: Boolean,
    val speechActivityRatio: Double
)

object AudioAnalyzer {
    fun analyze(x: FloatArray, fs: Int): AnalysisResult {
        val fp = FrameParams.create(x.size, fs)
        val vad = Vad.detect(PreEmphasis.apply(x), fp)
        val noise = vad.frameEnergy.filterIndexed { i, _ -> !vad.isSpeech[i] }
        val (cv, stationary) = noiseStationarity(noise)

        val energies = FloatArray(fp.numFrames) { i ->
            val start = i * fp.hopLen
            val count = min(fp.frameLen, x.size - start)
            if (count <= 0) 0f else
                (0 until count).sumOf { x[start + it].toDouble() * x[start + it] }.toFloat() / fp.frameLen
        }
        val sorted = energies.sorted()
        val noiseCount = max(2, (0.15 * energies.size).toInt())
        val noiseFloor = sorted.take(noiseCount).average()
        val snr = (10 * log10(max(energies.average() - noiseFloor, 1e-10) / max(noiseFloor, 1e-10)))
            .coerceIn(-20.0, 40.0)

        return AnalysisResult(
            snrDb = round(snr * 100) / 100.0,
            noiseStationarityCv = if (noise.isEmpty()) cv else round(cv * 10000) / 10000.0,
            stationaryNoise = stationary,
            speechActivityRatio = vad.isSpeech.count { it }.toDouble() / fp.numFrames
        )
    }

    /** Empty noise has no measurable stationarity, so it is explicitly non-stationary. */
    internal fun noiseStationarity(noise: List<Float>): Pair<Double, Boolean> {
        if (noise.isEmpty()) return Double.POSITIVE_INFINITY to false
        val mean = noise.average()
        val cv = sqrt(noise.map { (it - mean) * (it - mean) }.average()) / (mean + 1e-12)
        return cv to (cv < 0.55)
    }
}

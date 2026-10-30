package com.signalchain.app.dsp

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max

class NoisePsdMinimumStatisticsTest {
    private fun noise(samples: Int, seed: Long, amplitude: Double): FloatArray {
        val random = Random(seed)
        return FloatArray(samples) { (random.nextGaussian() * amplitude).toFloat() }
    }

    private fun bandMean(psd: FloatArray): Double =
        psd.sliceArray(10 until psd.size - 10).average()

    private fun dbRatio(a: Double, b: Double): Double = 10.0 * ln(a / b) / ln(10.0)

    @Test fun stationaryWhiteNoisePsdWithinThreeDb() {
        val x = noise(16000 * 4, 712L, 0.04)
        val fp = FrameParams.create(x.size, 16000)
        val estimated = bandMean(NoisePsd.estimate(x, fp, BooleanArray(fp.numFrames)))
        val expected = 0.04 * 0.04 * fp.win.sumOf { it * it }
        val errorDb = dbRatio(estimated, expected)
        assertTrue("stationary PSD error=$errorDb dB", abs(errorDb) <= 3.0)
    }

    @Test fun speechLikeBurstDoesNotInflateNoiseEstimate() {
        val sr = 16000
        val cleanNoise = noise(sr * 4, 923L, 0.035)
        val burst = cleanNoise.copyOf()
        val start = sr * 17 / 10
        val end = start + sr / 4
        for (i in start until end) {
            val t = i.toDouble() / sr
            burst[i] += (0.20 * kotlin.math.sin(2.0 * Math.PI * 180.0 * t) +
                0.08 * kotlin.math.sin(2.0 * Math.PI * 360.0 * t)).toFloat()
        }
        val fp = FrameParams.create(burst.size, sr)
        val quietTrack = NoisePsd.estimateMinimumStatisticsTimeline(cleanNoise, fp)
        val burstTrack = NoisePsd.estimateMinimumStatisticsTimeline(burst, fp)
        val first = start / fp.hopLen
        val last = end / fp.hopLen
        val quietMean = quietTrack.slice(first until last).map(::bandMean).average()
        val burstMean = burstTrack.slice(first until last).map(::bandMean).average()
        assertTrue("burst inflated PSD by ${burstMean / quietMean}x", burstMean <= quietMean * 1.10)
    }

    @Test fun stepChangeIsTrackedWithinWindowLength() {
        val sr = 16000
        val stepAt = sr * 2
        val low = noise(stepAt, 1201L, 0.025)
        val high = noise(sr * 3, 1202L, 0.050)
        val x = low + high
        val fp = FrameParams.create(x.size, sr)
        val track = NoisePsd.estimateMinimumStatisticsTimeline(x, fp)
        val windowFrames = (NoisePsd.TRACKING_WINDOW_SECONDS * sr / fp.hopLen).toInt()
        val before = bandMean(track[stepAt / fp.hopLen - 10])
        val afterIndex = minOf(track.lastIndex, stepAt / fp.hopLen + windowFrames + 5)
        val after = bandMean(track[afterIndex])
        val expectedLow = 0.025 * 0.025 * fp.win.sumOf { it * it }
        val expectedHigh = 0.050 * 0.050 * fp.win.sumOf { it * it }
        assertTrue("pre-step PSD error=${dbRatio(before, expectedLow)} dB", abs(dbRatio(before, expectedLow)) <= 3.0)
        assertTrue("post-step PSD error=${dbRatio(after, expectedHigh)} dB", abs(dbRatio(after, expectedHigh)) <= 3.0)
    }
}

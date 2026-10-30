package com.signalchain.app.dsp

import kotlin.math.*

/** Batch minimum-statistics noise PSD tracker, independent of the VAD mask. */
object NoisePsd {
    const val TRACKING_WINDOW_SECONDS = 1.5
    const val PERIODOGRAM_SMOOTHING = 0.80
    const val MIN_STATS_BIAS_COMPENSATION = 2.20
    const val STARTUP_DISCOUNT = 0.75
    const val STARTUP_RAMP_SECONDS = 1.0

    /** Test/evaluation switch. Production callers use the minimum-statistics path by default. */
    @Volatile
    var useLegacyEstimatorForAbTests: Boolean = false

    /** Preserves the prior API; speech flags are ignored unless the legacy A/B switch is enabled. */
    fun estimate(x: FloatArray, fp: FrameParams, speech: BooleanArray): FloatArray =
        estimate(x, fp, speech, useLegacyEstimatorForAbTests)

    /** Explicit A/B entry point. Set legacy=true to reproduce the former VAD-gated estimate. */
    fun estimate(x: FloatArray, fp: FrameParams, speech: BooleanArray, legacy: Boolean): FloatArray {
        if (legacy) return estimateLegacy(x, fp, speech)
        val timeline = estimateMinimumStatisticsTimeline(x, fp)
        return timeline.lastOrNull() ?: FloatArray(fp.fftSize / 2 + 1)
    }

    /** Framewise estimates are exposed for verification of startup and step tracking. */
    internal fun estimateMinimumStatisticsTimeline(x: FloatArray, fp: FrameParams): Array<FloatArray> {
        val frames = fp.numFrames.coerceAtLeast(1)
        val bins = fp.fftSize / 2 + 1
        val smoothed = Array(frames) { DoubleArray(bins) }
        val previous = DoubleArray(bins)
        // FrameParams is fixed at 25 ms analysis frames; recover the configured rate
        // closely enough to express time windows without changing its public shape.
        val sampleRate = (fp.frameLen / 0.025).roundToInt()
        val rampFrames = max(1, (STARTUP_RAMP_SECONDS * sampleRate / fp.hopLen).roundToInt())

        for (frameIndex in 0 until frames) {
            val start = frameIndex * fp.hopLen
            val input = DoubleArray(fp.fftSize) { k ->
                if (k < fp.frameLen) {
                    val source = start + k
                    if (source < x.size) x[source].toDouble() * fp.win[k] else 0.0
                } else 0.0
            }
            val spectrum = Fft.rfft(input)
            for (bin in 0 until bins) {
                val power = spectrum[bin].re * spectrum[bin].re + spectrum[bin].im * spectrum[bin].im
                val value = if (frameIndex == 0) power
                else PERIODOGRAM_SMOOTHING * previous[bin] + (1.0 - PERIODOGRAM_SMOOTHING) * power
                previous[bin] = value
                smoothed[frameIndex][bin] = value
            }
        }

        val windowFrames = max(1, (TRACKING_WINDOW_SECONDS * sampleRate / fp.hopLen).roundToInt())
        val estimates = Array(frames) { DoubleArray(bins) }
        for (bin in 0 until bins) {
            // Monotone queue: the head is the minimum in the active 1.5 s window.
            val indices = IntArray(frames)
            val values = DoubleArray(frames)
            var head = 0
            var tail = 0
            for (frameIndex in 0 until frames) {
                val oldest = frameIndex - windowFrames + 1
                while (head < tail && indices[head] < oldest) head++
                val value = smoothed[frameIndex][bin]
                while (tail > head && values[tail - 1] >= value) tail--
                indices[tail] = frameIndex
                values[tail] = value
                tail++

                // During the first second, discount the short-window minimum so startup
                // speech transients cannot immediately set an aggressive noise floor.
                val observed = frameIndex + 1
                val startupProgress = min(1.0, observed.toDouble() / rampFrames)
                val startupScale = STARTUP_DISCOUNT + (1.0 - STARTUP_DISCOUNT) * startupProgress
                estimates[frameIndex][bin] = values[head] * MIN_STATS_BIAS_COMPENSATION * startupScale
            }
        }
        return Array(frames) { i -> estimates[i].map { it.toFloat() }.toFloatArray() }
    }

    private fun estimateLegacy(x: FloatArray, fp: FrameParams, speech: BooleanArray): FloatArray {
        val power = DoubleArray(fp.fftSize / 2 + 1)
        val ids = (0 until fp.numFrames).filter { it < speech.size && !speech[it] }
            .ifEmpty { 0 until min(10, fp.numFrames) }
        for (frameIndex in ids) {
            val input = DoubleArray(fp.fftSize) { k ->
                if (k < fp.frameLen) x.getOrElse(frameIndex * fp.hopLen + k) { 0f } * fp.win[k] else 0.0
            }
            Fft.rfft(input).forEachIndexed { bin, c -> power[bin] += c.re * c.re + c.im * c.im }
        }
        return FloatArray(power.size) { bin ->
            ((-2..2).sumOf { offset -> power.getOrElse(bin + offset) { 0.0 } } / 5.0 / max(1, ids.count())).toFloat()
        }
    }
}

package com.signalchain.app.dsp

import kotlin.math.*

object Eq {
    private fun fir(n: Int, cut: Double, high: Boolean = false): DoubleArray {
        val m = (n - 1) / 2
        val coeffs = DoubleArray(n) { i ->
            val k = i - m
            val v = if (k == 0) {
                if (high) 1 - 2 * cut else 2 * cut
            } else {
                sin(2 * Math.PI * cut * k) / (Math.PI * k)
            }
            // Hamming window
            v * (0.54 - 0.46 * cos(2 * Math.PI * i / (n - 1)))
        }
        return if (high) {
            DoubleArray(n) { i -> coeffs[i] * if (i == m) 1.0 else -1.0 }
        } else {
            coeffs
        }
    }

    private fun band(n: Int, l: Double, h: Double): DoubleArray {
        val hp = fir(n, h)
        val lp = fir(n, l)
        return DoubleArray(n) { i -> hp[i] - lp[i] }
    }

    /** Optimized FIR filter using direct primitive loop instead of sumOf */
    private fun filter(x: FloatArray, b: DoubleArray): FloatArray {
        val n = x.size
        val m = b.size
        val y = FloatArray(n)
        for (i in 0 until n) {
            var acc = 0.0
            val jMax = min(i, m - 1)
            for (j in 0..jMax) {
                acc += x[i - j].toDouble() * b[j]
            }
            y[i] = acc.toFloat()
        }
        return y
    }

    fun apply(x: FloatArray, fs: Int, gain: Double = 1.0): FloatArray {
        if (gain <= 0) return x.copyOf()
        val ny = fs / 2.0

        // Use shorter filters for mobile performance (31 taps instead of 65)
        val bandTaps = 31
        val hpTaps = 65

        var o = x.copyOf()

        // Band boosts - apply gains to specific frequency ranges
        if (500 / ny < 1) {
            val bandCoeffs = band(bandTaps, 300 / ny, 500 / ny)
            val filtered = filter(o, bandCoeffs)
            for (i in o.indices) {
                o[i] = o[i] + (gain * 0.6 * filtered[i]).toFloat()
            }
        }

        val midHigh = min(2500 / ny, 0.99)
        val midCoeffs = band(bandTaps, 1000 / ny, midHigh)
        val midFiltered = filter(o, midCoeffs)
        for (i in o.indices) {
            o[i] = o[i] + (gain * 0.9 * midFiltered[i]).toFloat()
        }

        if (2500 / ny < 0.99) {
            val highCoeffs = band(bandTaps, 2500 / ny, min(4000 / ny, 0.99))
            val highFiltered = filter(o, highCoeffs)
            for (i in o.indices) {
                o[i] = o[i] + (gain * 0.4 * highFiltered[i]).toFloat()
            }
        }

        // High-pass filter at 80 Hz to remove rumble
        val hp = fir(hpTaps, 80 / ny, true)
        o = filter(o, hp)

        // Low-pass filter
        val lp = fir(bandTaps, min(8000 / ny, 0.99))
        o = filter(o, lp)

        return o
    }
}


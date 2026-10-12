package com.signalchain.app.dsp

import kotlin.math.*

object Eq {
    private fun oddTaps(base: Int, fs: Int): Int {
        val t = Math.round(base * fs / 16000.0).toInt()
        return if (t % 2 == 0) t + 1 else t
    }

    /** cutNorm = cutoff / Nyquist in (0,1]. fc is cycles/sample = cutNorm / 2. */
    private fun fir(n: Int, cutNorm: Double, high: Boolean = false): DoubleArray {
        val m = (n - 1) / 2
        val fc = cutNorm / 2.0
        return DoubleArray(n) { i ->
            val k = i - m
            val lp = if (k == 0) 2 * fc else sin(2 * Math.PI * fc * k) / (Math.PI * k)
            val h = if (high) (if (k == 0) 1.0 - lp else -lp) else lp
            h * (0.54 - 0.46 * cos(2 * Math.PI * i / (n - 1)))   // Hamming, same as scipy default
        }
    }

    private fun gainAt(b: DoubleArray, cyclesPerSample: Double): Double {
        var re = 0.0
        var im = 0.0
        for (i in b.indices) {
            val a = 2 * Math.PI * cyclesPerSample * i
            re += b[i] * cos(a)
            im -= b[i] * sin(a)
        }
        return sqrt(re * re + im * im)
    }

    /** Band-pass with unity gain at band centre (matches scipy.firwin scale=True). */
    private fun band(n: Int, lo: Double, hi: Double): DoubleArray {
        val hiLp = fir(n, hi)
        val loLp = fir(n, lo)
        val b = DoubleArray(n) { hiLp[it] - loLp[it] }
        val g = gainAt(b, (lo + hi) / 4.0)
        return if (g > 1e-9) DoubleArray(n) { b[it] / g } else b
    }

    private fun highpass(n: Int, cutNorm: Double): DoubleArray {
        val h = fir(n, cutNorm, high = true)
        val g = gainAt(h, 0.5)
        return if (g > 1e-9) DoubleArray(n) { h[it] / g } else h
    }

    private fun lowpass(n: Int, cutNorm: Double): DoubleArray {
        val h = fir(n, cutNorm, high = false)
        val g = gainAt(h, 0.0)
        return if (abs(g) > 1e-9) DoubleArray(n) { h[it] / g } else h
    }

    private fun filter(x: FloatArray, b: DoubleArray): FloatArray {
        val y = FloatArray(x.size)
        val bSize = b.size
        for (i in x.indices) {
            var acc = 0.0
            val jMax = min(i, bSize - 1)
            for (j in 0..jMax) {
                acc += x[i - j] * b[j]
            }
            y[i] = acc.toFloat()
        }
        return y
    }

    fun apply(x: FloatArray, fs: Int, gain: Double = 1.0): FloatArray {
        if (gain <= 0.0) return x.copyOf()
        val ny = fs / 2.0
        var o = x.copyOf()

        val bandTaps = oddTaps(65, fs)
        val hpTaps = oddTaps(129, fs)
        val lpTaps = oddTaps(65, fs)

        val f1Low = 300.0 / ny
        val f1High = 500.0 / ny
        if (f1High < 1.0) {
            val b1 = band(bandTaps, f1Low, f1High)
            val filtered1 = filter(o, b1)
            for (i in o.indices) o[i] += (gain * 0.6 * filtered1[i]).toFloat()
        }

        val f2Low = 1000.0 / ny
        val f2High = min(2500.0 / ny, 0.99)
        val b2 = band(bandTaps, f2Low, f2High)
        val filtered2 = filter(o, b2)
        for (i in o.indices) o[i] += (gain * 0.9 * filtered2[i]).toFloat()

        val f3Low = 2500.0 / ny
        val f3High = min(4000.0 / ny, 0.99)
        if (f3Low < 0.99 && f3Low < f3High) {
            val b3 = band(bandTaps, f3Low, f3High)
            val filtered3 = filter(o, b3)
            for (i in o.indices) o[i] += (gain * 0.4 * filtered3[i]).toFloat()
        }

        val hp = highpass(hpTaps, 80.0 / ny)
        o = filter(o, hp)

        if (8000.0 / ny < 0.99) {
            val lp = lowpass(lpTaps, min(8000.0 / ny, 0.99))
            o = filter(o, lp)
        }

        return o
    }
}

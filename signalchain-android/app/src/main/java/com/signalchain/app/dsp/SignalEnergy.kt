package com.signalchain.app.dsp

/** Primitive accumulation avoids one boxed Double per audio sample. */
internal object SignalEnergy {
    fun meanSquare(x: FloatArray): Double {
        var sum = 0.0
        for (sample in x) sum += sample.toDouble() * sample
        return if (x.isEmpty()) Double.NaN else sum / x.size
    }
}

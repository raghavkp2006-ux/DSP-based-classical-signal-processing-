package com.signalchain.app.dsp

import org.junit.Assert.*
import org.junit.Test

class SignalEnergyTest {
    @Test fun primitiveAccumulationMatchesBoxedReferenceExactly() {
        val rng = java.util.Random(91)
        for (n in listOf(0, 1, 100, 100000)) {
            val x = FloatArray(n) { rng.nextFloat()*2-1 }
            assertEquals(x.map { it.toDouble()*it }.average(), SignalEnergy.meanSquare(x), 0.0)
        }
    }
}

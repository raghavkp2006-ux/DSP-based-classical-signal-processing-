package com.signalchain.app.agent

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

class AgentParityTest {
    @Test fun pythonGridMatchesEveryParameter() {
        val json = javaClass.classLoader!!.getResourceAsStream("agent_params_golden.json")!!.bufferedReader().use { it.readText() }
        val rows = Regex("\\{\\s*\\\"snr\\\".*?\\\"params\\\"\\s*:\\s*\\{.*?}\\s*}", RegexOption.DOT_MATCHES_ALL).findAll(json).map { it.value }.toList()
        assertEquals(64, rows.size)
        val failures = mutableListOf<String>()
        for (row in rows) {
            fun value(key: String): String = Regex("\\\"$key\\\"\\s*:\\s*(\\\"[^\\\"]*\\\"|true|false|[-0-9.eE]+)").find(row)!!.groupValues[1].trim('"')
            val snr=value("snr").toDouble(); val stationary=value("stationary").toBoolean(); val activity=value("activity").toDouble()
            val d=DecisionAgent.decide(snr,stationary,activity); val p=d.params
            val label="snr=$snr stationary=$stationary activity=$activity mode=${d.mode}"
            fun compare(key:String, actual:Double) { val expected=value(key).toDouble(); if (abs(actual-expected)>1e-9) failures.add("$label $key python=$expected kotlin=$actual") }
            fun compareBool(key:String, actual:Boolean) { val expected=value(key).toBoolean(); if (actual!=expected) failures.add("$label $key python=$expected kotlin=$actual") }
            if(d.mode!=value("mode")) failures.add("$label expectedMode=${value("mode")}")
            compare("pre_emph_coeff",p.preEmphCoeff); compare("alpha",p.alpha); compare("beta",p.beta); compare("gain_smooth",p.gainSmooth)
            compareBool("use_spectral_subtraction",p.useSpectralSubtraction); compareBool("use_ml_postfilter",p.useMlPostfilter)
            compare("vad_hangover_frames",p.vadHangoverFrames.toDouble()); compareBool("use_eq",p.useEq); compare("eq_gain",p.eqGain)
            compare("compression_ratio",p.compressionRatio); compare("compression_makeup_db",p.compressionMakeupDb)
        }
        println("PARITY rows=${rows.size} mismatches=${failures.size}")
        failures.forEach(::println)
        assertTrue("Decision parity mismatches: ${failures.joinToString("; ")}", failures.isEmpty())
    }
}

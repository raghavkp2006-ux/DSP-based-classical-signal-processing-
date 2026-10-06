package com.signalchain.app.dsp

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sign

data class VadResult(val isSpeech: BooleanArray, val frameEnergy: FloatArray, val frameZcr: FloatArray)

object Vad {
    fun detect(x: FloatArray, fp: FrameParams, hangover: Int = 0): VadResult {
        val energy = FloatArray(fp.numFrames)
        val zcr = FloatArray(fp.numFrames)
        for (i in 0 until fp.numFrames) {
            var energySum = 0.0
            var zcrSum = 0.0
            var previous = 0.0
            for (k in 0 until fp.frameLen) {
                val current = x.getOrElse(i * fp.hopLen + k) { 0f }.toDouble()
                energySum += current * current
                if (k > 0) zcrSum += abs(sign(current) - sign(previous))
                previous = current
            }
            energy[i] = (energySum / fp.frameLen).toFloat()
            zcr[i] = (zcrSum / (2.0 * fp.frameLen)).toFloat()
        }

        val nn = max(1, round(fp.numFrames * 0.2).toInt())
        val noiseFloor = energy.sorted().take(nn).average() * 3
        val zcrThreshold = zcr.average() * 2
        val speech = BooleanArray(fp.numFrames) { energy[it] > noiseFloor && zcr[it] < zcrThreshold }
        if (speech.count { !it } < round(fp.numFrames * 0.2).toInt()) {
            java.util.Arrays.fill(speech, true)
            energy.indices.sortedBy { energy[it] }.take(round(fp.numFrames * 0.2).toInt()).forEach { speech[it] = false }
        }
        for (offset in 1..hangover) {
            for (i in offset until speech.size) speech[i] = speech[i] || speech[i - offset]
        }
        return VadResult(speech, energy, zcr)
    }

    fun detectSpeechFrames(x: FloatArray, sampleRate: Int) = detect(x, FrameParams.create(x.size, sampleRate)).isSpeech
}

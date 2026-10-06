package com.signalchain.app.dsp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sign
import kotlin.random.Random
import kotlin.math.sin

class VadTest {
    @Test
    fun testVadDetectsSpeechAndSilence() {
        val sampleRate = 16000
        val random = Random(42)
        
        // 1 second of silence, 1 second of speech, 1 second of silence
        val signal = FloatArray(3 * sampleRate) { i ->
            if (i in sampleRate until 2 * sampleRate) {
                // High energy speech-like
                (sin(2.0 * Math.PI * 300.0 * i / sampleRate) * 0.8 + random.nextFloat() * 0.2 - 0.1).toFloat()
            } else {
                // Constant low-energy high-frequency noise to avoid triggering the relative 3x energy threshold
                if (i % 2 == 0) 0.001f else -0.001f
            }
        }
        
        val isSpeech = Vad.detectSpeechFrames(signal, sampleRate)
        
        // Roughly frame size is 512 for 16kHz -> 32ms.
        // Hop is 128 -> 8ms. 3 seconds = 375 frames.
        
        // Check first second (silence)
        for (i in 20..80) {
            assertTrue("Expected silence at frame $i", !isSpeech[i])
        }
        
        // Check second second (speech)
        for (i in 120..180) {
            assertTrue("Expected speech at frame $i", isSpeech[i])
        }
        
        // Check third second (silence)
        for (i in 220..280) {
            assertTrue("Expected silence at frame $i", !isSpeech[i])
        }
    }

    @Test
    fun optimizedVadPreservesLegacySpeechArraysForThreeSignals() {
        val sampleRate = 16_000
        val random = Random(8128)
        val signals = listOf(
            FloatArray(sampleRate * 2) { i ->
                if (i in 4_000 until 22_000) (0.35 * sin(2.0 * Math.PI * 240 * i / sampleRate)).toFloat() else 0f
            },
            FloatArray(sampleRate * 2) { random.nextFloat() * 0.08f - 0.04f },
            FloatArray(sampleRate * 2) { i ->
                val burst = if ((i / 1_600) % 3 == 0) 0.12 else 0.01
                (burst * sin(2.0 * Math.PI * (180 + (i / 800) % 600) * i / sampleRate)).toFloat()
            }
        )
        signals.forEach { signal ->
            val fp = FrameParams.create(signal.size, sampleRate)
            assertArrayEquals(legacySpeechArray(signal, fp), Vad.detect(signal, fp).isSpeech)
        }
    }

    @Test
    fun tenMinuteVadFinishesUnderOneSecond() {
        val sampleRate = 16_000
        val signal = FloatArray(sampleRate * 60 * 10) { i ->
            (0.03 * sin(2.0 * Math.PI * 220 * i / sampleRate)).toFloat()
        }
        val start = System.nanoTime()
        val frameParams = FrameParams.create(signal.size, sampleRate)
        val result = Vad.detect(signal, frameParams)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertEquals(frameParams.numFrames, result.isSpeech.size)
        assertTrue("10-minute VAD took ${elapsedMs}ms", elapsedMs < 1_000)
    }

    /** Kept as the pre-optimization algorithm so output arrays remain directly comparable. */
    private fun legacySpeechArray(x: FloatArray, fp: FrameParams): BooleanArray {
        val energy = FloatArray(fp.numFrames)
        val zcr = FloatArray(fp.numFrames)
        for (i in 0 until fp.numFrames) {
            val frame = DoubleArray(fp.frameLen) { k -> x.getOrElse(i * fp.hopLen + k) { 0f }.toDouble() }
            energy[i] = (frame.sumOf { it * it } / fp.frameLen).toFloat()
            zcr[i] = (frame.drop(1).indices.sumOf { abs(sign(frame[it + 1]) - sign(frame[it])) } /
                (2.0 * fp.frameLen)).toFloat()
        }
        val nn = max(1, round(fp.numFrames * 0.2).toInt())
        val speech = BooleanArray(fp.numFrames) {
            energy[it] > energy.sorted().take(nn).average() * 3 && zcr[it] < zcr.average() * 2
        }
        if (speech.count { !it } < round(fp.numFrames * 0.2).toInt()) {
            java.util.Arrays.fill(speech, true)
            energy.indices.sortedBy { energy[it] }.take(round(fp.numFrames * 0.2).toInt())
                .forEach { speech[it] = false }
        }
        return speech
    }
}

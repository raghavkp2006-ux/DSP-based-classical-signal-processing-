package com.signalchain.app.dsp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
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
}

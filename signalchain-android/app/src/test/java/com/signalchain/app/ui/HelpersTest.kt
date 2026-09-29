package com.signalchain.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream

class HelpersTest {
    @Test
    fun oversizedStreamStopsBeforeTheFullPayloadIsCopied() {
        val maxBytes = 50L * 1024 * 1024
        val sourceSize = maxBytes + 128 * 1024
        val input = GeneratedInputStream(sourceSize)
        val output = CountingOutputStream()

        try {
            copyWithSizeLimit(input, output, maxBytes)
            fail("Expected an oversized stream to be rejected")
        } catch (e: IllegalStateException) {
            assertEquals("File is larger than the 50 MB limit.", e.message)
        }

        assertEquals(maxBytes, output.bytesWritten)
        assertTrue("Copy must abort before consuming the full oversized payload", input.bytesRead < sourceSize)
    }

    private class GeneratedInputStream(private val totalBytes: Long) : InputStream() {
        var bytesRead = 0L
            private set

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (bytesRead >= totalBytes) return -1
            val count = minOf(length.toLong(), totalBytes - bytesRead).toInt()
            buffer.fill(0, offset, offset + count)
            bytesRead += count
            return count
        }

        override fun read(): Int {
            if (bytesRead >= totalBytes) return -1
            bytesRead++
            return 0
        }
    }

    private class CountingOutputStream : OutputStream() {
        var bytesWritten = 0L
            private set

        override fun write(value: Int) {
            bytesWritten++
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            bytesWritten += length
        }
    }
}

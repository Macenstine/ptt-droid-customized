package com.github.devapro.pttdroid.wear.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PcmGainTest {

    private fun pcm(vararg samples: Int): ByteArray {
        val bytes = ByteArray(samples.size * 2)
        samples.forEachIndexed { i, s ->
            bytes[i * 2] = s.toByte()
            bytes[i * 2 + 1] = (s shr 8).toByte()
        }
        return bytes
    }

    @Test
    fun `scales samples and reports the pre-gain peak`() {
        val frame = pcm(100, -200, 50)

        val peak = PcmGain.apply(frame, 4f)

        assertEquals(200, peak)
        assertArrayEquals(pcm(400, -800, 200), frame)
    }

    @Test
    fun `clips at the 16 bit limits instead of wrapping`() {
        val frame = pcm(20_000, -20_000)

        PcmGain.apply(frame, 4f)

        assertArrayEquals(pcm(32_767, -32_768), frame)
    }

    @Test
    fun `silence stays silent`() {
        val frame = pcm(0, 0, 0)

        assertEquals(0, PcmGain.apply(frame, 8f))
        assertArrayEquals(pcm(0, 0, 0), frame)
    }

    @Test
    fun `a trailing odd byte is left alone`() {
        val frame = pcm(100) + byteArrayOf(0x7F)

        PcmGain.apply(frame, 2f)

        assertEquals(0x7F, frame[2].toInt())
        assertEquals(200, (frame[0].toInt() and 0xFF) or (frame[1].toInt() shl 8))
    }

    @Test
    fun `unity gain changes nothing`() {
        val frame = pcm(1, -1, 12_345, -32_768)
        val copy = frame.copyOf()

        PcmGain.apply(frame, 1f)

        assertArrayEquals(copy, frame)
    }
}

package com.github.devapro.pttdroid.wear.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoGainTest {

    /** A frame whose loudest sample is [peak]; the rest are half of it. */
    private fun frame(peak: Int, samples: Int = 640): ByteArray {
        val bytes = ByteArray(samples * 2)
        for (i in 0 until samples) {
            val s = if (i == 0) peak else peak / 2
            bytes[i * 2] = s.toByte()
            bytes[i * 2 + 1] = (s shr 8).toByte()
        }
        return bytes
    }

    @Test
    fun `quiet speech is raised towards the target`() {
        val gain = AutoGain()
        var last = frame(1_500)
        repeat(60) {
            last = frame(1_500)
            gain.apply(last)
        }
        val out = PcmGain.peak(last)
        val target = AutoGain.TARGET_PEAK
        assertTrue("peak $out should approach $target", out in (target * 0.85f).toInt()..(target * 1.01f).toInt())
    }

    @Test
    fun `silence is not boosted`() {
        val gain = AutoGain()
        val before = gain.gain

        val quiet = frame(100)
        gain.apply(quiet)

        assertEquals(before, gain.gain, 0f)
        assertEquals(100 * before.toInt(), PcmGain.peak(quiet))
    }

    @Test
    fun `a loud frame pulls the gain down at once and is never attenuated`() {
        val gain = AutoGain()

        val loud = frame(25_000)
        gain.apply(loud)

        assertEquals(AutoGain.MIN_GAIN, gain.gain, 0f)
        assertEquals(25_000, PcmGain.peak(loud))
    }

    @Test
    fun `a moderately loud frame is brought down to the target without clipping`() {
        val gain = AutoGain()

        val loud = frame(8_000)
        gain.apply(loud)

        assertTrue("gain ${gain.gain} should have dropped below its start", gain.gain < AutoGain.INITIAL_GAIN)
        val target = AutoGain.TARGET_PEAK.toInt()
        assertTrue(PcmGain.peak(loud) in (target - 100)..(target + 100))
    }

    @Test
    fun `gain is capped`() {
        val gain = AutoGain(initialGain = 1f)
        repeat(500) { gain.apply(frame(350)) }
        assertTrue(gain.gain <= AutoGain.MAX_GAIN)
    }

    @Test
    fun `release is gradual`() {
        val gain = AutoGain(initialGain = 1f)
        gain.apply(frame(1_000))
        // The wanted gain is 20, but a single frame only moves a tenth of the way there.
        assertTrue(gain.gain < 3.5f)
    }
}

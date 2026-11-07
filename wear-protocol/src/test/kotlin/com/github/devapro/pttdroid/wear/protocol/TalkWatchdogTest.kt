package com.github.devapro.pttdroid.wear.protocol

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TalkWatchdogTest {

    private val watchdog = TalkWatchdog(heartbeatTimeoutMs = 1_500, maxTalkMs = 10_000)

    @Test
    fun `never releases while disarmed`() {
        assertFalse(watchdog.shouldRelease(now = 1_000_000))
    }

    @Test
    fun `keeps the floor while heartbeats arrive`() {
        watchdog.arm(0)
        for (t in 500L..9_000L step 500L) {
            watchdog.beat(t)
            assertFalse("at $t", watchdog.shouldRelease(t))
        }
    }

    @Test
    fun `releases once the heartbeat goes quiet`() {
        watchdog.arm(0)
        watchdog.beat(500)
        assertFalse(watchdog.shouldRelease(1_999))
        assertTrue(watchdog.shouldRelease(2_000))
    }

    @Test
    fun `releases at the hard cap even with a live heartbeat`() {
        watchdog.arm(0)
        watchdog.beat(9_900)
        assertTrue(watchdog.shouldRelease(10_000))
    }

    @Test
    fun `a late heartbeat after disarm does not re-arm`() {
        watchdog.arm(0)
        watchdog.disarm()
        watchdog.beat(100)
        assertFalse(watchdog.isArmed)
        assertFalse(watchdog.shouldRelease(1_000_000))
    }

    @Test
    fun `can be armed again after a release`() {
        watchdog.arm(0)
        watchdog.disarm()
        watchdog.arm(5_000)
        assertFalse(watchdog.shouldRelease(5_500))
        assertTrue(watchdog.shouldRelease(6_500))
    }
}

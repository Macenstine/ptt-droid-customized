package com.github.devapro.pttdroid.wear.protocol

/**
 * Decides when the phone must let go of the floor on its own.
 *
 * Hold-to-talk crosses Bluetooth as discrete messages, and the "button up" one can be lost — the
 * watch walks out of range, its screen turns off, the app is killed. Without a fallback the
 * phone's microphone would stay open on the channel. The watch repeats a heartbeat while the
 * button is held, so silence means it is gone; [maxTalkMs] is a hard cap on top of that for a
 * heartbeat that keeps coming while the button is somehow stuck.
 *
 * Pure and clock-agnostic: callers pass a monotonic millisecond reading.
 */
class TalkWatchdog(
    private val heartbeatTimeoutMs: Long = DEFAULT_HEARTBEAT_TIMEOUT_MS,
    private val maxTalkMs: Long = DEFAULT_MAX_TALK_MS,
) {
    private var startedAt = NOT_ARMED
    private var lastBeat = NOT_ARMED

    val isArmed: Boolean get() = startedAt != NOT_ARMED

    fun arm(now: Long) {
        startedAt = now
        lastBeat = now
    }

    /** Ignored while disarmed, so a late heartbeat after "button up" cannot re-arm anything. */
    fun beat(now: Long) {
        if (isArmed) lastBeat = now
    }

    fun disarm() {
        startedAt = NOT_ARMED
        lastBeat = NOT_ARMED
    }

    fun shouldRelease(now: Long): Boolean =
        isArmed && (now - lastBeat >= heartbeatTimeoutMs || now - startedAt >= maxTalkMs)

    companion object {
        /** Three missed heartbeats at [WearPaths.HEARTBEAT_INTERVAL_MS]. */
        const val DEFAULT_HEARTBEAT_TIMEOUT_MS = 1_500L
        const val DEFAULT_MAX_TALK_MS = 60_000L
        private const val NOT_ARMED = -1L
    }
}

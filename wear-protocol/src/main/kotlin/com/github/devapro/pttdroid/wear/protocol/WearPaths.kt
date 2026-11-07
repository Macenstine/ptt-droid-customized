package com.github.devapro.pttdroid.wear.protocol

/** Message and data paths shared by the phone bridge and the watch app. */
object WearPaths {
    /** Watch → phone: the button went down. Empty payload. */
    const val TALK_DOWN = "/ptt/talk/down"

    /** Watch → phone: the button came up. Empty payload. */
    const val TALK_UP = "/ptt/talk/up"

    /** Watch → phone, repeated while the button is held. Empty payload. */
    const val TALK_HEARTBEAT = "/ptt/talk/heartbeat"

    /** Watch → phone: switch channel. Payload from [ChannelPayload]. */
    const val CHANNEL = "/ptt/channel"

    /** Phone → watch, a data item holding an encoded [WearState]. */
    const val STATE = "/ptt/state"

    /**
     * Phone → watch audio stream: what the phone received, for the watch speaker. The watch
     * opens it and reads; it stays open while the watch app is in front.
     */
    const val AUDIO_DOWN = "/ptt/audio/down"

    /**
     * Watch → phone audio stream: the watch microphone while the watch has the floor. The watch
     * opens it and writes; it stays open while the watch app is in front.
     */
    const val AUDIO_UP = "/ptt/audio/up"

    /** How often the watch repeats [TALK_HEARTBEAT] while the button is held. */
    const val HEARTBEAT_INTERVAL_MS = 500L
}

/**
 * The audio format on both streams: the relay's own format (PCM16LE, 16 kHz, mono, 40 ms
 * frames), passed through unchanged so neither end has to convert. The phone's `AudioConfig`
 * is the source of truth; a test in `:app` keeps these equal.
 */
object WearAudioFormat {
    const val SAMPLE_RATE_HZ = 16_000
    const val FRAME_BYTES = 1_280
}

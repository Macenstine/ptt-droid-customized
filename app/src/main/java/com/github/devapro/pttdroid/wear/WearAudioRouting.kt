package com.github.devapro.pttdroid.wear

import com.github.devapro.pttdroid.audio.VoicePlayerContract
import com.github.devapro.pttdroid.audio.VoiceRecorderContract
import com.github.devapro.pttdroid.data.settings.AudioOutput
import kotlinx.coroutines.channels.ReceiveChannel

/**
 * The recorder `PttController` sees: the phone microphone, unless this transmission was started
 * from the watch and the watch's microphone stream is up.
 *
 * `PttController` calls [start] and only then reads [frames], so the getter can answer per
 * transmission without forwarding anything. A press on the phone itself always keeps the
 * phone's microphone, whether or not a watch is attached.
 */
class RoutingRecorder(
    private val phone: VoiceRecorderContract,
    private val hub: WearAudioHub,
) : VoiceRecorderContract {

    @Volatile
    private var usingWatch = false

    override val frames: ReceiveChannel<ByteArray>
        get() = if (usingWatch) hub.upFrames else phone.frames

    override fun start() {
        usingWatch = hub.isWatchTalking && hub.watchMicReady.value
        if (usingWatch) hub.beginCapture() else phone.start()
    }

    override fun stop() {
        if (usingWatch) hub.endCapture() else phone.stop()
    }

    override fun release() {
        hub.endCapture()
        phone.release()
    }
}

/**
 * The player `PttController` sees: the watch speaker while the watch is listening, the phone's
 * own speaker otherwise. The phone's player is still prepared either way, so audio falls back
 * to it the moment the watch goes away.
 *
 * Output route and volume belong to the phone's speaker; the watch plays at its own volume.
 */
class RoutingPlayer(
    private val phone: VoicePlayerContract,
    private val hub: WearAudioHub,
) : VoicePlayerContract {

    override fun prepare(): Boolean = phone.prepare()

    /** No logging here — this runs per audio frame. */
    override fun play(pcm: ByteArray) {
        if (hub.watchListening.value) hub.enqueueDown(pcm) else phone.play(pcm)
    }

    override fun stop() {
        phone.stop()
        hub.clearDown()
    }

    override fun setOutput(output: AudioOutput) = phone.setOutput(output)

    override fun setVolume(volume: Float) = phone.setVolume(volume)

    override fun release() {
        phone.release()
        hub.clearDown()
    }
}

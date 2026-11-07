package com.github.devapro.pttdroid.wear

import com.github.devapro.pttdroid.audio.AudioConfig
import com.github.devapro.pttdroid.audio.VoicePlayerContract
import com.github.devapro.pttdroid.audio.VoiceRecorderContract
import com.github.devapro.pttdroid.data.settings.AudioOutput
import com.github.devapro.pttdroid.wear.protocol.WearAudioFormat
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WearAudioHubTest {

    private val frameBytes = 8
    private val hub = WearAudioHub(frameBytes = frameBytes)

    private fun frame(fill: Int) = ByteArray(frameBytes) { fill.toByte() }

    /** The watch's end of the microphone channel: what it writes, the hub reads. */
    private class WatchMic {
        val input = PipedInputStream(1 shl 16)
        val out = PipedOutputStream(input)
    }

    /** The watch's end of the playback channel: what the hub writes, the watch reads. */
    private class WatchSpeaker {
        val out = PipedOutputStream()
        val input = PipedInputStream(out, 1 shl 16)
    }

    private fun awaitTrue(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 2_000
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("timed out waiting for $what")
            Thread.sleep(5)
        }
    }

    @Test
    fun `microphone frames are kept only while a transmission is capturing`() = runBlocking {
        val mic = WatchMic()
        hub.attachUp(mic.input)
        awaitTrue("mic ready") { hub.watchMicReady.value }

        mic.out.write(frame(1))
        mic.out.flush()
        awaitTrue("first frame read") { mic.input.available() == 0 }
        Thread.sleep(50)

        hub.beginCapture()
        mic.out.write(frame(2))
        mic.out.flush()

        val received = withTimeout(2_000) { hub.upFrames.receive() }
        assertArrayEquals(frame(2), received)
        assertTrue("the earlier frame must have been dropped", hub.upFrames.tryReceive().isFailure)
    }

    @Test
    fun `frames after capture ends are discarded`() = runBlocking {
        val mic = WatchMic()
        hub.attachUp(mic.input)
        awaitTrue("mic ready") { hub.watchMicReady.value }

        hub.beginCapture()
        hub.endCapture()
        mic.out.write(frame(3))
        mic.out.flush()
        awaitTrue("frame read") { mic.input.available() == 0 }
        Thread.sleep(50)

        assertTrue(hub.upFrames.tryReceive().isFailure)
    }

    @Test
    fun `mic is no longer ready once the watch closes its channel`() {
        val mic = WatchMic()
        hub.attachUp(mic.input)
        awaitTrue("mic ready") { hub.watchMicReady.value }

        mic.out.close()
        awaitTrue("mic not ready") { !hub.watchMicReady.value }
    }

    @Test
    fun `received frames reach the watch and the listening flag follows the stream`() {
        val speaker = WatchSpeaker()
        assertFalse(hub.watchListening.value)

        hub.attachDown(speaker.out)
        assertTrue(hub.watchListening.value)

        hub.enqueueDown(frame(4))
        val read = ByteArray(frameBytes)
        awaitTrue("frame on the wire") { speaker.input.available() >= frameBytes }
        speaker.input.read(read)
        assertArrayEquals(frame(4), read)

        // The watch goes away; the next write fails and playback falls back to the phone.
        speaker.input.close()
        hub.enqueueDown(frame(5))
        awaitTrue("listening cleared") { !hub.watchListening.value }
    }

    @Test
    fun `a new playback channel replaces the old one`() {
        val first = WatchSpeaker()
        val second = WatchSpeaker()
        hub.attachDown(first.out)
        hub.attachDown(second.out)
        assertTrue(hub.watchListening.value)

        hub.enqueueDown(frame(6))
        awaitTrue("frame on the second channel") { second.input.available() >= frameBytes }
        assertEquals(0, first.input.available())
        assertTrue("the replaced channel must not clear the flag", hub.watchListening.value)
    }

    @Test
    fun `the wire format matches the relay's`() {
        assertEquals(AudioConfig.FRAME_BYTES, WearAudioFormat.FRAME_BYTES)
        assertEquals(AudioConfig.SAMPLE_RATE_HZ, WearAudioFormat.SAMPLE_RATE_HZ)
    }

    // --- RoutingRecorder / RoutingPlayer ---------------------------------------------------

    private class FakeRecorder : VoiceRecorderContract {
        override val frames: Channel<ByteArray> = Channel(8)
        var started = 0
        var stopped = 0
        override fun start() { started++ }
        override fun stop() { stopped++ }
        override fun release() = Unit
    }

    private class FakePlayer : VoicePlayerContract {
        val played = mutableListOf<ByteArray>()
        var stopped = 0
        override fun prepare() = true
        override fun play(pcm: ByteArray) { played += pcm }
        override fun stop() { stopped++ }
        override fun setOutput(output: AudioOutput) = Unit
        override fun setVolume(volume: Float) = Unit
        override fun release() = Unit
    }

    @Test
    fun `the phone microphone is used unless the watch started the talk`() {
        val phone = FakeRecorder()
        val recorder = RoutingRecorder(phone, hub)
        val mic = WatchMic()
        hub.attachUp(mic.input)
        awaitTrue("mic ready") { hub.watchMicReady.value }

        // Watch attached, but the press was on the phone itself.
        recorder.start()
        assertEquals(1, phone.started)
        assertSame(phone.frames, recorder.frames)
        recorder.stop()
        assertEquals(1, phone.stopped)

        // Same watch, now the press came from it.
        hub.beginWatchTalk()
        recorder.start()
        assertEquals("the phone mic must stay closed", 1, phone.started)
        assertSame(hub.upFrames, recorder.frames)
        recorder.stop()
        assertEquals(1, phone.stopped)
    }

    @Test
    fun `a watch talk with no microphone stream falls back to the phone`() {
        val phone = FakeRecorder()
        val recorder = RoutingRecorder(phone, hub)
        hub.beginWatchTalk()

        recorder.start()

        assertEquals(1, phone.started)
        assertSame(phone.frames, recorder.frames)
    }

    @Test
    fun `received audio plays on the phone until a watch is listening`() {
        val phone = FakePlayer()
        val player = RoutingPlayer(phone, hub)

        player.play(frame(7))
        assertEquals(1, phone.played.size)

        val speaker = WatchSpeaker()
        hub.attachDown(speaker.out)
        player.play(frame(8))

        assertEquals("the phone speaker must go quiet", 1, phone.played.size)
        awaitTrue("frame on the wire") { speaker.input.available() >= frameBytes }
    }
}

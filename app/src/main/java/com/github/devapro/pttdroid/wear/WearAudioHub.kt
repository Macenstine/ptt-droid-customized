package com.github.devapro.pttdroid.wear

import com.github.devapro.pttdroid.audio.AudioConfig
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * The phone's end of the two watch audio streams.
 *
 * The watch opens both channels and keeps them open while its app is in front; this class owns
 * the phone's half of each:
 *
 * - **down** (phone → watch): [enqueueDown] queues received frames, and a writer drains them
 *   into the channel. While the stream is up ([watchListening]) the phone plays received audio
 *   on the watch instead of its own speaker.
 * - **up** (watch → phone): a reader turns the channel back into fixed-size frames on
 *   [upFrames], but only while a transmission is capturing ([beginCapture]); anything the
 *   watch sends outside that window is discarded.
 *
 * Both queues are bounded and drop the oldest frame: this is a live voice path, so stale audio
 * is worth less than a gap. No logging on the per-frame paths.
 */
class WearAudioHub(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val frameBytes: Int = AudioConfig.FRAME_BYTES,
) {
    private val lock = Any()

    private val downQueue = Channel<ByteArray>(DOWN_QUEUE_FRAMES, BufferOverflow.DROP_OLDEST)
    private var downStream: OutputStream? = null
    private var downJob: Job? = null
    private val _watchListening = MutableStateFlow(false)

    /** True while the watch has an open stream to play received audio on. */
    val watchListening: StateFlow<Boolean> = _watchListening.asStateFlow()

    /** Frames from the watch microphone; read by `RoutingRecorder` during a watch transmission. */
    val upFrames = Channel<ByteArray>(UP_QUEUE_FRAMES, BufferOverflow.DROP_OLDEST)
    private var upStream: InputStream? = null
    private val _watchMicReady = MutableStateFlow(false)

    /** True while the watch has an open stream to send its microphone on. */
    val watchMicReady: StateFlow<Boolean> = _watchMicReady.asStateFlow()

    @Volatile
    private var accepting = false

    private val capturedFrames = AtomicInteger(0)

    @Volatile
    private var watchTalking = false

    /**
     * Whether the transmission in progress was started from the watch. Only then does the
     * recorder take the watch microphone; a press on the phone itself keeps using the phone's.
     */
    val isWatchTalking: Boolean get() = watchTalking

    fun beginWatchTalk() {
        watchTalking = true
    }

    fun endWatchTalk() {
        watchTalking = false
    }

    /** Called when the watch opens its playback channel; [out] is the phone's writing end. */
    fun attachDown(out: OutputStream) {
        val previous = synchronized(lock) {
            downStream.also { downStream = out }
        }
        // Cancel the old writer too: closing its stream does not wake it from waiting on the
        // queue, and it would otherwise swallow the next frame on its way to a dead channel.
        downJob?.cancel()
        previous.closeQuietly()
        clearDown()
        _watchListening.value = true
        downJob = scope.launch {
            try {
                for (frame in downQueue) {
                    out.write(frame)
                    out.flush()
                }
            } catch (e: IOException) {
                Timber.d("Watch playback stream closed: %s", e.toString())
            } finally {
                out.closeQuietly()
                synchronized(lock) {
                    if (downStream === out) {
                        downStream = null
                        _watchListening.value = false
                    }
                }
            }
        }
    }

    /** Called when the watch opens its microphone channel; [input] is the phone's reading end. */
    fun attachUp(input: InputStream) {
        val previous = synchronized(lock) {
            upStream.also { upStream = input }
        }
        previous.closeQuietly()
        _watchMicReady.value = true
        scope.launch {
            val data = DataInputStream(input)
            val buffer = ByteArray(frameBytes)
            try {
                while (isActive) {
                    data.readFully(buffer)
                    if (accepting) {
                        upFrames.trySend(buffer.copyOf())
                        capturedFrames.incrementAndGet()
                    }
                }
            } catch (e: IOException) {
                // EOF is an IOException too: the watch closed the channel or went away.
                Timber.d("Watch microphone stream closed: %s", e.toString())
            } finally {
                input.closeQuietly()
                synchronized(lock) {
                    if (upStream === input) {
                        upStream = null
                        _watchMicReady.value = false
                    }
                }
            }
        }
    }

    fun enqueueDown(pcm: ByteArray) {
        downQueue.trySend(pcm)
    }

    fun clearDown() {
        while (downQueue.tryReceive().isSuccess) { /* discard */ }
    }

    /** Starts keeping frames from the watch microphone, discarding any left from before. */
    fun beginCapture() {
        while (upFrames.tryReceive().isSuccess) { /* discard */ }
        accepting = true
    }

    fun endCapture() {
        accepting = false
        // One line per transmission, not per frame.
        val frames = capturedFrames.getAndSet(0)
        if (frames > 0) Timber.d("Watch microphone: %d frames received", frames)
    }

    private fun AutoCloseable?.closeQuietly() {
        if (this == null) return
        try {
            close()
        } catch (_: Exception) {
            // Already closed, or the peer is gone.
        }
    }

    private companion object {
        /** 1 s of audio: more than a hiccup, less than a lag the listener would notice. */
        const val DOWN_QUEUE_FRAMES = 25
        const val UP_QUEUE_FRAMES = 50
    }
}

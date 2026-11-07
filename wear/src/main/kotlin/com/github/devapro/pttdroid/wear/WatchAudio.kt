package com.github.devapro.pttdroid.wear

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import com.github.devapro.pttdroid.wear.protocol.AutoGain
import com.github.devapro.pttdroid.wear.protocol.WearAudioFormat
import com.github.devapro.pttdroid.wear.protocol.WearPaths
import com.github.devapro.pttdroid.wear.protocol.WearState
import com.github.devapro.pttdroid.wear.protocol.WearStatus
import com.google.android.gms.wearable.Wearable
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val TAG = "WatchAudio"

/**
 * The watch's end of the audio: its speaker plays what the phone received, and its microphone
 * is what the phone transmits while the watch holds the floor.
 *
 * Two channels to the phone stay open while the app is in front — [WearPaths.AUDIO_DOWN] for
 * playback and [WearPaths.AUDIO_UP] for the microphone — and are reopened if they drop. No
 * logging on the per-frame paths.
 */
class AudioLink(context: Context, private val scope: CoroutineScope) {

    private val channelClient = Wearable.getChannelClient(context)
    private val nodeClient = Wearable.getNodeClient(context)
    private val speaker = WatchSpeaker()

    private val outFrames = Channel<ByteArray>(OUT_QUEUE_FRAMES, BufferOverflow.DROP_OLDEST)
    private val mic = WatchMic { frame -> outFrames.trySend(frame) }

    // Microphone start/stop touch hardware and must stay in order, so they share one lane.
    private val micLane = Dispatchers.IO.limitedParallelism(1)

    @Volatile
    private var transmitting = false

    @Volatile
    private var rxStream: InputStream? = null

    @Volatile
    private var txStream: OutputStream? = null

    private var jobs: List<Job> = emptyList()

    fun start() {
        if (jobs.isNotEmpty()) return
        jobs = listOf(
            scope.launch(Dispatchers.IO) { playbackLoop() },
            scope.launch(Dispatchers.IO) { microphoneLoop() },
        )
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
        // Blocking reads and writes on the channel streams only end when the stream closes.
        rxStream.closeQuietly()
        txStream.closeQuietly()
        scope.launch(micLane) {
            mic.stop()
            speaker.release()
        }
    }

    /** The phone says whether this watch currently holds the floor. */
    fun onState(state: WearState?) {
        transmitting = state?.status == WearStatus.TRANSMITTING
        mic.sending = transmitting
    }

    /**
     * Starts capturing on button press. Frames are held back until the phone reports the floor
     * is ours ([onState]), then sent, so the first words spoken while waiting are not lost.
     */
    fun beginTalk() {
        scope.launch(micLane) {
            while (outFrames.tryReceive().isSuccess) { /* discard stale frames */ }
            mic.sending = transmitting
            if (!mic.start()) Log.w(TAG, "Microphone unavailable")
        }
    }

    fun endTalk() {
        scope.launch(micLane) { mic.stop() }
    }

    private suspend fun playbackLoop() {
        while (scope.isActive) {
            try {
                val node = nodeClient.connectedNodes.await().firstOrNull()
                if (node != null) {
                    val channel = channelClient.openChannel(node.id, WearPaths.AUDIO_DOWN).await()
                    val input = channelClient.getInputStream(channel).await()
                    rxStream = input
                    if (speaker.start()) readFrames(input)
                    input.closeQuietly()
                    rxStream = null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "playback channel: $e")
            }
            delay(RETRY_MS)
        }
    }

    private fun readFrames(input: InputStream) {
        val data = DataInputStream(input)
        val frame = ByteArray(WearAudioFormat.FRAME_BYTES)
        var played = 0
        try {
            while (true) {
                data.readFully(frame)
                speaker.write(frame)
                played++
            }
        } catch (_: IOException) {
            // The phone closed the channel or went out of range; the loop reopens it.
        } finally {
            Log.d(TAG, "playback channel closed; $played frames played")
        }
    }

    private suspend fun microphoneLoop() {
        while (scope.isActive) {
            try {
                val node = nodeClient.connectedNodes.await().firstOrNull()
                if (node != null) {
                    val channel = channelClient.openChannel(node.id, WearPaths.AUDIO_UP).await()
                    val out = channelClient.getOutputStream(channel).await()
                    txStream = out
                    try {
                        for (frame in outFrames) {
                            out.write(frame)
                            out.flush()
                        }
                    } catch (_: IOException) {
                        // Channel dropped mid-transmission; reopen below.
                    }
                    out.closeQuietly()
                    txStream = null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "microphone channel: $e")
            }
            delay(RETRY_MS)
        }
    }

    private fun AutoCloseable?.closeQuietly() {
        try {
            this?.close()
        } catch (_: Exception) {
            // Already closed.
        }
    }

    private companion object {
        const val RETRY_MS = 2_000L
        const val OUT_QUEUE_FRAMES = 50
    }
}

/** Plays 16 kHz mono PCM16 frames on the watch speaker. */
class WatchSpeaker {

    private var track: AudioTrack? = null

    fun start(): Boolean {
        if (track != null) return true
        val minBuffer = AudioTrack.getMinBufferSize(
            WearAudioFormat.SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) return false
        val built = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(WearAudioFormat.SAMPLE_RATE_HZ)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                // Several frames deep, so Bluetooth jitter drains frames already written
                // instead of starving the speaker.
                .setBufferSizeInBytes(maxOf(minBuffer, WearAudioFormat.FRAME_BYTES * DEVICE_BUFFER_FRAMES))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            Log.w(TAG, "Could not create AudioTrack", e)
            return false
        }
        if (built.state != AudioTrack.STATE_INITIALIZED) {
            built.release()
            return false
        }
        built.play()
        track = built
        return true
    }

    /** Blocks until the device has taken the frame; that back-pressure is what paces the reader. */
    fun write(frame: ByteArray) {
        track?.write(frame, 0, frame.size)
    }

    fun release() {
        val current = track ?: return
        track = null
        runCatching { current.stop() }
        current.release()
    }

    private companion object {
        const val DEVICE_BUFFER_FRAMES = 8
    }
}

/**
 * Captures the watch microphone as fixed-size frames.
 *
 * While [sending] is false, frames are kept in a short rolling buffer; when it turns true that
 * buffer is flushed first and live frames follow. Starting capture on the button press and
 * sending only once the phone reports the floor is ours means the beginning of the first word
 * is neither lost to the round trip nor sent before the relay would accept it.
 */
class WatchMic(private val sendFrame: (ByteArray) -> Unit) {

    @Volatile
    var sending = false

    @Volatile
    private var running = false

    private var thread: Thread? = null

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true
        val minBuffer = AudioRecord.getMinBufferSize(
            WearAudioFormat.SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) return false
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                WearAudioFormat.SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, WearAudioFormat.FRAME_BYTES * 4),
            )
        } catch (e: Exception) {
            // Typically a missing RECORD_AUDIO permission.
            Log.w(TAG, "Could not create AudioRecord", e)
            return false
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return false
        }
        try {
            record.startRecording()
        } catch (e: Exception) {
            record.release()
            return false
        }
        running = true
        thread = Thread({ captureLoop(record) }, "WatchMic").also { it.start() }
        return true
    }

    fun stop() {
        running = false
        sending = false
        val capture = thread ?: return
        thread = null
        // The loop releases the AudioRecord on its way out; wait briefly so a quick restart
        // does not open a second one on top of it.
        runCatching { capture.join(JOIN_MS) }
    }

    private fun captureLoop(record: AudioRecord) {
        val preroll = ArrayDeque<ByteArray>()
        var sent = 0
        var peak = 0
        // A fresh gain per press: the next one may be at a different distance from the mouth.
        val autoGain = AutoGain()
        try {
            while (running) {
                val frame = ByteArray(WearAudioFormat.FRAME_BYTES)
                var filled = 0
                while (filled < frame.size && running) {
                    val read = record.read(frame, filled, frame.size - filled)
                    if (read <= 0) {
                        running = false
                        break
                    }
                    filled += read
                }
                if (filled < frame.size) break
                val framePeak = autoGain.apply(frame)
                if (framePeak > peak) peak = framePeak
                if (sending) {
                    while (preroll.isNotEmpty()) {
                        sendFrame(preroll.removeFirst())
                        sent++
                    }
                    sendFrame(frame)
                    sent++
                } else {
                    preroll.addLast(frame)
                    if (preroll.size > PREROLL_FRAMES) preroll.removeFirst()
                }
            }
        } finally {
            runCatching { record.stop() }
            record.release()
            // One line per transmission, not per frame.
            Log.d(TAG, "microphone stopped; $sent frames sent; raw peak $peak of 32768; final gain x${autoGain.gain}")
        }
    }

    private companion object {
        /** 1 s of audio held back while waiting for the floor. */
        const val PREROLL_FRAMES = 25
        const val JOIN_MS = 300L
    }
}

package com.github.devapro.pttdroid.wear

import android.content.Context
import android.net.Uri
import android.util.Log
import com.github.devapro.pttdroid.wear.protocol.ChannelPayload
import com.github.devapro.pttdroid.wear.protocol.WearPaths
import com.github.devapro.pttdroid.wear.protocol.WearState
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * The watch's line to the phone: state in over the Data Layer, button and channel messages out.
 *
 * Outgoing messages go through one queue and one sender coroutine, so "down", the heartbeats and
 * "up" reach the phone in the order they were produced — two concurrent sends could otherwise let
 * "up" overtake "down".
 */
class PhoneLink(context: Context, private val scope: CoroutineScope) {

    private val dataClient = Wearable.getDataClient(context)
    private val messageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)

    private val _state = MutableStateFlow<WearState?>(null)

    /** The phone's latest published state, or null before anything has arrived. */
    val state: StateFlow<WearState?> = _state.asStateFlow()

    private val _phoneConnected = MutableStateFlow(false)

    /** Whether a paired phone is reachable right now. */
    val phoneConnected: StateFlow<Boolean> = _phoneConnected.asStateFlow()

    private data class Outgoing(val path: String, val data: ByteArray)

    private val outbox = Channel<Outgoing>(Channel.UNLIMITED)
    private var pollJob: Job? = null

    private val listener = DataClient.OnDataChangedListener { events ->
        for (event in events) {
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == WearPaths.STATE) {
                WearState.decode(event.dataItem.data)?.let { _state.value = it }
            }
        }
    }

    init {
        scope.launch {
            for (message in outbox) {
                try {
                    val nodes = nodeClient.connectedNodes.await()
                    if (message.path != WearPaths.TALK_HEARTBEAT) {
                        Log.d(TAG, "send ${message.path} to ${nodes.size} node(s)")
                    }
                    for (node in nodes) {
                        messageClient.sendMessage(node.id, message.path, message.data).await()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // The phone is out of range or play services is busy. Nothing to retry: a
                    // dropped "up" is covered by the phone's watchdog, and a dropped "down" is
                    // just a press that did not take.
                    Log.w(TAG, "send ${message.path} failed", e)
                }
            }
        }
    }

    /** Starts listening for state. Call from the Activity's `onStart`. */
    fun start() {
        dataClient.addListener(listener)
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                _phoneConnected.value = try {
                    nodeClient.connectedNodes.await().isNotEmpty()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    false
                }
                // The phone may have published before this listener existed.
                if (_state.value == null) loadLatestState()
                delay(CONNECTION_POLL_MS)
            }
        }
    }

    /** Stops listening. Call from the Activity's `onStop`. */
    fun stop() {
        dataClient.removeListener(listener)
        pollJob?.cancel()
        pollJob = null
    }

    fun send(path: String, data: ByteArray = ByteArray(0)) {
        outbox.trySend(Outgoing(path, data))
    }

    fun setChannel(channel: Int) = send(WearPaths.CHANNEL, ChannelPayload.encode(channel))

    private suspend fun loadLatestState() {
        try {
            val items = dataClient.getDataItems(Uri.parse("wear://*${WearPaths.STATE}")).await()
            try {
                for (item in items) WearState.decode(item.data)?.let { _state.value = it }
            } finally {
                items.release()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // No state yet; the listener will deliver it when the phone publishes.
        }
    }

    private companion object {
        const val TAG = "PhoneLink"
        const val CONNECTION_POLL_MS = 5_000L
    }
}

/**
 * Turns the button gesture into messages — "down", a heartbeat every
 * [WearPaths.HEARTBEAT_INTERVAL_MS] while held, then "up" — and runs the watch microphone for
 * exactly as long as the button is held.
 */
class TalkSender(
    private val link: PhoneLink,
    private val audio: AudioLink,
    private val scope: CoroutineScope,
) {

    private var heartbeat: Job? = null

    fun down() {
        if (heartbeat != null) return
        audio.beginTalk()
        link.send(WearPaths.TALK_DOWN)
        heartbeat = scope.launch {
            while (isActive) {
                delay(WearPaths.HEARTBEAT_INTERVAL_MS)
                link.send(WearPaths.TALK_HEARTBEAT)
            }
        }
    }

    /** Safe to call without a matching [down]; sends nothing then. */
    fun up() {
        val job = heartbeat ?: return
        heartbeat = null
        job.cancel()
        audio.endTalk()
        link.send(WearPaths.TALK_UP)
    }
}

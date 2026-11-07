package com.github.devapro.pttdroid.wear

import android.content.Context
import android.os.SystemClock
import com.github.devapro.pttdroid.domain.PttController
import com.github.devapro.pttdroid.domain.PttState
import com.github.devapro.pttdroid.ui.PttUiStatus
import com.github.devapro.pttdroid.wear.protocol.TalkWatchdog
import com.github.devapro.pttdroid.wear.protocol.WearPaths
import com.github.devapro.pttdroid.wear.protocol.WearState
import com.github.devapro.pttdroid.wear.protocol.WearStatus
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * The phone's end of the watch remote.
 *
 * A thin observer of [PttController], like the widget and the overlay: it publishes the session
 * state to the watch and turns the watch's button into `requestTalk`/`releaseTalk`. It never
 * starts the session — Android 14+ will not start a microphone foreground service from the
 * background, so the user starts the session on the phone once and the watch drives it from
 * there. Anything the watch asks while the session is down is a no-op in the controller.
 *
 * Everything runs on one main-thread scope, because watch messages arrive on a binder thread and
 * the [TalkWatchdog] is not thread-safe; handlers hop onto the scope in arrival order.
 */
class WearBridge(
    private val context: Context,
    private val controller: PttController,
    private val audioHub: WearAudioHub,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val watchdog = TalkWatchdog()
    private var stateJob: Job? = null
    private var watchdogJob: Job? = null
    private var releaseJob: Job? = null

    /** Begins publishing state to the watch. Called by the foreground service. */
    fun start() {
        if (stateJob != null) return
        stateJob = scope.launch {
            controller.state
                .map { it.toWearState() }
                .distinctUntilChanged()
                .collect { publish(it) }
        }
    }

    /** Releases the floor and tells the watch the session is gone. */
    fun stop() {
        stateJob?.cancel()
        stateJob = null
        releaseJob?.cancel()
        release()
        publish(OFFLINE_STATE)
    }

    fun onTalkDown() {
        scope.launch {
            // A new press during the hangover of the last one keeps the floor it already has.
            releaseJob?.cancel()
            releaseJob = null
            audioHub.beginWatchTalk()
            watchdog.arm(clock())
            controller.requestTalk()
            startWatchdog()
        }
    }

    fun onHeartbeat() {
        scope.launch { watchdog.beat(clock()) }
    }

    /**
     * The watch's microphone audio is still in flight over Bluetooth when "button up" arrives,
     * and the two travel on different streams, so the floor is held for a moment longer rather
     * than clipping the end of the last word.
     */
    fun onTalkUp() {
        scope.launch {
            releaseJob?.cancel()
            releaseJob = launch {
                delay(HANGOVER_MS)
                release()
            }
        }
    }

    fun onSetChannel(channel: Int) {
        scope.launch { controller.setChannel(channel) }
    }

    private fun release() {
        watchdog.disarm()
        watchdogJob?.cancel()
        watchdogJob = null
        releaseJob = null
        controller.releaseTalk()
        audioHub.endWatchTalk()
    }

    /** The watch opened one of its audio channels; hands the phone's end of it to the hub. */
    fun onAudioChannelOpened(channel: ChannelClient.Channel) {
        val client = Wearable.getChannelClient(context)
        when (channel.path) {
            WearPaths.AUDIO_DOWN -> client.getOutputStream(channel)
                .addOnSuccessListener(audioHub::attachDown)
                .addOnFailureListener { Timber.d("Watch playback channel failed: %s", it.toString()) }

            WearPaths.AUDIO_UP -> client.getInputStream(channel)
                .addOnSuccessListener(audioHub::attachUp)
                .addOnFailureListener { Timber.d("Watch microphone channel failed: %s", it.toString()) }
        }
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (isActive) {
                delay(WATCHDOG_TICK_MS)
                if (watchdog.shouldRelease(clock())) {
                    Timber.d("Watch went quiet while talking; releasing the floor")
                    release()
                    return@launch
                }
            }
        }
    }

    private fun publish(state: WearState) {
        val request = PutDataRequest.create(WearPaths.STATE)
            .setData(state.encode())
            .setUrgent()
        Wearable.getDataClient(context).putDataItem(request)
            .addOnFailureListener { Timber.d("Could not publish state to the watch: %s", it.toString()) }
    }

    private companion object {
        const val WATCHDOG_TICK_MS = 250L
        const val HANGOVER_MS = 150L
        val OFFLINE_STATE = WearState(WearStatus.OFFLINE, channel = 1, peers = 0, floorHolderName = null, hasError = false)
    }
}

internal fun PttState.toWearState(): WearState = WearState(
    status = PttUiStatus.of(this).toWearStatus(),
    channel = channel,
    peers = peers,
    floorHolderName = floorHolderName.takeIf { isFloorHeldByOther },
    hasError = lastError != null,
)

internal fun PttUiStatus.toWearStatus(): WearStatus = when (this) {
    PttUiStatus.OFFLINE -> WearStatus.OFFLINE
    PttUiStatus.CONNECTING -> WearStatus.CONNECTING
    PttUiStatus.READY -> WearStatus.READY
    PttUiStatus.REQUESTING -> WearStatus.REQUESTING
    PttUiStatus.TRANSMITTING -> WearStatus.TRANSMITTING
    PttUiStatus.RECEIVING -> WearStatus.RECEIVING
}

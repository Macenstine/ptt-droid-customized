package com.github.devapro.pttdroid.wear

import com.github.devapro.pttdroid.wear.protocol.ChannelPayload
import com.github.devapro.pttdroid.wear.protocol.WearPaths
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import org.koin.android.ext.android.inject
import timber.log.Timber

/**
 * Receives the watch's button and channel messages and hands them to [WearBridge].
 *
 * Play services starts this service on demand when a message arrives, so it works while the app
 * is in the background; the bridge itself decides whether anything can happen (it cannot, unless
 * the foreground session is already running).
 */
class WearBridgeService : WearableListenerService() {

    private val bridge: WearBridge by inject()

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        Timber.d("Watch opened channel %s", channel.path)
        bridge.onAudioChannelOpened(channel)
    }

    override fun onMessageReceived(event: MessageEvent) {
        // Heartbeats arrive twice a second; everything else is one line per button event.
        if (event.path != WearPaths.TALK_HEARTBEAT) Timber.d("Watch message %s", event.path)
        when (event.path) {
            WearPaths.TALK_DOWN -> bridge.onTalkDown()
            WearPaths.TALK_HEARTBEAT -> bridge.onHeartbeat()
            WearPaths.TALK_UP -> bridge.onTalkUp()
            WearPaths.CHANNEL -> ChannelPayload.decode(event.data)?.let(bridge::onSetChannel)
        }
    }
}

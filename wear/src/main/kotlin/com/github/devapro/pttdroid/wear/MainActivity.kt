package com.github.devapro.pttdroid.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var link: PhoneLink
    private lateinit var audio: AudioLink
    private lateinit var talk: TalkSender

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A walkie-talkie that sleeps mid-conversation is useless; the screen stays on while the
        // app is in front, and the phone's watchdog covers the app going away.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        link = PhoneLink(applicationContext, lifecycleScope)
        audio = AudioLink(applicationContext, lifecycleScope)
        talk = TalkSender(link, audio, lifecycleScope)

        // The audio link needs to know when the phone says the floor is ours.
        lifecycleScope.launch { link.state.collect(audio::onState) }

        // Declined, the mic simply stays unavailable and the watch can still listen.
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MICROPHONE)
        }

        setContent {
            val state by link.state.collectAsState()
            val phoneConnected by link.phoneConnected.collectAsState()
            PttScreen(
                state = state,
                phoneConnected = phoneConnected,
                onTalkDown = talk::down,
                onTalkUp = talk::up,
                onChannel = link::setChannel,
            )
        }
    }

    override fun onStart() {
        super.onStart()
        link.start()
        audio.start()
    }

    override fun onStop() {
        // Never leave the phone's microphone open because this screen went away mid-press.
        talk.up()
        audio.stop()
        link.stop()
        super.onStop()
    }

    // The watch's stem button as a physical talk key, where the device maps one.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_STEM_1) {
            if (event.repeatCount == 0 && canTalk()) talk.down()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_STEM_1) {
            talk.up()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun canTalk(): Boolean =
        link.phoneConnected.value && link.state.value?.status?.isControlLive == true

    private companion object {
        const val REQUEST_MICROPHONE = 1
    }
}

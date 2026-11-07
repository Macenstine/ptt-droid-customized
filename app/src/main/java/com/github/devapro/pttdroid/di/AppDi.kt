package com.github.devapro.pttdroid.di

import com.github.devapro.pttdroid.audio.VoicePlayer
import com.github.devapro.pttdroid.audio.VoicePlayerContract
import com.github.devapro.pttdroid.audio.VoiceRecorder
import com.github.devapro.pttdroid.audio.VoiceRecorderContract
import com.github.devapro.pttdroid.domain.ForegroundProvider
import com.github.devapro.pttdroid.domain.PttSessionLauncher
import com.github.devapro.pttdroid.domain.ServicePttSessionLauncher
import com.github.devapro.pttdroid.foreground.ActivityForegroundTracker
import com.github.devapro.pttdroid.overlay.OverlayController
import com.github.devapro.pttdroid.wear.RoutingPlayer
import com.github.devapro.pttdroid.wear.RoutingRecorder
import com.github.devapro.pttdroid.wear.WearAudioHub
import com.github.devapro.pttdroid.wear.WearBridge
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * `:app`'s own Koin module — Android-only classes that need a real `Context`, `AudioRecord`/
 * `AudioTrack`, or `PttForegroundService`, none of which `:shared` can see.
 *
 * Everything platform-independent (`PttController`, `SettingsRepository`, the stores, the
 * domain wiring...) lives in `:shared`'s `sharedModule`
 * (`di/SharedDi.kt`); each feature's MVI stack registers itself via `register*ScreenDi()` in
 * its own module; Android's other platform providers (the settings `DataStore`, the on-device
 * relay) live in `:shared`'s `sharedAndroidModule` (`di/SharedDiAndroid.kt`). `PTTdroidApplication`
 * loads all of them together — see that file — so it does not matter that `VoiceRecorderContract`/
 * `VoicePlayerContract` are only bound here while `PttController` (which consumes them) is
 * declared in `sharedModule`: Koin merges every loaded module into one graph.
 */
val appModule = module {
    // Audio goes through the Wear routers: the phone's own recorder/player unless a watch is
    // attached (and, for the microphone, the transmission was started from the watch).
    single { WearAudioHub() }
    single<VoiceRecorderContract> { RoutingRecorder(VoiceRecorder(get(SESSION_SCOPE)), get()) }
    // Needs a Context of its own for AudioManager: the speaker/earpiece choice is half
    // AudioAttributes on the track and half the process's audio mode. See VoicePlayer's KDoc.
    single<VoicePlayerContract> { RoutingPlayer(VoicePlayer(androidContext()), get()) }

    single<PttSessionLauncher> { ServicePttSessionLauncher(androidContext()) }

    // MainActivity's start/stop counts, reported into the tracker — see its KDoc for why a
    // stopped Activity and the overlay/widget do not count as foreground.
    single<ForegroundProvider> { ActivityForegroundTracker }

    // The Wear OS remote: another observer of PttController, started and stopped with the service.
    single { WearBridge(androidContext(), get(), get()) }

    single {
        OverlayController(
            context = androidContext(),
            controller = get(),
            settingsRepository = get(),
            scope = get(SESSION_SCOPE),
        )
    }
}

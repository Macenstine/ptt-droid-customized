# Wear OS companion (thin remote)

A Wear OS watch can drive the phone app's PTT session. The watch is a remote: it shows the
session state, sends the hold-to-talk button, and supplies its own microphone and speaker. The
phone keeps the relay connection and owns `PttController`.

| Piece | Where |
|---|---|
| Wire format: paths, `WearState`, `WearStatus`, `TalkWatchdog`, `AutoGain` | `:wear-protocol` (pure Kotlin) |
| Phone side: `WearBridge`, `WearBridgeService`, `WearAudioHub`, `RoutingRecorder`/`RoutingPlayer` | `:app`, package `wear` |
| Watch app: `PttScreen`, `PhoneLink`, `AudioLink` | `:wear` |

## Control flow

1. The user starts the session on the phone (Android 14+ will not start a microphone foreground
   service from the background, so the watch cannot start it).
2. `PttForegroundService` starts `WearBridge`, which publishes `PttUiStatus`-derived state to the
   Data Layer (`/ptt/state`).
3. The watch button sends `/ptt/talk/down`, then `/ptt/talk/heartbeat` every 500 ms, then
   `/ptt/talk/up`. `WearBridge` maps these onto `PttController.requestTalk()` / `releaseTalk()`.
4. If the heartbeat stops for 1.5 s, or a transmission passes 60 s, `TalkWatchdog` makes the phone
   release the floor — a lost "up" cannot strand the phone's microphone.
5. On "up" the phone holds the floor 150 ms longer, so the tail of the audio still in flight on the
   other stream is not clipped.

## Audio

Two `ChannelClient` streams, opened by the watch while its app is in front and reopened if they
drop. Both carry the relay's own format — PCM16LE, 16 kHz, mono, 1280-byte (40 ms) frames — so
nothing is converted (`WearAudioFormat`, kept equal to `AudioConfig` by a test).

- `/ptt/audio/down` (phone → watch): `RoutingPlayer` hands received frames to `WearAudioHub`
  while the watch is listening; otherwise they play on the phone's speaker as before.
- `/ptt/audio/up` (watch → phone): the watch starts capturing on the button press, holds back about
  1 s of audio until the phone reports the floor is ours, then sends. `RoutingRecorder` takes these
  frames only for a transmission started from the watch; a press on the phone keeps the phone mic.
- The watch applies `AutoGain` (target peak 20000 of 32768, up to 20x, never boosting noise below a
  floor), because watch microphones capture very quietly.

Both directions drop the oldest frame when behind: this is a live voice path.

## Limits

- Audio plays on the watch only while its app is in front; some watches freeze the app with the
  screen off, and nothing here runs a watch-side foreground service yet.
- The watch's `RECORD_AUDIO` permission is requested at launch.
- Samsung watches' two buttons are Home and Back, so the stem-button talk key
  (`KEYCODE_STEM_1`) is probably unavailable there.
- Latency over Bluetooth is not measured.

## Setup constraints

- The watch app's `applicationId` equals the phone app's, and both must be signed with the same
  key, or the Data Layer will not pair them. Debug builds from one machine share the debug key, and
  both debug variants carry the `.dev` suffix, so they install beside a release copy.
- `WearBridge` is only a controller observer; it never starts the session.
- Colours on the watch repeat `PttUiStatus`; `WearStatusMappingTest` keeps them equal.
- `:app` now depends on `play-services-wearable`, which is not an OSI-licensed Maven Central
  dependency: this build cannot go to the official F-Droid catalogue as it is.

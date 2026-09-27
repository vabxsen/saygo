# saygo 0.9.0: native system controls

Version code 16; Android 10+ (minimum API29), target/compile API36. This release adds six explicit device actions. These are individual commands, without autonomous planning.

| Command | Behavior |
|---|---|
| `Open notifications` / `Show notifications` | Request Android’s notification shade. |
| `Open quick settings` / `Show quick settings` | Request Android’s expanded device controls. |
| `Volume up` / `Media volume up` | Raise media volume by one step. |
| `Volume down` / `Media volume down` | Lower media volume by one step. |
| `Mute media` | Mute media; repeating it leaves media muted. |
| `Unmute media` | Unmute media; repeating it leaves media unmuted. |

These commands are listed in the in-app guide and accessibility disclosure. They use the existing optional phone-controls service, wait for the voice panel to close, check consent again, and reject locked or non-interactive screens. They are device-wide actions and do not require an originating app. A queued command can be cancelled with the existing Stop/Cancel path. Existing app-target checks still apply to taps, edits, gestures and Back.

Panel actions use Android’s [AccessibilityService global actions](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService). Media commands use [AudioManager](https://developer.android.com/reference/android/media/AudioManager#adjustStreamVolume(int,%20int,%20int)) for the music/media stream and display Android’s volume UI. The manifest declares the normal `MODIFY_AUDIO_SETTINGS` permission. No notification-listener or Do Not Disturb access is requested; ring and alarm streams are not selected. Device policy or audio routing can still reject or alter the result. Fixed-volume devices receive an unavailable message, and attempts beyond the current media limits report that limit. Feedback says “requested” for submitted actions, rather than asserting an audible result.

## Native observations

On the owned API36 emulator, the production parser/executor opened the actual SystemUI notification shade. The subsequent Quick Settings command expanded the panel, exposing device controls and the brightness slider. The opt-in ExplicitCommandProbe supplied literal commands and bypassed speech input. [Sanitized panel metadata](system-control-evidence/observations.json) and probe logs are saved alongside this report; no notification text is published.

## Verification

**88 local tests passed: 65 Android instrumentation tests on API36 and 23 JVM tests**, with zero skips/failures in the passing run. Debug/test builds, lint and the unsigned release bundle build passed. Lint reported zero errors and 10 existing advisory warnings. Debug APK signature verification (v2) and 16 KB ZIP alignment checks passed. [Saved logs, reports and sanitized observations](system-control-evidence/) record this run. The test suite checks native panel visibility, actual AudioManager volume/mute state, one-step changes, unchanged ring/alarm volume, repeated mute/unmute, min/max limits, waiting for the voice panel, cancellation and consent withdrawal. It also verifies the new command-guide rows remain reachable. Volume tests restore previous media settings in finally blocks; panel tests collapse the system shade afterward.

## Remaining limits

This expands the supported command set; it does not make the app capable of every phone task. Live microphone recognition, actual Instagram Reels and Google Play approval remain unverified. Bluetooth/cast/fixed-volume hardware and audible output were not checked on the emulator. Opening Quick Settings does not itself toggle Wi-Fi, Bluetooth or other tiles: a subsequent explicit tap is a separate action. “Mute media” also affects spoken feedback if the selected TTS engine uses the media stream. Production signing, publisher details and Play submission remain outstanding.

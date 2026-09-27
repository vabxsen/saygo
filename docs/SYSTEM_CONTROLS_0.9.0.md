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

## Hosted Quick Settings check

The initial [hosted run](https://github.com/vabxsen/saygo/actions/runs/36349496070) passed build and all 62 tests on Android 10/14. On Android 16, 61 tests passed and the Quick Settings visibility assertion timed out after successful dispatch. The [original failure](system-control-evidence/initial-api36-failure.json) is preserved; that run is not counted as a compatibility pass. The native panel was visible on both the existing local Pixel profile and a separate fresh 320dp-wide profile. The local image differs from the hosted Google APIs image, so this did not conclusively reproduce its cause.

The panel test now waits for the launched fixture to become focused and for accessibility events to settle before sending the command. It retains the same native panel ID/visibility assertion, adds window/resource metadata to any failure, and does not retry or skip a failed action. Production behavior and the 0.9.0 APK are unchanged by this test refinement.

Both refined panel tests [passed on the separate fresh 320×640, density-160 API36 emulator](system-control-evidence/device-panel-refinement-tests.txt). The test build also passed.

The focused-fixture refinement did not resolve the hosted API36 failure. The [0.9.1 follow-up](QUICK_SETTINGS_0.9.1.md) records the cold-boot reproduction and production fix.

# saygo 0.8.0: actual YouTube Shorts controls

The existing production commands successfully opened, paused, resumed and changed videos in the installed YouTube app. No production behavior changed. This is a deterministic-command check on the owned Android 16/API36 emulator, using YouTube 21.38.130. It does not test speech input or establish that all apps/screens can be controlled.

## Observed sequence

| Explicit command | Visible result |
|---|---|
| `Search YouTube for cooking` | YouTube showed the requested query and actual Short results below a sponsored result. |
| `Show grid`, then `Tap cell 7` | Opened the observed non-ad pasta Short by @CookTubeOfficial. The cell was selected from the current layout, not assumed to work on every screen. |
| `Tap Pause video` | Player control changed to “Play video”; two separate UI captures held at 0:00 of 0:17. |
| `Tap Play video` | Control changed back to “Pause video”; elapsed time advanced to 0:08. |
| `Next video` | The upward swipe changed the player to a different bread-bites Short by @ganeshshricooking, at 0:07 of 0:31. |
| `Previous video` | The downward swipe returned to the original pasta Short, at 0:07 of 0:17. |

[Sanitized UI observations](youtube-control-evidence/observations.json) retain titles, channel labels, player controls, elapsed time and capture timestamps. [Probe logs](youtube-control-evidence/) record parsed commands and production feedback. Dispatch success is checked separately from visible outcomes. Android's media-session dump reported `NONE` for this Shorts player, so media-session status alone was not used as playback evidence. The emulator ran with host audio disabled; audible output was not checked. Ads, product links, likes, subscriptions and sharing were not activated.

## Reproduce on a dedicated emulator

`ExplicitCommandProbe` is an opt-in instrumentation runner, separate from JUnit. It parses every explicitly supplied line before executing any, runs each command through the production executor, and waits six seconds between commands. It temporarily enables saygo's accessibility service, grants test control consent and hides the floating bubble; previous accessibility settings and preferences are restored afterward. This is test harness setup, not the app's user onboarding. It does not request microphone access, collect screen contents, choose commands, or assert the receiving app's result.

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest -PexplicitCommandProbe=true
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
$commands = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes('search youtube for cooking'))
adb shell am instrument -w -e commandsBase64 $commands dev.saygo.app.test/dev.saygo.app.ExplicitCommandProbe
```

Require `dispatchPassed=true` and `INSTRUMENTATION_CODE: -1`; inspect the resulting screen independently. Resolve app onboarding manually first and inspect the current layout before choosing a grid cell. Multiple explicitly chosen commands can be separated with a newline before Base64 encoding. Use only a dedicated test device: supplied commands may change the receiving app. The runner stops on the first failed dispatch and accepts at most ten commands.

Rebuild/reinstall the test APK without the property to restore AndroidJUnitRunner. Supplying both manual-probe flags is rejected. Neither probe is included in the production APK or run by normal JUnit/CI discovery.

## Validation and limits

Default debug/test builds, 21 JVM tests and lint passed (zero errors, 10 existing warnings). The restored default runner passed all seven CommandExecutorTest cases. Production APK SHA-256 remains `14b7e56b708a77caef038b6af91d6a9fdd7336f229306696a949d89ba23aade4`, identical to the existing 0.8.0 artifact. Previous full local and Android 10/14/16 checks remain documented in the [speech audit](SPEECH_APP_AUDIT_0.8.0.md); they are not claimed as newly rerun here.

Hosted regression follow-up: [build and Android 10/14/16 checks](https://github.com/vabxsen/saygo/actions/runs/36348141978) passed for `781fb87a9544c68ebb10290baad5363e3b53615e`. Each emulator ran **56 core tests**, with zero failures, errors or skips. [Downloaded report summary](youtube-control-evidence/hosted-results.json). These jobs exclude the three actual-app routing tests and do not invoke either manual probe.

This check bypasses SpeechSession and VoiceActivity. Live microphone recognition and the complete spoken-command flow remain unverified. Instagram is absent from the emulator, Google search results previously hit a traffic challenge, and Play signing/submission/approval remain outstanding. Individual deterministic commands remain the product scope; unrestricted “anything” control is not established.

## Android 14 fixture failure

The first hosted run of this follow-up passed build and Android 10/16, but Android 14 had [three failures among 56 tests](youtube-control-evidence/initial-api34-failures.json). All three stopped in `openSettings()` before sending the command under test: UiAutomation saw Settings, while the bound production service returned a null active-window root. This does not establish a command-dispatch defect, and the run is not counted as passing.

The test setup now force-stops Settings before launching it and waits for accessibility events to settle. This isolates the target window across tests that repeatedly disconnect/reconnect the service. The existing service-root assertion and timeout remain; no test is skipped or retried and production behavior is unchanged. The subsequent hosted run passed all 56 tests on API29/34/36, including the three previously failing cases; the original failure evidence is retained above.

With the isolated Settings setup, [all 37 local PhoneControlServiceTest cases passed](youtube-control-evidence/settings-fixture-instrumentation.txt) on API36; [test APK build passed](youtube-control-evidence/settings-fixture-build.log).

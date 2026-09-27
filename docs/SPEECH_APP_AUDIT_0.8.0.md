# saygo 0.8.0: speech-provider and real-app audit

The production app is unchanged. This audit adds a separately invoked synthetic-audio probe and stronger evidence about the receiving apps. It does not establish universal phone control.

## What was observed

| Check | Evidence | Result and limit |
|---|---|---|
| Actual saygo microphone UI | VoiceActivity displayed “Listening…” and the on-device provider opened a session | Provider startup verified; no recognized microphone command verified |
| “Open YouTube” | Real on-device ASR returned the expected text; production parser/executor reported opening YouTube | Passed synthetic-input probe; first launch showed YouTube's notification permission prompt |
| “Search Google for Android accessibility” | Real ASR returned the expected text; Chrome received the query | Initial Chrome onboarding was completed without an account. Google then redirected to a traffic challenge. Search results remain unverified |
| “Search YouTube for cooking” | Real ASR returned the expected text; the actual YouTube app displayed “cooking” and non-ad video results | Search display verified; video playback and feed transitions were not tested |
| “Cancel” | Real ASR returned the expected text; the executor reported cancellation | Passed synthetic-input cancellation probe |
| Instagram/Reels | Package lookup returned no installed Instagram APK | Not tested; no third-party APK or account was introduced |

All four synthetic-input cases passed on the owned Android 16/API36 emulator. The saved [probe transcripts](speech-app-evidence/) and [sanitized observations](speech-app-evidence/observations.json) distinguish recognized commands, successful dispatch and visible content. Raw third-party UI dumps and Google challenge identifiers are not published.

## Scope of the speech probe

The optional `SyntheticSpeechProbe` feeds generated Microsoft Zira Desktop speech through a 16 kHz, mono, signed 16-bit PCM pipe to Android's real on-device SpeechRecognizer. It uses [audio-source and segmented-session APIs](https://developer.android.com/reference/android/speech/RecognizerIntent#EXTRA_AUDIO_SOURCE), then checks the completed transcript against an expected command before passing it to the production parser and executor. It does not inject recognition callbacks or substitute a mock recognizer. The PCM fixtures, their phrases, format and hashes are under `app/src/androidTest/assets/synthetic-speech/`.

This path bypasses the production SpeechSession microphone request and VoiceActivity lifecycle. Passing it is evidence about these synthetic utterances and this installed provider, not proof of live microphone capture, accents, background noise, offline model availability on other devices, or speech recognition on Android 10/14. Android's external-audio API requires Android 13+; the normal app still supports Android 10+.

Direct file input and a non-segmented pipe produced empty final bundles in this provider. Segmented-session mode returned the actual transcriptions. Earlier failed probe results are retained. The final test waits for the segmented session to end before executing anything; partial segments alone do not trigger commands.

The SDK emulator microphone-injection route was also attempted with host-microphone input disabled: disabling emulator audio caused termination, and audio-enabled attempts stalled or terminated during injection, including paced input after the provider was ready. These are test-infrastructure failures, not established saygo defects. No live-microphone pass is claimed. Emulator version: 37.1.11.0 (build 15917651), Windows host.

## Reproduce on a dedicated emulator

Use an Android 13+ emulator with a working speech provider and the receiving apps installed. This launches apps and submits the fixture search queries. The probe is deliberately separate from the regular JUnit/CI runner.

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest -PsyntheticSpeechProbe=true
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell pm grant dev.saygo.app android.permission.RECORD_AUDIO
adb shell am instrument -w -e case open-youtube dev.saygo.app.test/dev.saygo.app.SyntheticSpeechProbe
adb shell am instrument -w -e case google-search dev.saygo.app.test/dev.saygo.app.SyntheticSpeechProbe
adb shell am instrument -w -e case youtube-search dev.saygo.app.test/dev.saygo.app.SyntheticSpeechProbe
adb shell am instrument -w -e case cancel dev.saygo.app.test/dev.saygo.app.SyntheticSpeechProbe
```

Require `passed=true` and `INSTRUMENTATION_CODE: -1` for each case; a shell exit code alone is insufficient. Restore the previous microphone grant afterward. Rebuild/reinstall `assembleDebugAndroidTest` without the property to restore AndroidJUnitRunner. No probe class or synthetic audio fixture is included in the production APK.

## Regression checks and remaining work

The normal runner was restored and all **59 Android instrumentation tests and 21 JVM tests passed**. Default debug/test builds and lint passed with zero errors and 10 existing advisory warnings. The debug APK is byte-for-byte identical to the published 0.8.0 APK. Evidence: [Android run](speech-app-evidence/speech-default-instrumentation.txt), [build](speech-app-evidence/speech-default-build.log), and XML reports in the evidence folder.

The [hosted run](https://github.com/vabxsen/saygo/actions/runs/36345983281) also passed build and **56 core tests on each of Android 10, 14 and 16**, with no failures, errors or skips. Tested commit: `bf71451b15366fd534ad542e141ec8cdfd905806`. [Detailed reports](speech-app-evidence/hosted-results.json) confirm the optional probe does not alter the default suite. These hosted jobs do not invoke synthetic recognition and exclude the three Chrome/YouTube routing tests run locally.

Live microphone recognition, actual Instagram Reels, playback/feed transitions in the named apps, and other-device behavior remain unverified. Google search results were blocked by Google's traffic challenge; it was not bypassed. Google Play submission/approval, signing and publisher-specific details also remain outstanding. The original “anything” requirement remains unproven.

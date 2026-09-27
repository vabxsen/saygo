# Functional audit — saygo 0.4.1

**Current status:** The three rescan findings below are resolved in version 0.4.2. See [FIXES_0.4.2.md](FIXES_0.4.2.md) for the implementation and passing regression evidence. This document preserves the earlier audit state.

**Follow-up rescan:** This earlier audit is retained as historical evidence. Three additional edge-case issues were found afterward; see [RESCAN_REPORT.md](RESCAN_REPORT.md). Its expanded findings supersede the no-remaining-failure conclusion below.

Date: 2026-09-27. Version code: 10. Application ID: dev.saygo.app.

## Result

The checked buttons, permission flows, command routing and phone-control operations pass on the Android 16 / API 36 x86_64 emulator. The user explicitly chose emulator-only verification. This is not a claim that live speech recognition, audible output, Instagram Reels, or every supported Android device has been validated.

Final automated results: **32 Android instrumentation tests and 13 JVM unit tests passed**, with no failures. Debug APK, unsigned release AAB, debug lint and release vital lint passed. Debug lint reports 0 errors and 10 existing advisory warnings. APK signature and 16 KB ZIP alignment were verified.

## Issues fixed

1. **Permanent microphone denial had no recovery route.** On 0.4.0, repeated taps with Android's USER_FIXED denial flag stayed on Home. 0.4.1 remembers that permission was requested and opens Android app settings when another permission prompt is unavailable. Verified by before/after Android UI captures.
2. **Queued phone actions could outlive consent.** Consent was checked when an action was queued, but not when the delayed action ran. The service now checks again immediately before dispatch. A regression test queues navigation, withdraws consent, then releases the voice panel; the action is rejected.
3. **Spoken-feedback cancellation missed pending initialization.** A result could start speaking after a new listening session began, and an older result could win over a newer queued result. A small queue now cancels pending output, keeps only the latest result, checks the current preference, and stops output when the toggle is turned off. Initialization failures are handled after construction completes. Six unit tests cover queue readiness, cancellation, disabled feedback, failure recovery and latest-result behavior.

Android documents that a text-to-speech initialization failure can call its listener before construction finishes: [TextToSpeech API reference](https://developer.android.com/reference/android/speech/tts/TextToSpeech). The initialization callback is therefore deferred to the main message queue.

## Button and flow inventory

| Control or flow | Result and evidence |
|---|---|
| Header settings gear | Pass: opens Setup; Home returns to primary screen (UI test). |
| Home / Commands / Setup navigation | Pass: destinations and return paths tested. |
| Home microphone | Pass: disclosure, Android permission prompt and actual listening UI exercised. |
| Open YouTube example | Pass: opens the command guide, not an immediate app launch (intended behavior). |
| Google search example | Pass: opens the guide; all documented command rows remain reachable. |
| Command-guide scrolling | Pass: all app, search, swipe, navigation and cancellation examples checked. |
| Microphone disclosure — Not now | Pass: closes dialog without recording consent. |
| Microphone disclosure — Agree & continue | Pass: Android permission prompt opens on a fresh install. |
| Android microphone grant | Pass: accepting While using the app starts listening. |
| Permanent microphone denial | Pass after fix: a subsequent attempt opens app settings. |
| Setup microphone / App settings | Pass: Android app settings opens from the enabled row. |
| Phone-controls Enable | Pass: disclosure opens; acceptance reaches Android accessibility settings. |
| Phone-controls disclosure — Not now | Pass: no consent is granted by dismissal. |
| Phone-controls disclosure — Agree & open settings | Pass: Android confirmation can enable the service and show its bubble. |
| Review access | Pass: reopens the disclosure; Not now dismisses it. |
| Floating microphone preference | Pass: row toggles and persists across Activity recreation; actual bubble show/hide also tested. |
| Spoken feedback preference | Pass: state persists; switching off stops/cancels pending feedback. Audible output was not assessed. |
| Privacy / Got it | Pass: privacy dialog opens and dismisses. |
| Turn off phone controls | Pass: Android service is disabled and Setup returns to Enable. |
| Floating microphone drag | Pass: actual touch injection moves the bubble without opening the voice panel. |
| Floating microphone tap | Pass: opens listening over Android Settings. |
| Listening close button | Pass: cancels and returns to the originating app. |
| Android Back while listening | Pass: returns Home and closes the listening UI. |
| Silence / timeout | Pass: actual recognition session reaches a bounded retry state. |
| Try again | Pass: starts a new session and displays Listening again. |

## Supported command and engine checks

| Feature | Result and test scope |
|---|---|
| Open installed app | Pass: real YouTube launcher invoked and YouTube UI observed; exact launcher resolution also tested. |
| App not installed | Pass: useful error and no unrelated launch. Instagram is not installed in this emulator. |
| Google search | Pass: correct HTTPS endpoint and encoded query; real Chrome routing tested. |
| YouTube search | Pass: correct HTTPS endpoint and encoded query; real YouTube/browser routing tested. |
| Search punctuation / Unicode / query injection | Pass: query stays data and cannot replace host or add URL parameters. |
| Next / previous reel and horizontal swipes | Pass: parser aliases verified; Android accepted/completed each of four actual gesture directions. Target-specific Instagram playback is unverified. |
| Back / Home / Recents | Pass: actual accessibility global actions dispatched; Home reached the launcher. |
| Wrong foreground app | Pass: stale-target swipe rejected. |
| Scrolling from saygo or without an origin | Pass: instructs user to use the floating microphone over the target app. |
| Pending action interruption / consent withdrawal | Pass: queued work cancelled or rejected. |
| Voice panel does not close | Pass: bounded wait ends with a useful error instead of acting. |
| Service shutdown | Pass: connection state clears and bubble disappears. |
| Cancel / stop command | Pass: no activity is launched. |
| Unsupported or chained instructions | Pass: finite command grammar rejects unsupported actions. |
| Speech callback ordering | Pass: one result at most; late callbacks after result/cancel cannot execute another command. |
| Speech errors | Pass at callback level: no match, timeout, permission, network, busy provider, missing English and audio errors have actionable messages. These are not all live provider failure simulations. |
| Spoken-feedback lifecycle | Pass at queue/unit level: 6 tests; actual acoustic output is unverified. |

The microphone icon inside the listening sheet is a status graphic; Cancel and Try again are its controls. Command guide entries are explanatory text. Home example rows intentionally navigate to the guide.

## Test reliability and evidence

Final device suite: 6 executor + 3 actual routing + 11 accessibility-service + 7 UI + 5 speech-callback tests = 32. Unit suites: 7 parser + 6 feedback-queue tests = 13.

Evidence is saved in audit-evidence/: final logs, unit XML reports, permission before/after captures, listening/retry/cancel captures, service setup, and connected/disabled controls.

YouTube's initial notification prompt was an external-app first-run state, not a failed saygo launch. Routing tests handle that prompt and stop YouTube/Chrome between cases to prevent delayed redirects from contaminating the next test. The final wait helper records the successful window observation once instead of re-querying a transitioning window after success.

Android's standalone UI dumper temporarily suppresses accessibility services. Connected-state checks therefore used live screenshots, and service tests use UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES. A transient disconnected state caused by the dumper was not counted as an app defect.

## Explicit limits

- Live English speech accuracy and microphone hardware quality were not tested with human speech.
- Audible text-to-speech playback was not assessed; the emulator ran without audio output.
- Instagram is absent. Reel progression inside Instagram and real third-party media playback remain unverified.
- Browser search dispatch and query correctness passed; internet search result rendering and external app onboarding are outside the asserted result.
- This run used Android 16 only. Physical Android 10/14, manufacturer differences, phone-call interruption, lock-screen behavior and a full TalkBack pass remain release-device checks.
- Arbitrary tapping/typing, multi-step automation, continuous listening and automatically navigating into Instagram Reels are not implemented features. The guide already states that limit.

No remaining failure was found in the checked emulator scope. The APK remains a development-signed test build; the AAB is unsigned.

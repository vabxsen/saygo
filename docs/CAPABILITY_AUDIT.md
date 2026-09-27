# Capability audit against the original requirement

Date: 2026-09-27. App: saygo 0.4.2. Audited app source: commit 3b841db; CI repair: 36b7b3a.

## Verdict

The requirement to do anything a person can do on their phone is **not met**. The current implementation is a finite voice-command app. Its passing regression tests establish specific behavior, not unrestricted phone control. Fixing the three previous defects did not implement missing command categories.

## Requirement-to-evidence matrix

| Requirement | Current implementation and evidence | Result |
|---|---|---|
| Understand English speech | SpeechSession requests en-US and handles one result, cancellation, errors and timeout. SpeechSessionTest injects callbacks; it does not feed actual audio into a recognition provider. | Implemented; actual recognition accuracy unverified |
| Open installed apps | CommandExecutor resolves launcher labels and known Instagram/YouTube aliases. Executor tests inspect intents; CommandRoutingTest checks a real receiving package for YouTube. | Covered within those bounds; Instagram launch not runtime verified |
| Google search | Encoded query in an HTTPS intent. Executor tests inspect query/host and routing tests confirm browser foreground. | Routing covered; network results/content not verified |
| YouTube search | Encoded query in a YouTube URL. Routing test accepts YouTube or browser as receiver. | Routing covered; exact displayed search results not verified |
| Scroll reels | Next reel maps to a single upward swipe. A separate test activity records swipe receipt. The app does not identify Instagram's Reels screen or navigate there. | Generic gesture verified; actual Instagram Reels unverified |
| Back, Home, Recents | Service dispatches global navigation; Back checks the original foreground package. | Regression coverage exists |
| Tap arbitrary controls / long press | No command variants, parser branches or executor for these operations. Service reads package/bounds, not a target control tree. | Not implemented |
| Type/dictate in another app | No text-editing command or ACTION_SET_TEXT implementation. | Not implemented |
| Messages, calls, alarms, files, media controls, settings changes | No dedicated commands or integrations for these tasks. Opening an app does not perform its internal tasks. | Not implemented |
| Multi-step requests | Parser rejects action chains; no planner or sequencer exists. | Not implemented |
| Continuous listening / wake word | SpeechSession is user initiated, bounded to 15 seconds, and does not restart itself. | Not implemented |
| Android 10+ on all phones | minSdk29 permits installation. Local saved runtime results are API36 only. A hosted API29/34/36 matrix is running after the SDK setup repair. | Broad compatibility not yet established; no all-device guarantee |
| Google Play distribution | No signed production bundle or completed Play review. Accessibility declaration, final privacy details and store submission remain outstanding. | Not released |

## Existing verification and its limits

Saved evidence in `fix-evidence/` records 38 instrumentation tests on API36 and 14 JVM tests, with passing debug/release builds and lint (0 errors, 10 advisory warnings). The saved instrumentation log and unit XML were re-read for this audit. No physical device is connected; the user's emulator-only preference remains in effect.

The first hosted run, [36328140040](https://github.com/vabxsen/saygo/actions/runs/36328140040), failed all four jobs in SDK setup before app compilation/testing: setup-android's default package list requested the unavailable legacy `tools` package. Commit 36b7b3a explicitly requests `platform-tools` in both jobs. The replacement run is [36331987438](https://github.com/vabxsen/saygo/actions/runs/36331987438). All four jobs passed SDK setup and reached build/instrumentation; the final run passed its build and all three core emulator jobs on API 29, 34 and 36. This applies to the unchanged 0.4.2 app source, not the subsequent 0.5.0 features.

CI excludes three CommandRoutingTest tests because its images do not supply the required actual YouTube/Chrome setup. The remaining emulator tests must not be described as full third-party app or live-speech verification.

## Constraints on the broader product

Android accessibility APIs can support more explicit user-directed actions, including clicking exposed controls and editing supported fields. Their availability depends on the target UI exposing usable nodes/actions. These are additional features to implement and verify, not features already present. See [Android accessibility service documentation](https://developer.android.com/guide/topics/ui/accessibility/service).

Google Play permits deterministic, human-defined automation for narrow, understood purposes. Its policy prohibits general assistants from using Accessibility APIs to autonomously initiate, plan and execute actions; the stated exception applies to verified accessibility tools whose core purpose assists people with disabilities. saygo currently declares `isAccessibilityTool=false`, consistent with its general-assistant audience. A different declaration alone would not qualify it. See [Google Play's AccessibilityService policy](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en).

Consequently, an unrestricted promise of “anything” is not a verified or supportable acceptance criterion for the current app. This audit leaves that original requirement open rather than treating the existing smaller command set as completion. Further work needs concrete task coverage and separate implementation and runtime evidence for each supported task.

## Follow-up: explicit commands selected

The user selected individual voice commands with Google Play support. Version 0.5.0 adds exact-label tap/long-press and focused-field insert/replace/clear/select-all commands. The table above records the audited 0.4.2 baseline; it must not be mistaken for a description of the current implementation. The new controls passed local runtime verification; see [0.5.0 results](DIRECT_CONTROLS_0.5.0.md). Arbitrary whole-task planning remains outside the selected direction; the original broad coverage requirement is still unproven.

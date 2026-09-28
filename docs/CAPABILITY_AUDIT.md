# Capability audit against the original requirement

## Current assessment: 0.9.2

The original “anything a person can do on their phone” requirement remains **unmet and unproven**. The user selected individual deterministic commands with Google Play support. The app now covers a wider set of those commands, but passing tests must not be treated as universal task coverage.

| Requirement | Authoritative evidence and current limit | Assessment |
|---|---|---|
| English speech | Four real-provider synthetic-audio probes passed in 0.8.0. Production microphone UI started the provider, but no microphone command was recognized in the emulator checks. [Speech audit](SPEECH_APP_AUDIT_0.8.0.md) | Synthetic recognition verified; complete live spoken flow unverified |
| Open installed apps | Exact launcher-label/known-alias resolution; current executor and actual YouTube routing tests pass. Instagram is absent from the owned emulator. | Implemented and bounded; Instagram launch unverified |
| Google search | Encoded query routing passes. Chrome received the requested query, but Google displayed a traffic challenge. [Receiving-app evidence](SPEECH_APP_AUDIT_0.8.0.md) | Search routing verified; results blocked/unverified |
| YouTube search, playback and next/previous | Actual search results, opening a Short, pause/resume and next/previous clips were observed through explicit production commands in 0.8.0. [YouTube audit](YOUTUBE_CONTROL_0.8.0.md). Current regression tests cover the retained command paths. | Those observed flows verified; no universal YouTube task claim |
| Instagram Reels | Single upward/downward gestures are delivered to a separate native target app in current tests. Actual Instagram is not installed. | Generic swipe verified; Reels unverified |
| Tap, long press, typing and selection | Current native target-app tests verify unique labels, clickable ancestors, focused text edits, ambiguity/disabled-target rejection and literal dictation. [Direct controls](DIRECT_CONTROLS_0.5.0.md) | Verified within exposed accessibility-node limits; password fields excluded |
| Unlabelled controls, pinch and drag | Current tests verify grid coordinates, native scaling, hold timing, drag/drop and cancellation in separate fixture activities. [Grid](GRID_0.6.0.md), [pinch](PINCH_0.7.0.md), [drag](DRAG_0.8.0.md) | Verified fixture behavior; target apps must support the chosen interaction |
| System navigation and device controls | Back/Home/Recents plus explicit notifications, Quick Settings and media-volume commands. Current native tests inspect visible panels and actual audio state. [0.9.0 controls](SYSTEM_CONTROLS_0.9.0.md), [0.9.2 follow-up](QUICK_SETTINGS_0.9.2.md) | Supported commands verified within the recorded device/test scope |
| Calls, messages, alarms, files and arbitrary app workflows | Generic individual controls may be used inside compatible apps, but no complete acceptance matrix verifies every such task. There are no dedicated task planners or integrations for these categories. | Broad task coverage incomplete |
| Continuous listening / wake word | Production listening is tap-initiated, one result per session, bounded to 15 seconds. | Always-on listening and wake word not implemented |
| Android 10+ on all phones | Packaged minSdk29/target36 verified; per-version emulator reports are linked from the current verification page. OEM variations, physical devices and all-device behavior are not established by the emulator matrix. | Supported minimum verified; universal compatibility unproven |
| Google Play and GitHub distribution | Source is committed/pushed to the requested GitHub repository. Debug APK and unsigned release AAB are built. Publisher-specific privacy details, production signing, Play submission and review remain outstanding. | GitHub delivered; Google Play release incomplete |
| Security and platform limits | Locked/non-interactive screen guards, consent rechecks, target validation and cancellation remain. Secure/password fields and Android restrictions are not bypassed. | Deliberate boundaries; these prevent an unrestricted “anything” promise |

The [system-control audit](SYSTEM_CONTROLS_0.9.0.md) and [0.9.2 follow-up](QUICK_SETTINGS_0.9.2.md) record validation and hosted failures/follow-ups. Earlier evidence is explicitly versioned. Live microphone input, actual Instagram/Reels, Google search results, broader task coverage and Play distribution still require additional evidence or external setup. The goal must remain open.

## Historical baseline and follow-ups

The sections below preserve the original 0.4.2 audit and subsequent additions. Its initial implementation column is historical, not the current feature list.


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

## Follow-up: grid and pinch controls

Version 0.6.0 added a numbered grid for unlabelled tap targets, with 47 core tests passing on each of Android 10, 14 and 16. Version 0.7.0 adds explicit native pinch zoom and verifies real scaling in a separate app, including offset windows. See [grid results](GRID_0.6.0.md) and [pinch results](PINCH_0.7.0.md). These additions do not establish universal phone control or Google Play approval.

## Follow-up: hold-and-drag

Version 0.8.0 adds an explicit drag between two different numbered grid cells and fixes cancellation of queued input. Native drag-and-drop and pointer timing are verified in a separate test app. See [drag results](DRAG_0.8.0.md). Arbitrary phone tasks and Play approval remain unverified.

## Follow-up: actual speech provider and receiving apps

Four synthetic PCM utterances passed through the real on-device recognizer and production command parser/executor on API36. YouTube displayed cooking search results. Chrome initially required onboarding, then Google presented a traffic challenge. Microphone capture and Instagram remain unverified; the file-input probe bypasses the production microphone session. See [speech and app audit](SPEECH_APP_AUDIT_0.8.0.md).

## Follow-up: native system controls

Version 0.9.0 adds explicit commands to open notifications or Quick Settings and raise, lower, mute or unmute media volume. These operate on the device after the usual consent, voice-panel and unlocked-screen checks. See [system-control verification](SYSTEM_CONTROLS_0.9.0.md). This expands individual command coverage; it does not establish universal phone control, always-on speech or Play approval.

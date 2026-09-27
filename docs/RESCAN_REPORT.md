# saygo 0.4.1 — full source rescan

**Current status:** The three rescan findings below are resolved in version 0.4.2. See [FIXES_0.4.2.md](FIXES_0.4.2.md) for the implementation and passing regression evidence. This document preserves the earlier audit state.

Date: 2026-09-27. Scope: all application Kotlin sources, resources, manifests, build configuration, tests, GitHub workflow, and project documentation; targeted Android 16/API 36 emulator diagnostics. The user chose emulator-only testing.

**Result: three confirmed medium-priority issues remain. This was an audit; application code and the delivered APK were not changed.** The prior 45 tests still pass, but they did not cover these edge cases. This report supplements the earlier functional audit and supersedes its “no remaining failure” statement for the expanded scope.

## Confirmed findings

### 1. [P2] The floating microphone intercepts a commanded swipe

Location: `app/src/main/java/dev/saygo/app/control/PhoneControlService.kt:192–197`, together with its touch handler at lines 102–115.

Trigger: drag the floating button over the starting point of a swipe, then issue that swipe. For “Next reel”/“Scroll down”, the starting point is the active window's horizontal center and 72% of its height.

Observed on the unchanged production APK: the bubble moved upward while the Settings content stayed in place. saygo nevertheless reported **“Swipe sent.”** The final diagnostic measured its top changing from 1654 px to 713 px. An independent earlier run measured 1654 px to 598 px; both show interception. The callback confirms delivery of a screen gesture, not receipt by the intended app.

Cause: the service leaves its own touchable overlay in the gesture path. Foreground-package validation does not exclude that overlay from touch dispatch.

Recommended correction: temporarily hide or make the bubble non-touchable before dispatch, then restore it on completion, cancellation, rejected dispatch, or interrupted lifecycle as appropriate. Preserve the user's bubble preference and position.

Verification required for a fix: drag the bubble to the start of each of the four gestures; verify the target scrolls, the bubble stays in place, and normal bubble tapping/dragging works afterward.

Evidence: [before](rescan-evidence/rescan-bubble-before.png), [after](rescan-evidence/rescan-bubble-after.png), [coordinates and result](rescan-evidence/rescan-bubble.txt), `rescanBubbleInterceptsCommandWhenDraggedOntoSwipeStart` in the diagnostic test copy.

### 2. [P2] Queued Back bypasses the foreground-app check

Location: `app/src/main/java/dev/saygo/app/control/PhoneControlService.kt:150–159`.

Trigger: Back has been queued for one app, but another app becomes active before the voice panel is considered closed. The delayed navigation branch runs before the foreground-package validation used for swipes.

Observed: the diagnostic queued Back with `com.android.settings` as its origin, brought `dev.saygo.app` to the foreground, then released the pending action. Android accepted the Back action and saygo reported **“Back requested.”** It did not reject the changed target. This is a controlled queue/lifecycle reproduction; it does not require human speech.

Impact: a timing race can send Back to a different app from the one over which the user spoke. Unlike Home/Recents, Back is dependent on the current screen and can leave the wrong task or dismiss its UI.

Recommended correction: validate the effective original foreground target for Back immediately before dispatch. Keep the intentionally global behavior of Home/Recents, and preserve Back initiated from saygo itself.

Verification required for a fix: queue Back for app A, switch to B during the wait, and assert no global action is dispatched. Also verify normal Back from A and from saygo, and the existing Home/Recents flows.

Evidence: [observed origin, foreground and feedback](rescan-evidence/rescan-back.txt), `rescanBackStillRunsAfterOriginAppChanges` in the diagnostic test copy.

### 3. [P2] Valid app names containing “and” or “then” cannot launch

Location: `app/src/main/java/dev/saygo/app/commands/Command.kt:37–41`.

Trigger: a single installed app's full label contains either word. For example, the synthetic full labels “Dungeons and Dragons” and “Then and Now” were passed to the actual parser as “Open <label>”. Both returned null before installed-app lookup could run.

Cause: the chained-command filter rejects either word anywhere in a label. This conflicts with the guide's instruction to use an installed app's full name. These are parser fixtures; the audit does not claim those exact apps were installed.

Recommended correction: allow an unambiguous exact installed label to resolve to one launch action, while continuing to reject actual multiple-action requests. Do not simply remove the chain guard and add fuzzy matching.

Verification required for a fix: launcher fixtures with conjunctions, duplicate labels, unknown names, and genuine “open X and scroll down” requests. Only one launcher intent should ever be emitted.

Evidence: [parser diagnostic](rescan-evidence/RescanParserProbe.kt), [JUnit result](rescan-evidence/TEST-dev.saygo.app.commands.RescanParserProbe.xml).

## Verification and coverage

- The delivered APK's SHA-256 matches the build copy. Every application file in the source project still matches the delivered 0.4.1 source ZIP: [integrity record](rescan-evidence/source-integrity.json).
- All **32 existing Android instrumentation tests** passed again on that APK. Two extra characterization probes also passed by confirming the current defects: **34 total**. Their passing status does not mean the defects are fixed. [Run log](rescan-evidence/full-instrumentation-log.txt).
- All **13 existing JVM tests** passed again, plus two parser characterization probes: **15 total**. One additional probe records trailing search-punctuation normalization; that is already documented behavior and is not counted as another confirmed bug.
- Debug/test compilation, unit tests, lint, and release bundle tasks succeeded. Unchanged build tasks were reused by Gradle. Lint reports **0 errors and 10 warnings**: dependency freshness, target freshness, redundant resource qualifier, a newer XML attribute ignored on older APIs, and a bubble accessibility warning. The bubble does override/call `performClick`; the warning alone does not prove a click failure.
- The first bubble probe could not save a screenshot under the test package's unavailable external directory. That harness path was corrected to the app's internal files directory. The next probe read a stale accessibility node before its geometry updated; screenshots already showed movement. The final probe polls fresh geometry and reproduced the defect. Both intermediate logs are retained and are not counted as app crashes.

| Area reviewed | Evidence and result |
|---|---|
| `MainActivity` permission and settings flows | Source, current UI tests, earlier permission captures; the prior permanent-denial recovery remains present. |
| `VoiceActivity` disclosure gate, cancellation, retry and lifecycle | Full source review and current speech/UI suites; human audio and interruption by a real call remain untested. |
| `SpeechSession` provider selection, timeout and callbacks | Full source review and five callback tests. Engine existence is not proof that the requested English model is installed; provider-specific recovery still needs device tests. |
| `SaygoApplication` and `FeedbackQueue` | Full source review and six queue tests; existing cancellation and initialization fixes remain. Audible TTS and real engine failures are outside this emulator run. |
| Command parser/executor | All branches reviewed, seven existing parser and six executor tests plus diagnostics; app-name issue confirmed. Search URIs encode query data. |
| Actual app/search routing | Three existing tests passed: YouTube launcher, Google browser, YouTube search receiver. Rendering internet results is not asserted. |
| `PhoneControlService` | All source paths reviewed, eleven existing service tests plus two diagnostics; overlay interception and stale Back target confirmed. |
| Compose Home/Commands/Setup, components, theme, strings and drawable resources | Full source review and seven current UI tests. Navigation, guide, switches, privacy and disclosure dismissals pass; no new visual redesign was made. |
| Preferences/session state and backup rules | Full source/configuration review. Consent/preferences are local; backups excluded; no transcript-history persistence code found. |
| Manifest and release packaging | Reviewed source and merged release manifests, SDK levels, exports, service permission, queries, shrinker config and build output. VoiceActivity is private; accessibility service is system-permission protected. |
| Gradle wrapper, dependencies and CI | Pinned wrapper/checksum and build configuration reviewed; build/lint pass. CI coverage gap noted below. |
| README, privacy, publishing, testing, design and prior audit documents | Reviewed against current behavior. Full-name launch promise needs the parser correction. Publication prerequisites remain documented and incomplete. |

## Additional gaps and limits

- **CI does not run Android instrumentation tests.** `.github/workflows/android.yml:25` runs JVM tests, lint and builds, without an emulator or `connectedDebugAndroidTest`. Device behavior can regress while CI remains green. Add an emulator job and compatibility coverage before release; this is a validation gap, separate from the three reproduced app defects.
- Android 10/API 29 and Android 14/API 34 runtime compatibility remain unverified. Only Android 15/16 system images are installed locally; this rescan used API 36.
- Human speech accuracy, audible TTS, Instagram/Reels, manufacturer-specific lifecycle behavior, phone calls, full TalkBack use, and release-minified runtime behavior remain unverified. No new claim of universal phone compatibility is made.
- The on-device speech preference does not test English model readiness before choosing that engine. The app shows an error if English is missing; Retry makes the same selection. This is a provider-dependent recovery limitation, not an additional device-reproduced failure in this report. Android distinguishes an existing recognizer from an unavailable language model in the [SpeechRecognizer API](https://developer.android.com/reference/android/speech/SpeechRecognizer).
- Always-on listening, arbitrary tapping/typing, automatic entry into Reels and multistep automation remain documented unimplemented capabilities, not regressions introduced by this scan.
- No GitHub publication, Play submission, signing changes or production fix was made. Existing versioned APK/AAB/ZIP artifacts are unchanged. Diagnostic tests are preserved with this report and were used only in the build copy.

Android's [AccessibilityService documentation](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#dispatchGesture(android.accessibilityservice.GestureDescription,%20android.accessibilityservice.AccessibilityService.GestureResultCallback,%20android.os.Handler)) specifies screen-level gesture dispatch. The overlay finding above is based on the actual emulator reproduction, not documentation alone.

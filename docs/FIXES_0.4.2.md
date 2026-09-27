# saygo 0.4.2 — fixes and verification

All three confirmed defects from the 0.4.1 rescan are fixed. Version code: 11. Application ID remains `dev.saygo.app`; the debug APK can update the previous debug build.

## What changed

1. **Floating microphone intercepting swipes:** the service temporarily detaches the overlay, allows Android's input-window update to settle, then checks consent, lock state and the foreground target again before dispatch. It restores the same button and position after completion/cancellation/rejected dispatch. Cancelling pending work restores it; hiding the bubble or disabling controls prevents restoration. A brief detachment alone was insufficient in testing, which is why the second validation pass is present.
2. **Back affecting a changed app:** Back now validates its original package immediately before dispatch, just as swipes do. Missing origins are rejected. MainActivity explicitly supplies saygo as the origin so Back still works from the app itself. Home and Recents remain global actions.
3. **App names containing “and” or “then”:** the parser accepts these words when the entire requested name exactly matches an installed launcher label. Resolution remains local and case insensitive, duplicate packages remain deduplicated, and ambiguous labels still receive the existing error. Unknown/chained phrases do not execute multiple actions.

## Verification

**38 Android instrumentation tests and 14 JVM tests passed: 52 total.** All tests ran against the final 0.4.2 application build on the isolated Android 16/API 36 emulator.

| Fix or gate | Evidence |
|---|---|
| All four swipes reach another app with the bubble at the starting point | A separate test-APK Activity records actual DOWN/UP coordinates and the received direction. All four directions passed; the button retained its position. This checks receipt by the target, not only Android's completion callback. |
| Bubble remains usable | Post-swipe dragging and clicking passed. Preference-off and service-disable during a pending swipe did not resurrect the bubble. |
| Stale Back | A Back command queued for Settings was rejected after a different app became foreground. The other app remained active. |
| Normal navigation | Back from saygo and from the original external app passed; missing-origin Back was rejected; existing Home/Recents tests passed. |
| Names with conjunctions | Real installed test-APK launcher labels “Dungeons and Dragons” and “Then and Now” resolve to one launcher intent. Parser tests also cover case differences, unknown names and chained requests. |
| Existing behavior | App/search routing, URL encoding, consent, preferences, UI navigation, speech callbacks and feedback queue tests passed. |
| Build | Debug APK, instrumentation APK, unit tests, debug lint, release shrinking/vital lint and unsigned release AAB tasks passed. |
| Lint | 0 errors, 10 existing advisory warnings. |
| Deliverable | APK signature and 16 KB ZIP alignment verified. Application source and test files match the build copy byte for byte. |
| Fixture isolation | The gesture receiver and synthetic launcher labels live only in the test APK; neither is in the merged release manifest. |

Android suite: 7 executor + 3 real routing + 16 service + 7 Compose UI + 5 speech callback = 38. JVM suite: 8 parser + 6 feedback queue = 14.

Evidence: [final Android run](fix-evidence/fix-instrumentation.txt), [swipe receiver results](fix-evidence/fix-swipe-results.txt), [build](fix-evidence/fix-final-build.log), [artifact verification](fix-evidence/fix-artifact-verification.txt). Unit XML reports and the merged release manifest are in `fix-evidence/`.

The regression fixture initially lacked Kotlin runtime classes when launched in its own process; it now uses only Android/Java framework classes. Drag simulation uses a realistic series of touch moves while asserting the same exact final position. Routing tests temporarily grant YouTube notification permission on API 33+ to keep its external first-run dialog out of the routing assertion, then restore the previous permission state. UiAutomation also requests view IDs for its guarded prompt handler. These were test-harness corrections; the assertions for target receipt, button position and changed-app rejection remain in the final suite.

During repeated receiver tests, the emulator's YouTube launcher entered a self-relaunch loop and Android killed it with `rapidActivityLaunch`. The Android event log is preserved in [fix-youtube-loop.txt](fix-evidence/fix-youtube-loop.txt). Only YouTube's data in the disposable, account-free emulator was reset before the final run; no phone or user account data was involved. The final passing routing checks used the unchanged saygo production APK.

## CI coverage

The GitHub workflow now includes an instrumentation matrix for API 29, 34 and 36 using `reactivecircus/android-emulator-runner@v2`. Workflow YAML parsing and the matrix/command structure were checked locally. The runner configuration follows its [official instructions](https://github.com/ReactiveCircus/android-emulator-runner).

The emulator CI job runs core app, fixture, service, UI and speech-callback tests. It explicitly excludes `CommandRoutingTest`, whose three tests require actual YouTube/Chrome installations; those three passed locally and remain in the normal full test command. Hosted CI has **not** been executed because this project has not been pushed to GitHub. API 29/34 runtime results are therefore still pending, not claimed as passing.

## Limits

The three rescan defects are resolved in the checked scope. Real English speech accuracy, audible TTS, Instagram Reels, physical Android 10/14 and OEM-specific behavior remain unverified. The emulator ran without audio. This release does not add continuous listening, arbitrary tapping/typing, multistep actions or automatic navigation into Reels. The APK is debug signed and the AAB is unsigned; publishing and release signing remain separate.

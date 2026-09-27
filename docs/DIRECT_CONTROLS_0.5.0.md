# saygo 0.5.0: direct phone controls

The user selected individual voice commands with Google Play support. This version adds six explicit commands to the previous app/search/swipe/navigation set:

- `Tap Search`: exact, case-insensitive match to one visible usable label or content description. Supports a label inside a clickable parent.
- `Long press Download`: one supported long-click on the named control.
- `Type Hello, world!`: insert literal text at the focused field’s cursor or replace its selection. Spoken instructions inside the payload remain text.
- `Replace text with Hello`: replace the focused field’s entire contents.
- `Clear text`: empty the focused field.
- `Select all`: select the focused field’s text.

Missing, partial, duplicate and disabled labels do not trigger a guessed action. The screen scan has a fixed size limit and rejects an incomplete scan. Focused-field editing requires a visible, enabled editable field in the validated app window. Password fields require the keyboard. Tap and text commands share the existing consent, lock and foreground-package checks; withdrawing consent or switching apps before dispatch cancels the action. No command sequence or autonomous planner has been added. Feedback does not contain the dictated text or field contents.

Phone controls now inspect control labels and the focused field locally when needed. The in-app disclosure, accessibility description and privacy draft reflect that change. The new disclosure uses a v2 consent key; prior v1 consent does not authorize expanded access. After upgrading, review Phone controls in Setup and enable the service again.

## Verified

**43 Android instrumentation tests and 16 JVM tests passed (59 total).** The Android run used the isolated API36 emulator with actual YouTube/Chrome routing included. Debug build, lint and release bundle with shrinking passed. Lint reports 0 errors and 10 advisory warnings. APK signature and 16 KB ZIP alignment were checked. Both external fixture activities remain test-only.

Five added Android tests cover actual tap/long-press delivery in a separate app process; absent, disabled and duplicate targets; literal text insertion, selection replacement, whole-field replacement and clearing; stale-app and revoked-consent cancellation; and migration from the old disclosure. Two new JVM tests cover parsing and literal payload preservation. The command-guide UI test now scrolls through every new command.

An initial test issued a typing command before the preceding tap finished establishing input focus. The test now waits for the observable focused editable field before issuing the next independent command. The isolated text-edit test and subsequent full 43-test run passed. This was a test-sequencing correction; production focus validation remains strict.

Evidence is saved in [controls-evidence](controls-evidence/), including the final Android output, JVM XML, build logs and lint report.

## Compatibility and remaining gaps

**Version 0.5.0 passed the hosted build and all three emulator jobs in [run 36333025318](https://github.com/vabxsen/saygo/actions/runs/36333025318), for app commit `2201feb`. Downloaded test XML confirms 40 tests on each API (29, 34 and 36), with zero failures, errors or skips. The [saved result summary](controls-evidence/hosted-results.json) lists the exact tests.** Hosted instrumentation excludes three tests requiring actual YouTube/Chrome installations; those passed in the local full run.

This is still **not universal phone control**. Unlabelled/canvas controls, unsupported fields, free-form task planning, automatic Reels navigation, continuous listening, wake words, repeated scrolling and dedicated messaging/purchase workflows are not implemented. Real speech accuracy and actual Instagram behavior remain unverified under the user's emulator-only testing preference. The packaged APK is debug signed; the bundle is unsigned. Google Play submission and approval are still outstanding.

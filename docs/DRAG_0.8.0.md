# saygo 0.8.0: explicit hold-and-drag

## Commands and behavior

Say **Show grid**, optionally **Zoom cell 5**, then **Drag cell 1 to cell 9**. Each endpoint is the centre of a different cell in the current grid region. Digits and English number words work. A request maps to one fixed hold, straight movement and release; it never selects an item or plans a task. The target app must support this input. Delivery success does not establish that an arbitrary app accepted a drop.

The microphone and grid are removed before touching. Consent, screen availability, origin package, window identity and bounds are validated before the hold. The hold lasts Android's configured long-press timeout plus 150 ms; movement lasts 700 ms. The original target and permission checks run again between hold and movement. Android's [continued stroke API](https://developer.android.com/reference/android/accessibilityservice/GestureDescription.StrokeDescription#continueStroke(android.graphics.Path,%20long,%20long,%20boolean)) keeps the same pointer down across both phases.

**Cancel** now clears queued phone commands as well as closing the grid. Cancellation or a failed validation during a drag hold releases at the source instead of moving. The initial hold may already have affected the other app; the result says so. Movement already dispatched may finish. Interrupted or disabled services queue no further command. Grid coordinates are temporary and retain the existing app-change, scroll, screen-off, rotation and one-minute expiry rules. No new data collection or network service is added.

## Verification

**59 Android instrumentation tests and 21 JVM tests passed (80 local tests).** Debug/test builds, lint and release shrinking/bundling passed. Lint reports zero errors and 10 existing advisory warnings. The full local API36 suite includes actual Chrome/YouTube routing; it does not test live speech recognition or Instagram.

The new separate-process test app verifies one pointer DOWN/UP, a real long-press interval before movement, native Android drag-and-drop payload delivery, and exact source/destination coordinates in full and offset windows. The microphone is placed over the source and must return to its exact position afterward. Other cases cover a zoomed grid, missing/invalid/identical cells, cancellation and consent withdrawal during the hold, a replacement window from the same app, and cancellation while a command waits for the voice panel to close. The UI test checks the new command-guide row.

The initial tests caught an implementation defect: a stationary continued stroke reported completion immediately after DOWN, regardless of its declared duration. Movement started roughly 40–80 ms after DOWN, before the native long-press timeout. The fix uses an explicit Handler timer after pointer-down delivery, then validates the target and continues the same stroke. Android's [input injector implementation](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/services/accessibility/java/com/android/server/accessibility/MotionEventInjector.java) suppresses unchanged MOVE samples and completes a sequence at its last emitted event. The test fixture now measures elapsed uptime and signals actual DOWN directly, allowing reliable mid-hold cancellation without depending on delayed accessibility content events. Earlier failures are preserved alongside the passing results.

Evidence: [full Android suite](drag-evidence/drag-full-instrumentation.txt), [actual touch/drop receipts](drag-evidence/drag-delivery.txt), [final build](drag-evidence/drag-final-build.log), and JVM XML/lint reports in [drag-evidence](drag-evidence/).

Hosted Android 10, 14 and 16 checks for this version are pending. Earlier versions' passes do not establish this version's compatibility.

## Remaining scope

This does not satisfy universal phone control. Independent precision markers for each endpoint, arbitrary multi-finger gestures, wake words and continuous listening remain unimplemented. Live speech accuracy, actual Instagram Reels, manufacturer-specific behavior and arbitrary third-party drag targets remain unverified under the emulator-only preference. Google Play submission and approval remain outstanding. The APK is debug signed and the release bundle unsigned.

# saygo 0.7.0: native pinch zoom

## Commands and behavior

Say **Zoom in** to spread two fingers, or **Zoom out** to bring them together. **Pinch out** and **Pinch in** are equivalent physical-gesture aliases. The action lasts half a second and is centred on the current app window. It works in content that supports pinch input; a successful dispatch is not a claim that arbitrary content changed size. **Zoom cell 5** still refines the numbered grid and never pinches the app.

The grid is dismissed and the microphone temporarily removed before input. After the input-window update, saygo checks consent, screen availability, the original app, window identity and bounds again. A different window from the same package is rejected. Gesture completion or cancellation restores the microphone at its previous position. The app refuses its own UI, missing origins and unusably small/negative window bounds.

The gesture uses two simultaneous native AccessibilityService strokes. It adds no screenshot capture, screen interpretation, network service, background listening or task planning. The existing optional control disclosure and command guide now include pinch zoom. Android API reference: [dispatchGesture](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#dispatchGesture(android.accessibilityservice.GestureDescription,%20android.accessibilityservice.AccessibilityService.GestureResultCallback,%20android.os.Handler)).

## Local verification

**54 Android instrumentation tests and 20 JVM tests passed (74 total).** The Android run used API36 and included actual YouTube/Chrome routing. Debug/test builds, release shrinking/bundle and lint passed. Lint reports zero errors and 10 existing advisory warnings.

The separate test application uses Android's real ScaleGestureDetector. It verifies two pointers, one completed touch sequence and actual scale change for both directions, in full and offset windows. The microphone is deliberately placed at the first touch point and the grid is present before each command. Tests assert removal of the grid and restoration of the microphone position. Additional tests check a replaced window during gesture preparation, withdrawn consent, and missing/own/changed origins. Parser coverage checks direction aliases, literal dictation, grid distinction and rejection of chained or repeated requests. The guide test scrolls to the new command row.

The first run received both fingers but the smaller window scaled too little. Its original finger spread crossed Android's native minimum scaling span too late. Increasing the ending spread from 64% to 80% of window width made both sizes pass without weakening the scale assertion. The first full run also failed the pre-existing Back-target assertion during concurrent compilation; the final full run is performed after compilation. Earlier failures remain in the evidence directory.

Evidence: [final Android run](pinch-evidence/pinch-final-instrumentation.txt), [actual native scale receipts](pinch-evidence/pinch-delivery.txt), [build and release tasks](pinch-evidence/pinch-final-build.log), and JVM XML/lint results in [pinch-evidence](pinch-evidence/).

## Compatibility and remaining scope

Hosted Android 10, 14 and 16 results for this version are pending. Previous versions' green results do not prove this release. CI excludes three actual YouTube/Chrome routing cases; those are included in the local full suite.

This release closes the two-finger zoom gap, not the original “anything” requirement. Drag-and-drop with an initial hold, arbitrary multi-finger gestures, wake words and continuous listening remain unimplemented. Live speech accuracy, actual Instagram Reels, other manufacturers' devices and arbitrary third-party zoom behavior remain unverified under the emulator-only preference. Google Play submission and approval remain outstanding. The APK is debug signed and the bundle unsigned.

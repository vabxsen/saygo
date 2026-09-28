# saygo 0.9.3: Android 14 Quick Settings

Version code 19; minimum API29 and target API36.

The final 0.9.2 hosted run passed all 64 tests on Android 10 and 16. Android 14 passed 63, but `open quick settings` left the collapsed notification shade visible. The diagnostic tree showed `quick_qs_panel` and no expanded `quick_settings_panel`; one native request was accepted. [Failure evidence](quick-settings-evidence/hosted-36378556027-results.json).

The existing bounded continuation now also applies to Android 14 and 15 when animation duration is zero. The first native request must be accepted, SystemUI must have input focus, and consent/unlocked-screen checks remain in force. At most one additional expansion can occur, within the same two-second cancellable queue. No animation settings are changed. The consent-withdrawal test now requires the continuation to be rejected on API34 and newer.

Local build, all 23 JVM tests, lint, debug/test APKs and the unsigned release bundle passed. Four API36 native panel and Stop/consent regression tests passed with animations disabled; original animation settings were restored afterward. [Local evidence](quick-settings-evidence/android14-panel-tests.txt). Hosted Android 10/14/16 compatibility verification is pending. The assertions still require the actual expanded native panel. Earlier failures remain recorded; they are not treated as passes. Live microphone recognition, Instagram/Reels, physical-device and OEM behavior, production signing and Google Play review remain outstanding.

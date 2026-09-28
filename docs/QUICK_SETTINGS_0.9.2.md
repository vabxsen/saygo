# saygo 0.9.2: wait for the native panel

Version code 18; minimum API29, target API36. Commands remain explicit and deterministic.

The 0.9.1 retry could give up if SystemUI had not taken focus at the first check. This follow-up starts checking after 800 ms, refreshes the accessibility cache and waits up to two seconds from the accepted request for SystemUI focus. Only then can it send the single additional native expansion. It does not send further actions if the panel never gains focus. Stop, a new command, consent withdrawal, lock/screen-off and service interruption retain their existing controls. The app never changes the user's animation settings.

The earlier [0.9.1 report](QUICK_SETTINGS_0.9.1.md) preserves local passes and hosted failures. The local Play Store image differs from CI's Google APIs image. Installing the matching image was attempted, but the official 1.76 GiB archive transferred only 41,320 bytes in a 30-second range test; that installer was stopped. Hosted tests remain the compatibility check for that image.

## Verification

Build, 23 JVM tests, lint and the unsigned release bundle passed. The cold-boot Quick Settings test and three notification/cancellation/consent tests passed on the local API36 Play Store image with animations disabled. [Evidence](quick-settings-evidence/). The final five-test panel/control/swipe subset also passed with the lifecycle-based gesture fixture. The full local API36 core suite also passed all 64 tests with normal animations. [The first hosted run](quick-settings-evidence/hosted-36366617340-results.json) passed build and all 64 API29 tests. API34/API36 each failed only while waiting for the GestureTargetActivity fixture before the Quick Settings command was sent. Those runs do not demonstrate a production dispatch failure and are not counted as compatibility passes. Native panel checks still require the visible expanded panel, and cancellation checks wait beyond the two-second continuation limit. The lifecycle-based grid/drag/pinch test fixtures remain; they passed all 64 core tests with normal animations in the 0.9.1 local run.

## Limits

Live microphone recognition, actual Instagram Reels, all-device/OEM coverage and Google Play approval remain unverified. The release bundle remains unsigned; publisher setup and Play submission are incomplete. See the [capability audit](CAPABILITY_AUDIT.md).

## Launcher-based panel check

The native notification and Quick Settings assertions now start from the emulator's resolved home launcher instead of launching a fixture Activity. This removes the unrelated first-activity readiness dependency from global panel tests. The test still uses the production parser/executor/service and requires the actual expanded native panel; it restores the launcher by collapsing the shade in `finally`. Gesture tests keep their separate lifecycle-aware fixtures. Both refined native panel tests passed locally after a cold boot with animations disabled. The test APK build passed. The refined panel tests passed on API29/34/36. [The hosted reports](quick-settings-evidence/hosted-36377364377-results.json) show all 64 API29 tests passing and one unrelated pinch-fixture readiness failure on each of API34/API36.

The four native test activities now share readiness handling for disabled animations: with window and transition scales both zero, focus plus the next frame signals readiness, since an enter-animation completion callback may be omitted. Normal-animation launches retain their completion callback. The fixture helper reports its last window IDs, bounds and ready-label state on failure. The local five-test pinch/drag/grid/swipe/panel subset passed with animations disabled, and the test build passed. The final [hosted run](quick-settings-evidence/hosted-36378556027-results.json) passed all 64 tests on API29 and API36. API34 passed 63 tests but the actual Quick Settings action left the collapsed shade visible. All fixture readiness checks passed. This is a remaining production compatibility failure, addressed in the 0.9.3 follow-up.

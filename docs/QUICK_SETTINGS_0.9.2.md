# saygo 0.9.2: wait for the native panel

Version code 18; minimum API29, target API36. Commands remain explicit and deterministic.

The 0.9.1 retry could give up if SystemUI had not taken focus at the first check. This follow-up starts checking after 800 ms, refreshes the accessibility cache and waits up to two seconds from the accepted request for SystemUI focus. Only then can it send the single additional native expansion. It does not send further actions if the panel never gains focus. Stop, a new command, consent withdrawal, lock/screen-off and service interruption retain their existing controls. The app never changes the user's animation settings.

The earlier [0.9.1 report](QUICK_SETTINGS_0.9.1.md) preserves local passes and hosted failures. The local Play Store image differs from CI's Google APIs image. Installing the matching image was attempted, but the official 1.76 GiB archive transferred only 41,320 bytes in a 30-second range test; that installer was stopped. Hosted tests remain the compatibility check for that image.

## Verification

Build, 23 JVM tests, lint and the unsigned release bundle passed. The cold-boot Quick Settings test and three notification/cancellation/consent tests passed on the local API36 Play Store image with animations disabled. [Evidence](quick-settings-evidence/). The final five-test panel/control/swipe subset also passed with the lifecycle-based gesture fixture. Hosted results are pending. Native panel checks still require the visible expanded panel, and cancellation checks wait beyond the two-second continuation limit. The lifecycle-based grid/drag/pinch test fixtures remain; they passed all 64 core tests with normal animations in the 0.9.1 local run.

## Limits

Live microphone recognition, actual Instagram Reels, all-device/OEM coverage and Google Play approval remain unverified. The release bundle remains unsigned; publisher setup and Play submission are incomplete. See the [capability audit](CAPABILITY_AUDIT.md).

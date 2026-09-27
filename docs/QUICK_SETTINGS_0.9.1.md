# saygo 0.9.1: Quick Settings after boot

Version code 17; Android 10+ (minimum API29), target API36. The command set remains the explicit, deterministic controls introduced in [0.9.0](SYSTEM_CONTROLS_0.9.0.md).

## Problem and change

On Android 16 with animation duration set to zero, the first `Open quick settings` after a cold boot could open only the collapsed notification shade. Android accepted the global action, but the expanded controls and brightness slider were absent. Both hosted attempts failed this assertion; the [second failure](system-control-evidence/refined-api36-failure.json) is retained alongside the original. A cold-boot local emulator reproduced the visual failure. Later invocations could pass, so a warm emulator alone was insufficient evidence.

The service now allows one additional native Quick Settings expansion after 800 ms on API36+ when animations are disabled. It runs only if the first request was accepted and SystemUI still has input focus. It does not change animation settings, collapse the shade, inject a swipe, or choose another task. The continuation remains in the existing cancellable queue and rechecks consent, screen lock, screen interactivity and the voice panel before running. New commands, Stop, service interruption and screen-off cancel queued work. Volume changes are never repeated.

The focus check refreshes the [accessibility cache](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#clearCache()) and then reads the focused window. Both the active root and cached window focus can otherwise still refer to the previous app after a native panel opens. No notification text is collected by the diagnostic reports.

## Verification

The cold-boot API36 regression passed with all three animation scales set to zero. The test uses the production parser/executor/service and requires the actual expanded native panel. The final build, 23 JVM tests, lint and unsigned release bundle passed. Full core emulator and hosted compatibility checks are pending. [Saved local evidence](quick-settings-evidence/). The visibility assertion still requires the expanded `quick_settings_panel`; a collapsed shade is not accepted as a pass.

## Remaining limits

Live microphone recognition, actual Instagram Reels, universal OEM compatibility and Google Play approval remain unverified. The release bundle is unsigned. This patch does not establish unrestricted phone control or complete Play publication. See the [current capability audit](CAPABILITY_AUDIT.md).

> Current version: **0.9.2**. See [native panel readiness](QUICK_SETTINGS_0.9.2.md). Earlier sections preserve verification history.

> Current version: **0.9.1**. See [Quick Settings after boot](QUICK_SETTINGS_0.9.1.md). Earlier sections preserve verification history.

> Current version: **0.9.0**. See [native system controls](SYSTEM_CONTROLS_0.9.0.md). Earlier sections preserve verification history.

> Additional 0.8.0 evidence: [actual YouTube Shorts controls](YOUTUBE_CONTROL_0.8.0.md). Opening, pause/resume and next/previous clips were observed through explicit commands; speech input was bypassed.

> Additional 0.8.0 evidence: [actual speech-provider and receiving-app audit](SPEECH_APP_AUDIT_0.8.0.md). Synthetic-file recognition passes do not establish microphone capture.

> Current version: **0.8.0**. See [hold-and-drag verification](DRAG_0.8.0.md). Earlier sections preserve verification history.

> Current version: **0.7.0**. See [native pinch zoom and current results](PINCH_0.7.0.md). Earlier sections preserve verification history.

> Current version: **0.6.0**. See [numbered grid and current results](GRID_0.6.0.md). The sections below preserve earlier verification history.

> Current version: **0.5.0**. See [direct controls and current results](DIRECT_CONTROLS_0.5.0.md). The sections below preserve the 0.4.2 verification history.

# Verification — saygo 0.4.2

Version code 11; minimum Android API 29, target API 36.

All three rescan findings are fixed. **38 Android instrumentation tests and 14 JVM tests pass.** Debug/test builds, lint and release AAB build pass. Lint has 0 errors and 10 existing warnings. APK signature and 16 KB alignment were verified.

See [FIXES_0.4.2.md](FIXES_0.4.2.md) for regression coverage, logs and scope. A GitHub emulator matrix is configured for API 29/34/36 but has not run remotely. Local runtime verification used API 36. Physical Android 10/14, live speech, audible feedback and Instagram remain unverified.

# Device acceptance tests

Record results with OS version, phone model, speech provider, app versions, and network conditions. A successful build is not proof of speech quality or compatibility on every device.

## Required device matrix before release

- Android 10 / API 29: minimum-version launch, permissions, recognition, overlay, gestures.
- Android 14 / API 34: owner's phone, microphone indicator/lifecycle, background restrictions.
- Android 16 / API 36: target-version behavior, edge-to-edge layouts, back navigation.
- At least one additional manufacturer, e.g. Samsung, for service management and permissions.

## Flows

1. Fresh install: no microphone capture, overlay, or accessibility access before opt-in. Decline each disclosure; verify nothing is enabled. Grant microphone; leave controls off; app launches and searches still work.
2. Revoke microphone permission and disable the OS microphone privacy toggle; verify a useful error and no crash. Test no speech provider, missing English model, noisy audio, no network, and timeout.
3. Say each documented phrase. Verify matching is case insensitive. Check installed app absent and duplicate launcher names. Search for `C++ & Kotlin`, accented characters, and text containing URL punctuation; verify correct query encoding.
4. Enable controls after consent. Open a target app, tap the bubble, say “next reel”; check exactly one upward swipe after the listening panel closes. “Previous reel” reverses it. Test horizontal swipes separately. Navigate to Reels manually before testing.
5. Switch apps while the voice panel is open; lock the phone; cancel recognition; rotate; press Home; interrupt with a call. Verify microphone stops, stale commands do not act on another app, and no automatic retries occur.
6. Test Back, Home, Recents. Test scrolling from saygo itself: it must instruct you to use the bubble over the target app.
7. Hide/show and drag the bubble; rotate to landscape; use split screen. Check keyboard and system navigation remain reachable. Disable accessibility while a command is pending; ensure pending work is cancelled and the bubble disappears.
8. Test 200% font size, screen reader labels, dark mode, 320dp width, portrait and landscape. Scroll all screens and dialogs; buttons must remain reachable.
9. Toggle spoken feedback. Start another listening session while a result is speaking; speech output must stop before recognition starts. Test with no TTS engine.
10. Verify app data contains preferences only, no transcript/audio/history. Inspect the release manifest and SDK inventory before answering Play Data safety.

Automated unit tests exercise the command grammar and reject unsupported/chained actions. Instrumented tests check consent declines, navigation to the command guide, query encoding, and missing-app failure. Live speech and third-party app interactions require real-device testing.



## Regression additions in 0.4.2

Place the floating button at the starting point of each of the four swipes; verify the target receives the gesture and the button returns to the same position. Verify it remains draggable/clickable, and hiding it or disabling controls during an action keeps it hidden. Queue Back for one app and change the foreground before dispatch; Back must be rejected. Check Back from saygo still works. Launcher names containing “and” or “then” must work only as exact installed labels; chained commands remain unsupported.

The full local instrumentation suite includes real YouTube/Chrome routing. CI's API 29/34/36 matrix runs the core suite with that external-app-only class excluded; keep actual third-party app tests in the release acceptance matrix.

## Direct control acceptance (0.5.0)

Test exact labels, content descriptions and labels nested inside clickable parents. Duplicate, absent, disabled and partial labels must not trigger an action. A separate test APK records click and long-click delivery and exposes a real editable field. Verify literal punctuation, inserting at a selection, whole-field replacement, clear and select-all. Text must never be interpreted as another command or echoed in feedback. Repeat against actual messaging/search apps without sending anything during the test. Test changes of foreground app and revoked consent before dispatch. Old v1 disclosure acceptance must not grant v2 screen-reading access.

## Grid acceptance (0.6.0)

Verify real touches in an unlabelled external canvas at the four corner-cell centres and the central cell, with the microphone positioned over each target. Verify grid window bounds in screen coordinates and actual long-press delivery after zoom/back. Inspect rendered screenshots; an attached View alone is insufficient. Test hide/cancel, rotation, one-minute expiry, screen-off/wake, changed app/window, revoked consent and service shutdown. Delayed window events from before grid creation must not remove a fresh grid. Cancelling between overlay detachment and gesture dispatch must restore the microphone without touching the target.

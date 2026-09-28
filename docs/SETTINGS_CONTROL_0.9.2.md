# Android Settings search verification — saygo 0.9.2

On the owned Android 16 / API36 Google Play emulator, individual production commands opened Settings and its search field, entered a query, replaced it and cleared it. No setting was toggled or changed.

| Command | Observed receiving-app result |
|---|---|
| `open settings` | Android Settings opened |
| `tap search settings` | Settings Intelligence search field opened |
| `type bluetooth` | Field contained `bluetooth`; Bluetooth results appeared |
| `replace text with wifi` | Field contained `wifi`; Wi-Fi results appeared |
| `clear text` | Field returned to its empty Search settings placeholder and results disappeared |
| `go home` | Native Home request accepted during cleanup |

The receiving search package was `com.google.android.settings.intelligence`. [Saved evidence](settings-evidence/) contains command dispatch logs and only the known test field/result titles extracted from post-command UI snapshots. Dispatch success alone was not counted as proof of text editing.

These checks used the test-only ExplicitCommandProbe through the production parser, executor and accessibility service. They bypass microphone recognition and VoiceActivity. Each command was explicitly selected by the tester; the app did not plan a task. The probe restores previous consent/accessibility preferences, and the standard test runner was restored after the audit. Select-all behavior was not separately checked in this receiving app. This is bounded evidence for one native search field, not proof of every app, Android version or device.

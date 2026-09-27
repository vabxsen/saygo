# Publication preparation

Source is published at [vabxsen/saygo](https://github.com/vabxsen/saygo). The app has not been submitted to Google Play. Neither policy compliance nor store approval is guaranteed by these implementation choices.

## Google Play

1. Finalize the app name, icon, publisher identity, support contact, and unique application ID. `dev.saygo.app` is provisional. Check the name and package availability before the first upload.
2. Complete the device matrix in TESTING.md, including Android 10 and the owner's Android 14 phone. Verify speech with the actual installed provider, English language models, and internet disabled/enabled. Test current Instagram and YouTube versions.
3. Complete developer-account verification and any account-specific testing requirements displayed in Play Console. Prepare a signed release AAB using your upload key and Play App Signing. Keep the key out of GitHub; do not use the debug signing key for production.
4. Keep `minSdk=29` and `targetSdk=36` for the current intended release, revisiting the target requirement before submitting. Target SDK and minimum SDK are independent.
5. Publish a finalized privacy policy at a public HTTPS URL. Replace the publisher/contact placeholders in PRIVACY.md. The in-app privacy explanation is already implemented.
6. Submit the AccessibilityService declaration and a demonstration video showing the prominent in-app disclosure, affirmative consent, manual Android enablement, and one-command/one-action behavior. The app uses `isAccessibilityTool=false`: it is a general voice utility, not a verified disability-focused accessibility tool.
7. Explain the narrow, human-defined mappings and absence of autonomous planning. “Next reel” is exactly one swipe; it does not independently navigate to Reels or choose content. Ask for review based on the actual implemented behavior, not broader future promises.
8. Complete Data safety from the released binary and the actual speech-provider behavior. Do not blindly declare that no data ever leaves the phone: the system recognizer may process audio online, explicit queries go to Google/YouTube, and optional spoken feedback uses the Android TTS engine. Reassess when adding SDKs, crash reporting, analytics, or any backend.
9. Prepare store screenshots, feature graphic, content rating, target audience, and an accurate listing. Accessibility use must be documented in the listing. Upload to an internal test track first.

Draft short description: **Open apps, search, and navigate your phone with simple voice commands.**

Draft listing paragraph: **saygo listens when you tap its microphone and performs one supported English command. Open installed apps, search Google or YouTube, or enable optional phone controls for named taps and long presses, focused-field text editing, grid-directed taps and drags, single swipes, pinch zoom, Back, Home, and Recent apps. Phone controls use Android's AccessibilityService to show a floating microphone or optional numbered grid and perform your explicit commands. saygo processes foreground app identity, window bounds, control labels and the focused text field locally to carry out your command. It does not save screen contents or capture screenshots. Dictated text is entered into the app you control. No account is required. Speech recognition availability and offline support depend on your device and speech provider.**

## GitHub

The source includes a Gradle wrapper, MIT license, contribution guidance, tests, and a CI workflow. The repository is already published. Continue committing source and verification evidence while respecting `.gitignore`. Attach a clearly labelled debug APK to a prerelease if distributing for testing. A production GitHub APK should be deliberately signed, versioned, and accompanied by a checksum. Do not publish the unsigned AAB as an installable phone download.

## Primary references checked 2026-09-27

- [AccessibilityService automation policy](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en): deterministic automation is distinguished from autonomous planning/execution. Non-accessibility-tool disclosure, consent, and declaration apply.
- [Target API requirement](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en-gb): new mobile submissions target API 36 from August 31, 2026.
- [Android AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService): user enablement, gestures, navigation, and window access.
- [SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer): continuous recognition is not its intended use; on-device availability is device dependent.
- [Microphone background restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start).



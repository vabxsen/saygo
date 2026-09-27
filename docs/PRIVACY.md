# saygo privacy policy — publisher draft

**Before public release:** fill in the publisher identity and contact below, review the actual release behavior and provider arrangements, and host the finalized policy publicly. This draft is not a published policy.

Effective date: to be set at publication.
Publisher: **[Your publisher name]**
Privacy contact: **[Your public support email]**

saygo is an English voice-command utility. It does not require an account and contains no advertising, analytics SDK, or app-operated backend.

## Microphone and speech

saygo accesses your microphone only for a listening session you initiate by tapping its microphone. It ends recognition after one result, an error, cancellation, leaving the voice screen, or a 15-second timeout. It does not save audio or transcripts.

On devices offering on-device speech recognition, saygo requests that recognizer. Otherwise it uses your configured Android speech recognition provider. That provider may send audio to its servers and handle data according to its own privacy policy. Language availability and offline support vary. saygo has no direct internet permission and does not itself upload audio, but this does not prevent another system speech provider from processing audio online.

## Optional phone controls

With your separate consent and manual enablement in Android settings, saygo uses AccessibilityService to display a floating microphone button and perform one explicitly requested swipe or system navigation action. Android grants access to window content. saygo reads only the active package identity and root-window bounds to target a gesture. It does not read text from the screen or save/share screen contents, window bounds, or app identities.

The service receives window-change event notifications but does not inspect or retain their contents. It checks foreground window metadata when you request a command. The app does not autonomously choose actions or run multi-step plans.

## App launches, searches, and feedback

Installed launcher app names and package identifiers are matched locally to the app name you say. Search commands send the query you specify to Google or YouTube by opening a URL in an appropriate app or browser. Those services process it under their own privacy policies. Optional spoken feedback uses the Android text-to-speech engine to speak generic action results; its provider's policy applies.

## Local storage and control

saygo stores disclosure choices and preferences on your device. It holds the most recent action result in memory, not a saved history. Android cloud backup is disabled. Clear saygo's storage or uninstall it to remove its preferences. You can revoke microphone permission in Android app settings and disable phone controls in saygo Setup or Android accessibility settings.

No payments, location access, contact access, storage access, camera access, or notification-reading permission is requested by this version. This policy must be revised if those features or third-party SDKs are added.



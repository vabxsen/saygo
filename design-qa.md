# Design QA — saygo 0.4.0

final result: passed

## Evidence

- Source visual truth: docs/design/selected-home.png, the first displayed concept selected by the user.
- Final implementation: docs/screenshots/home.png. Combined comparison: docs/design/comparison-final.png (reference left, implementation right).
- Source pixels: 853 × 1844. Native capture: 1170 × 2676, density 480 dpi / 3x. Crop 72 physical pixels of native system UI at top and bottom, then normalize both to 390 × 844. CSS size is not applicable to this native Compose app.
- State: Home, light theme, idle, microphone permission granted, default text size. The native status and navigation bars remain OS-owned.
- Additional captures: Commands, Setup, dark theme and listening sheet in docs/screenshots. Large text at 390dp and 320dp widths: docs/design/cobalt-final-large.png and cobalt-small-large.png.

## Findings and comparison history

Initial comparison (docs/design/cobalt-comparison.png) found no major composition drift. [P2] The Commands tab wrapped within a word at 200% text size (docs/design/cobalt-large-home.png). Fixed by allocating extra width to the center tab at large font scales. Post-fix captures at both phone widths show all three labels on one line, with the body independently scrollable. No actionable P0/P1/P2 findings remain.

Small fidelity adjustments were made before the final comparison: display type 44 to 46sp, wordmark 29 to 27sp, microphone glyph 44% to 50% of the inner disc. The final combined comparison was opened and reviewed after these changes. No additional visual changes followed that comparison.

## Required fidelity surfaces

- Typography: native Android sans-serif matches the bold grotesk direction. Headline preserves the two-line composition. Body copy and labels remain readable and scale with system settings. Minor glyph-width differences from the generated concept are accepted P3 differences, not a pixel-identical font match.
- Spacing/layout: 26dp body margins, 172dp circular primary control, separated command rows and persistent bottom navigation match the composition. Body scrolling keeps content reachable at small heights and large text sizes. Header and microphone positions differ only slightly due to native font metrics.
- Colors/tokens: cobalt #084BDD, near-white #FDFDFD and ink #111725. White on cobalt contrast is 6.86:1; muted text on background 5.57:1. Dark theme uses a brighter blue for readable contrast. Flat native surfaces intentionally omit generation texture.
- Image/icon quality: this reference contains typography and standard UI controls, no photographic or illustrative assets. Material icons remain crisp vectors. The native Settings, search and video glyphs have minor accepted differences from the generated reference. The microphone ring is a native button border, not a rasterized interface.
- Copy/content: selected headline, helper text, command examples and navigation are retained. Before microphone permission, helper copy explains that tapping enables access. Command rows open the actual command guide. Existing disclosures remain intact.

The 800 × 844 combined comparison made all labels, glyphs and spacing readable at full view; no additional focused crop was needed. Secondary screens and large-text captures were also directly opened and inspected.

## Interaction verification

Four instrumentation tests passed on Android 16/API 36: permission-free guide access, microphone disclosure decline, accessibility disclosure decline, and Home shortcut/header-settings navigation. Manual inspection covered the listening state and cancel returning to Home. Debug/release builds and lint passed, with 0 errors and 10 existing advisory warnings. APK signature and 16KB alignment verified.

## Follow-up polish and remaining device checks

P3: exact font and icon silhouettes differ slightly from image generation. Physical Android 10/14 devices, TalkBack traversal, spoken-command accuracy, and third-party app responses remain unverified. The emulator listening screenshot verifies the interface state, not speech accuracy.

## Implementation checklist

- [x] Implement the selected native design and matching secondary screens.
- [x] Fix large-text navigation and recheck both widths.
- [x] Compare the final Home capture with the selected reference.
- [x] Run build, lint, navigation and disclosure checks.

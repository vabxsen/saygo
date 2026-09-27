package dev.saygo.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.saygo.app.data.Preferences
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SaygoUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun homeAndCommandGuideAreAvailableWithoutPermissions() {
        compose.onNodeWithContentDescription("Tap to speak a command").assertIsDisplayed()
        compose.onNodeWithText("Commands", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Voice commands").assertIsDisplayed()
        compose.onNodeWithText("“Open Instagram”").assertIsDisplayed()
        compose.onNodeWithText("“Next reel”").performScrollTo().assertIsDisplayed()
    }

    @Test fun homeShortcutsAndSettingsReturnToHome() {
        compose.onNodeWithText("Open YouTube").performScrollTo().performClick()
        compose.onNodeWithText("Voice commands").assertIsDisplayed()
        compose.onNodeWithText("Home", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("Open setup").performClick()
        compose.onNodeWithText("Permissions and preferences").assertIsDisplayed()
        compose.onNodeWithText("Home", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("Tap to speak a command").assertIsDisplayed()
    }
    @Test fun microphoneDisclosureCanBeDeclined() {
        compose.runOnUiThread { Preferences(compose.activity).speechConsent = false }
        compose.onNodeWithContentDescription("Tap to speak a command").performClick()
        compose.onNodeWithText("Microphone access").assertIsDisplayed()
        compose.onNodeWithText("Not now").performClick()
        compose.onNodeWithText("Microphone access").assertDoesNotExist()
        compose.runOnUiThread { org.junit.Assert.assertFalse(Preferences(compose.activity).speechConsent) }
    }

    @Test fun accessibilityDisclosureRequiresExplicitAcceptance() {
        compose.onNodeWithText("Setup", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("Set up phone controls").performScrollTo().performClick()
        compose.onNodeWithText("Control your phone by voice").assertIsDisplayed()
        compose.onNodeWithText("Not now").performClick()
        compose.onNodeWithText("Control your phone by voice").assertDoesNotExist()
    }

    @Test fun privacyCanBeOpenedScrolledAndDismissed() {
        compose.onNodeWithContentDescription("Open setup").performClick()
        compose.onNodeWithContentDescription("Open privacy").performScrollTo().performClick()
        compose.onNodeWithText("Your voice, your privacy").assertIsDisplayed()
        compose.onNodeWithText("Got it").performClick()
        compose.onNodeWithText("Your voice, your privacy").assertDoesNotExist()
    }

    @Test fun bothPreferenceRowsToggleAndPersistAcrossRecreation() {
        compose.onNodeWithContentDescription("Open setup").performClick()
        val prefs = Preferences(compose.activity)
        val oldBubble = prefs.showBubble
        val oldSpoken = prefs.spokenFeedback
        try {
            compose.onNodeWithText("Floating microphone").performScrollTo().performClick()
            compose.onNodeWithText("Spoken feedback").performScrollTo().performClick()
            compose.runOnIdle {
                org.junit.Assert.assertEquals(!oldBubble, prefs.showBubble)
                org.junit.Assert.assertEquals(!oldSpoken, prefs.spokenFeedback)
            }
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("Floating microphone").performScrollTo()
                .assert(if (oldBubble) isOff() else isOn())
            compose.onNodeWithText("Spoken feedback").performScrollTo()
                .assert(if (oldSpoken) isOff() else isOn())
        } finally {
            compose.runOnUiThread { prefs.showBubble = oldBubble; prefs.spokenFeedback = oldSpoken }
        }
    }

    @Test fun googleExampleAndEntireGuideRemainReachable() {
        compose.onNodeWithText("Search Google for coffee").performScrollTo().performClick()
        listOf("Open Instagram", "Open YouTube", "Search Google for coffee nearby",
            "Search YouTube for cooking", "Next reel", "Previous reel", "Swipe left / Swipe right",
            "Go back", "Go home", "Recent apps", "Show grid", "Zoom cell 5", "Tap cell 5", "Long press cell 5", "Grid back / Hide grid", "Tap Search", "Long press a label", "Type your words", "Replace text with your words", "Clear text", "Select all", "Cancel").forEach {
            compose.onNodeWithText("“" + it + "”").performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("Home", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("Tap to speak a command").assertIsDisplayed()
    }
}


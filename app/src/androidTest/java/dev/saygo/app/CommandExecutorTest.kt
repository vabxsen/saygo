package dev.saygo.app

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import dev.saygo.app.commands.*
import dev.saygo.app.data.SessionState
import org.junit.Assert.*
import org.junit.Test

class CommandExecutorTest {
    private class RecordingContext(base: Context) : ContextWrapper(base) {
        var launched: Intent? = null
        override fun startActivity(intent: Intent) { launched = intent }
    }
    @Test fun searchQueryCannotInjectUrlParametersOrChangeTheHost() {
        val context = RecordingContext(InstrumentationRegistry.getInstrumentation().targetContext)
        val query = "cats & admin=true # / café"
        CommandExecutor(context).execute(Command.Search(SearchProvider.GOOGLE, query))
        assertEquals("www.google.com", context.launched?.data?.host)
        assertEquals(query, context.launched?.data?.getQueryParameter("q"))
        assertNull(context.launched?.data?.getQueryParameter("admin"))
    }
    @Test fun unknownAppReportsFailureInsteadOfLaunchingAnUnrelatedApp() {
        val context = RecordingContext(InstrumentationRegistry.getInstrumentation().targetContext)
        CommandExecutor(context).execute(Command.OpenApp("definitely.not.installed.saygo.test"))
        assertNull(context.launched)
        assertFalse(SessionState.feedback.value.success)
    }

    @Test fun youtubeSearchRetainsTheQueryAndUsesTheCorrectEndpoint() {
        val context = RecordingContext(InstrumentationRegistry.getInstrumentation().targetContext)
        val query = "café & C++ # music"
        CommandExecutor(context).execute(Command.Search(SearchProvider.YOUTUBE, query))
        assertEquals("https", context.launched?.data?.scheme)
        assertEquals("www.youtube.com", context.launched?.data?.host)
        assertEquals("/results", context.launched?.data?.path)
        assertEquals(query, context.launched?.data?.getQueryParameter("search_query"))
        assertEquals(Intent.ACTION_VIEW, context.launched?.action)
        assertTrue(SessionState.feedback.value.success)
    }

    @Test fun anInstalledAppResolvesToItsOwnLauncher() {
        val context = RecordingContext(InstrumentationRegistry.getInstrumentation().targetContext)
        CommandExecutor(context).execute(Command.OpenApp("saygo"))
        assertEquals("dev.saygo.app", context.launched?.component?.packageName)
        assertTrue(SessionState.feedback.value.success)
    }

    @Test fun missingBrowserAndSecurityFailuresBecomeVisibleFeedback() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        listOf(android.content.ActivityNotFoundException(), SecurityException()).forEach { error ->
            val context = object : ContextWrapper(base) {
                override fun startActivity(intent: Intent) { throw error }
            }
            val before = SessionState.feedback.value.sequence
            CommandExecutor(context).execute(Command.Search(SearchProvider.GOOGLE, "coffee"))
            assertTrue(SessionState.feedback.value.sequence > before)
            assertFalse(SessionState.feedback.value.success)
        }
    }

    @Test fun exactInstalledConjunctionLabelsLaunchOnlyOneApp() {
        val context = RecordingContext(InstrumentationRegistry.getInstrumentation().targetContext)
        val executor = CommandExecutor(context)
        listOf("Dungeons and Dragons", "Then and Now").forEach { name ->
            val command = executor.parse("Open $name")
            assertEquals(Command.OpenApp(name), command)
            executor.execute(command!!)
            assertEquals("dev.saygo.app.test", context.launched?.component?.packageName)
        }
        assertNull(executor.parse("Open Dungeons and Dragons then go home"))
        assertNull(executor.parse("Open Instagram and scroll down"))
    }

    @Test fun cancellationDoesNotLaunchAnything() {
        val context = RecordingContext(InstrumentationRegistry.getInstrumentation().targetContext)
        CommandExecutor(context).execute(Command.Stop)
        assertNull(context.launched)
        assertEquals("Cancelled. Nothing changed.", SessionState.feedback.value.title)
    }
}

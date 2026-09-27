package dev.saygo.app

import android.content.Intent
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import dev.saygo.app.commands.Command
import dev.saygo.app.commands.CommandExecutor
import dev.saygo.app.commands.SearchProvider
import dev.saygo.app.data.SessionState
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.After
import android.os.ParcelFileDescriptor

/** Actual platform routing; URL contents are independently checked by CommandExecutorTest. */
class CommandRoutingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val automation get() = instrumentation.uiAutomation

    private var previousYouTubeNotifications: Boolean? = null

    @Before fun isolateReceivingApps() {
        val info = automation.serviceInfo
        info.flags = info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        automation.serviceInfo = info
        // Keep an external first-run notification dialog out of the routing assertion.
        // Restore the original permission afterward; never alter user data or notification content.
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            previousYouTubeNotifications = context.packageManager.checkPermission(permission, "com.google.android.youtube") == android.content.pm.PackageManager.PERMISSION_GRANTED
            shell("pm grant com.google.android.youtube android.permission.POST_NOTIFICATIONS")
            assertEquals(android.content.pm.PackageManager.PERMISSION_GRANTED,
                context.packageManager.checkPermission(permission, "com.google.android.youtube"))
        }
        stopReceivingApps()
    }
    @After fun finishReceivingApps() {
        stopReceivingApps()
        if (previousYouTubeNotifications == false) shell("pm revoke com.google.android.youtube android.permission.POST_NOTIFICATIONS")
    }
    private fun stopReceivingApps() {
        // YouTube can asynchronously hand a web link to Chrome. Stop both between cases.
        shell("am force-stop com.google.android.youtube")
        shell("am force-stop com.android.chrome")
    }
    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))
            .bufferedReader().use { it.readText() }
    }
    @Test fun opensInstalledYouTubeThroughTheRealLauncher() {
        launch(Command.OpenApp("YouTube"), setOf("com.google.android.youtube"))
    }
    @Test fun googleSearchReachesTheBrowser() {
        launch(Command.Search(SearchProvider.GOOGLE, "coffee"), setOf("com.android.chrome"))
    }
    @Test fun youtubeSearchReachesYouTubeOrTheBrowser() {
        launch(Command.Search(SearchProvider.YOUTUBE, "cooking"), setOf("com.google.android.youtube", "com.android.chrome"))
    }
    private fun launch(command: Command, receivers: Set<String>) {
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        await("saygo foreground") { foreground() == context.packageName }
        instrumentation.runOnMainSync { CommandExecutor(context).execute(command) }
        assertTrue(SessionState.feedback.value.title, SessionState.feedback.value.success)
        await("expected receiving app $receivers") {
            val root = automation.rootInActiveWindow
            if (root?.packageName?.toString() == "com.google.android.permissioncontroller" &&
                root.findAccessibilityNodeInfosByText("YouTube").isNotEmpty() &&
                root.findAccessibilityNodeInfosByText("notifications").isNotEmpty()) {
                root.findAccessibilityNodeInfosByViewId("com.google.android.permissioncontroller:id/permission_deny_button")
                    .firstOrNull()?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
            }
            root?.packageName?.toString() in receivers
        }
    }
    private fun foreground() = automation.rootInActiveWindow?.packageName?.toString()
    private fun await(label: String, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 30000
        while (SystemClock.uptimeMillis() < end) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        fail(label + "; foreground=" + foreground())
    }
}
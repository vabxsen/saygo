package dev.saygo.app

import android.app.Activity
import android.app.Instrumentation
import android.app.UiAutomation
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Base64
import dev.saygo.app.commands.CommandExecutor
import dev.saygo.app.control.PhoneControlService
import dev.saygo.app.data.Preferences
import dev.saygo.app.data.SessionState

/** Manual, dedicated-emulator probe. Executes only the explicitly supplied commands.
 * It bypasses speech/UI and checks dispatch, not the receiving app's visible outcome.
 * Does not collect screen contents or choose subsequent actions.
 */
class ExplicitCommandProbe : Instrumentation() {
    private var encodedCommands = ""
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        encodedCommands = arguments?.getString("commandsBase64").orEmpty()
        start()
    }

    override fun onStart() {
        val output = Bundle()
        val trace = mutableListOf<String>()
        val automation = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
            automation.executeShellCommand(command),
        ).bufferedReader().use { it.readText().trim() }
        fun await(label: String, condition: () -> Boolean) {
            val deadline = SystemClock.uptimeMillis() + 10_000
            while (!condition() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
            check(condition()) { "Timed out: $label" }
        }
        fun restoreSetting(key: String, value: String) {
            if (value == "null" || value.isBlank()) shell("settings delete secure $key")
            else {
                require(value.matches(Regex("[a-zA-Z0-9_.$/:]+")))
                shell("settings put secure $key $value")
            }
        }
        val prefs = Preferences(targetContext)
        val consent = prefs.controlConsent
        val bubble = prefs.showBubble
        val services = shell("settings get secure enabled_accessibility_services")
        val enabled = shell("settings get secure accessibility_enabled")
        var passed = false
        try {
            val commands = String(Base64.decode(encodedCommands, Base64.DEFAULT), Charsets.UTF_8)
                .lines().filter { it.isNotBlank() }
            require(commands.size in 1..10) { "Supply 1–10 explicit commands" }
            val executor = CommandExecutor(targetContext)
            // Validate every command before executing any of them.
            val parsed = commands.map { checkNotNull(executor.parse(it)) { "Unsupported command: $it" } }
            val otherServices = services.split(':').filter {
                it != "null" && it.isNotBlank() && !it.startsWith("dev.saygo.app/")
            }.joinToString(":")
            restoreSetting("enabled_accessibility_services", otherServices)
            await("previous service disconnected") { PhoneControlService.current == null }
            prefs.controlConsent = true
            prefs.showBubble = false
            restoreSetting("enabled_accessibility_services", listOf(
                otherServices, "dev.saygo.app/dev.saygo.app.control.PhoneControlService",
            ).filter { it.isNotBlank() }.joinToString(":"))
            shell("settings put secure accessibility_enabled 1")
            await("service connected") { PhoneControlService.current != null }
            parsed.forEachIndexed { index, command ->
                val root = automation.rootInActiveWindow
                val origin = root?.packageName?.toString()
                @Suppress("DEPRECATION")
                root?.recycle()
                val before = SessionState.feedback.value.sequence
                runOnMainSync { executor.execute(command, origin) }
                await("command feedback") { SessionState.feedback.value.sequence > before }
                val feedback = SessionState.feedback.value
                trace += "${commands[index]} => ${feedback.title} (success=${feedback.success}, origin=$origin)"
                check(feedback.success) { feedback.title }
                // Allows the operator to inspect the resulting UI with an external tool.
                SystemClock.sleep(6_000)
            }
            passed = true
        } catch (failure: Exception) {
            trace += "failure=${failure.message}"
        } finally {
            runOnMainSync { PhoneControlService.current?.onInterrupt() }
            restoreSetting("enabled_accessibility_services", services)
            restoreSetting("accessibility_enabled", enabled)
            prefs.controlConsent = consent
            prefs.showBubble = bubble
            output.putString("trace", trace.joinToString("\n"))
            output.putBoolean("dispatchPassed", passed)
            finish(if (passed) Activity.RESULT_OK else Activity.RESULT_CANCELED, output)
        }
    }
}

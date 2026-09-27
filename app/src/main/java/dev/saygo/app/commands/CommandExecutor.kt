package dev.saygo.app.commands

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.saygo.app.control.PhoneControlService
import dev.saygo.app.data.SessionState
import java.util.Locale

class CommandExecutor(private val context: Context) {
    fun parse(transcript: String): Command? = CommandParser.parse(transcript) { name ->
        launcherApps().any { it.loadLabel(context.packageManager).toString().equals(name, ignoreCase = true) }
    }

    private fun launcherApps() = context.packageManager.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0,
    )

    fun execute(command: Command, originPackage: String? = null) {
        try {
            when (command) {
                is Command.OpenApp -> openApp(command.name)
                is Command.Search -> search(command)
                is Command.Swipe, is Command.Navigate -> {
                    val service = PhoneControlService.current
                    if (service == null) SessionState.report("Enable phone controls in Setup first.", false)
                    else service.executeWhenReady(command, originPackage)
                }
                Command.Stop -> SessionState.report("Cancelled. Nothing changed.")
            }
        } catch (_: ActivityNotFoundException) {
            SessionState.report("No app is available to open this. Install a browser or the requested app.", false)
        } catch (_: SecurityException) {
            SessionState.report("Android blocked this action. Check the app’s permissions.", false)
        }
    }

    private fun openApp(name: String) {
        val pm = context.packageManager
        val normalized = name.lowercase(Locale.ROOT)
        val known = mapOf("instagram" to "com.instagram.android", "youtube" to "com.google.android.youtube", "chrome" to "com.android.chrome", "google" to "com.google.android.googlequicksearchbox")
        val apps = launcherApps()
        val matches = apps.filter {
            it.loadLabel(pm).toString().equals(name, ignoreCase = true) || it.activityInfo.packageName == known[normalized]
        }.distinctBy { it.activityInfo.packageName }
        when {
            matches.isEmpty() -> SessionState.report("Couldn’t find “$name”. Say the installed app’s full name.", false)
            matches.size > 1 -> SessionState.report("More than one app is called “$name”. Open it manually to choose.", false)
            else -> {
                val app = matches.single()
                val launch = pm.getLaunchIntentForPackage(app.activityInfo.packageName)
                if (launch == null) SessionState.report("This app cannot be launched directly.", false)
                else {
                    context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    SessionState.report("Opened ${app.loadLabel(pm)}.")
                }
            }
        }
    }

    private fun search(command: Command.Search) {
        val uri = when (command.provider) {
            SearchProvider.GOOGLE -> Uri.Builder().scheme("https").authority("www.google.com").path("search").appendQueryParameter("q", command.query).build()
            SearchProvider.YOUTUBE -> Uri.Builder().scheme("https").authority("www.youtube.com").path("results").appendQueryParameter("search_query", command.query).build()
        }
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        SessionState.report("Opened ${if (command.provider == SearchProvider.GOOGLE) "Google" else "YouTube"} search.")
    }
}

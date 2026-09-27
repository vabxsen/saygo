package dev.saygo.app.commands

sealed interface Command {
    data class OpenApp(val name: String) : Command
    data class Search(val provider: SearchProvider, val query: String) : Command
    data class Swipe(val direction: Direction) : Command
    data class Pinch(val zoomIn: Boolean) : Command
    data class Navigate(val destination: Destination) : Command
    data class DeviceControl(val action: DeviceAction) : Command
    data class Tap(val label: String, val longPress: Boolean = false) : Command
    data class EditText(val operation: TextOperation, val text: String = "") : Command
    data class Grid(val operation: GridOperation, val cell: Int? = null, val destinationCell: Int? = null) : Command
    data object Stop : Command
}

enum class GridOperation { SHOW, HIDE, ZOOM, BACK, TAP, LONG_PRESS, DRAG }

enum class TextOperation { INSERT, REPLACE, CLEAR, SELECT_ALL }

enum class SearchProvider { GOOGLE, YOUTUBE }
enum class Direction { UP, DOWN, LEFT, RIGHT }
enum class Destination { BACK, HOME, RECENTS }
enum class DeviceAction { NOTIFICATIONS, QUICK_SETTINGS, MEDIA_UP, MEDIA_DOWN, MEDIA_MUTE, MEDIA_UNMUTE }

/** Deliberately finite grammar: one utterance maps to at most one predefined action. */
object CommandParser {
    fun parse(transcript: String, isInstalledAppName: (String) -> Boolean = { false }): Command? {
        // Dictation is literal data: preserve punctuation and internal whitespace.
        val raw = transcript.trim()
        if (raw.length > 2000) return null
        Regex("^(type|replace text with)\\s+(.+)$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).matchEntire(raw)?.let {
            return Command.EditText(if (it.groupValues[1].equals("type", true)) TextOperation.INSERT else TextOperation.REPLACE, it.groupValues[2])
        }
        val text = transcript.trim().replace(Regex("\\s+"), " ").trimEnd('.', '!', '?')
        if (text.isEmpty() || text.length > 240) return null
        val key = text.lowercase(java.util.Locale.ROOT)
        when (key) {
            "open notifications", "show notifications" -> return Command.DeviceControl(DeviceAction.NOTIFICATIONS)
            "open quick settings", "show quick settings" -> return Command.DeviceControl(DeviceAction.QUICK_SETTINGS)
            "volume up", "media volume up" -> return Command.DeviceControl(DeviceAction.MEDIA_UP)
            "volume down", "media volume down" -> return Command.DeviceControl(DeviceAction.MEDIA_DOWN)
            "mute media" -> return Command.DeviceControl(DeviceAction.MEDIA_MUTE)
            "unmute media" -> return Command.DeviceControl(DeviceAction.MEDIA_UNMUTE)
            "zoom in", "pinch out" -> return Command.Pinch(true)
            "zoom out", "pinch in" -> return Command.Pinch(false)
            "show grid" -> return Command.Grid(GridOperation.SHOW)
            "hide grid", "dismiss grid" -> return Command.Grid(GridOperation.HIDE)
            "grid back" -> return Command.Grid(GridOperation.BACK)
            "clear text" -> return Command.EditText(TextOperation.CLEAR)
            "select all" -> return Command.EditText(TextOperation.SELECT_ALL)
            "stop", "cancel", "never mind" -> return Command.Stop
            "go back", "back" -> return Command.Navigate(Destination.BACK)
            "go home", "home", "home screen" -> return Command.Navigate(Destination.HOME)
            "recent apps", "show recent apps", "open recent apps" -> return Command.Navigate(Destination.RECENTS)
            "next reel", "next video", "scroll down", "swipe up" -> return Command.Swipe(Direction.UP)
            "previous reel", "previous video", "scroll up", "swipe down" -> return Command.Swipe(Direction.DOWN)
            "swipe left" -> return Command.Swipe(Direction.LEFT)
            "swipe right" -> return Command.Swipe(Direction.RIGHT)
        }
        if (key.startsWith("drag ")) {
            val cellWord = "([1-9]|one|two|three|four|five|six|seven|eight|nine)"
            val match = Regex("^drag cell $cellWord to cell $cellWord$").matchEntire(key) ?: return null
            fun cell(value: String) = value.toIntOrNull() ?:
                (listOf("one", "two", "three", "four", "five", "six", "seven", "eight", "nine").indexOf(value) + 1)
            val start = cell(match.groupValues[1]); val end = cell(match.groupValues[2])
            return if (start == end) null else Command.Grid(GridOperation.DRAG, start, end)
        }
        if (Regex("^(zoom|tap|long press) cell(?: |$)").containsMatchIn(key)) {
            val match = Regex("^(zoom|tap|long press) cell ([1-9]|one|two|three|four|five|six|seven|eight|nine)$").matchEntire(key) ?: return null
            val number = match.groupValues[2].toIntOrNull() ?: (listOf("one", "two", "three", "four", "five", "six", "seven", "eight", "nine").indexOf(match.groupValues[2]) + 1)
            return Command.Grid(when (match.groupValues[1]) {
                "zoom" -> GridOperation.ZOOM
                "tap" -> GridOperation.TAP
                else -> GridOperation.LONG_PRESS
            }, number)
        }
        Regex("^(tap|long press) (.+)$", RegexOption.IGNORE_CASE).matchEntire(text)?.let {
            return Command.Tap(it.groupValues[2], it.groupValues[1].equals("long press", true))
        }
        Regex("^search (google|youtube) for (.+)$", RegexOption.IGNORE_CASE).matchEntire(text)?.let {
            return Command.Search(
                if (it.groupValues[1].equals("google", true)) SearchProvider.GOOGLE else SearchProvider.YOUTUBE,
                it.groupValues[2].trim(),
            )
        }
        Regex("^(?:open|launch) (.+)$", RegexOption.IGNORE_CASE).matchEntire(text)?.let {
            val name = it.groupValues[1].trim()
            // A conjunction can belong to one exact installed label, never a sequence of actions.
            if (Regex("\\b(?:and|then)\\b", RegexOption.IGNORE_CASE).containsMatchIn(name) && !isInstalledAppName(name)) return null
            return Command.OpenApp(name)
        }
        return null
    }
}

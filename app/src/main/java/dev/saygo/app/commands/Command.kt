package dev.saygo.app.commands

sealed interface Command {
    data class OpenApp(val name: String) : Command
    data class Search(val provider: SearchProvider, val query: String) : Command
    data class Swipe(val direction: Direction) : Command
    data class Navigate(val destination: Destination) : Command
    data object Stop : Command
}

enum class SearchProvider { GOOGLE, YOUTUBE }
enum class Direction { UP, DOWN, LEFT, RIGHT }
enum class Destination { BACK, HOME, RECENTS }

/** Deliberately finite grammar: one utterance maps to at most one predefined action. */
object CommandParser {
    fun parse(transcript: String, isInstalledAppName: (String) -> Boolean = { false }): Command? {
        val text = transcript.trim().replace(Regex("\\s+"), " ").trimEnd('.', '!', '?')
        if (text.isEmpty() || text.length > 240) return null
        val key = text.lowercase(java.util.Locale.ROOT)
        when (key) {
            "stop", "cancel", "never mind" -> return Command.Stop
            "go back", "back" -> return Command.Navigate(Destination.BACK)
            "go home", "home", "home screen" -> return Command.Navigate(Destination.HOME)
            "recent apps", "show recent apps", "open recent apps" -> return Command.Navigate(Destination.RECENTS)
            "next reel", "next video", "scroll down", "swipe up" -> return Command.Swipe(Direction.UP)
            "previous reel", "previous video", "scroll up", "swipe down" -> return Command.Swipe(Direction.DOWN)
            "swipe left" -> return Command.Swipe(Direction.LEFT)
            "swipe right" -> return Command.Swipe(Direction.RIGHT)
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

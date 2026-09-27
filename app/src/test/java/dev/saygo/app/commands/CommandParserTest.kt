package dev.saygo.app.commands

import org.junit.Assert.*
import org.junit.Test

class CommandParserTest {
    @Test fun `launcher command preserves full app name`() {
        assertEquals(Command.OpenApp("YouTube Music"), CommandParser.parse("  open   YouTube Music  "))
        assertEquals(Command.OpenApp("Instagram"), CommandParser.parse("Launch Instagram!"))
    }
    @Test fun `search payload retains case and embedded punctuation`() {
        assertEquals(Command.Search(SearchProvider.GOOGLE, "C++ & Kotlin"), CommandParser.parse("search Google for C++ & Kotlin"))
        assertEquals(Command.Search(SearchProvider.YOUTUBE, "Cats and Dogs"), CommandParser.parse("SEARCH YOUTUBE FOR Cats and Dogs"))
    }
    @Test fun `every documented gesture maps to the physical swipe direction`() {
        mapOf(
            "next reel" to Direction.UP, "next video" to Direction.UP, "scroll down" to Direction.UP,
            "swipe up" to Direction.UP, "previous reel" to Direction.DOWN, "previous video" to Direction.DOWN,
            "scroll up" to Direction.DOWN, "swipe down" to Direction.DOWN,
            "swipe left" to Direction.LEFT, "swipe right" to Direction.RIGHT,
        ).forEach { (input, direction) -> assertEquals(input, Command.Swipe(direction), CommandParser.parse(input)) }
    }
    @Test fun `navigation and cancellation use exact phrases`() {
        listOf("back", "go back").forEach { assertEquals(Command.Navigate(Destination.BACK), CommandParser.parse(it)) }
        listOf("home", "go home", "home screen").forEach { assertEquals(Command.Navigate(Destination.HOME), CommandParser.parse(it)) }
        listOf("recent apps", "show recent apps", "open recent apps").forEach { assertEquals(Command.Navigate(Destination.RECENTS), CommandParser.parse(it)) }
        listOf("stop", "cancel", "never mind").forEach { assertEquals(Command.Stop, CommandParser.parse(it)) }
    }
    @Test fun `arbitrary or chained instructions never execute`() {
        listOf("", "  ", "open", "search Google for", "buy milk", "I want you to go home",
            "go home then open Instagram", "open Instagram and scroll down", "scroll down twice", "keep scrolling", "a".repeat(241)
        ).forEach { assertNull(inputDescription(it), CommandParser.parse(it)) }
    }
    @Test fun `punctuation and casing from speech recognizers are tolerated`() {
        assertEquals(Command.Navigate(Destination.HOME), CommandParser.parse("  GO HOME.  "))
        assertEquals(Command.Swipe(Direction.UP), CommandParser.parse("Next Reel!"))
    }
    @Test fun `search content is data not a second instruction`() {
        assertEquals(Command.Search(SearchProvider.GOOGLE, "go home then open Instagram"), CommandParser.parse("search Google for go home then open Instagram"))
    }
    @Test fun `conjunctions are accepted only for exact installed app labels`() {
        val names = listOf("Dungeons and Dragons", "Then and Now")
        val installed: (String) -> Boolean = { input -> names.any { it.equals(input, true) } }
        names.forEach { assertEquals(Command.OpenApp(it), CommandParser.parse("Open $it", installed)) }
        assertEquals(Command.OpenApp("then and now"), CommandParser.parse("open then and now", installed))
        listOf("Open Instagram and scroll down", "Open Then and Now then go home", "Open unknown and unknown").forEach {
            assertNull(CommandParser.parse(it, installed))
        }
    }
    @Test fun `explicit tap and edit commands stay single actions`() {
        assertEquals(Command.Tap("Search"), CommandParser.parse("Tap Search"))
        assertEquals(Command.Tap("Play media", true), CommandParser.parse("Long press Play media"))
        assertEquals(Command.Tap("Search then Send"), CommandParser.parse("Tap Search then Send"))
        assertEquals(Command.EditText(TextOperation.CLEAR), CommandParser.parse("clear text"))
        assertEquals(Command.EditText(TextOperation.SELECT_ALL), CommandParser.parse("select all"))
        assertNull(CommandParser.parse("tap"))
        assertNull(CommandParser.parse("type"))
        assertNull(CommandParser.parse("clear text and go home"))
    }
    @Test fun `dictation preserves literal punctuation case and instructions`() {
        val text = "Hello,  world! Then tap Send."
        assertEquals(Command.EditText(TextOperation.INSERT, text), CommandParser.parse("Type " + text))
        assertEquals(Command.EditText(TextOperation.REPLACE, "C++ & café?"), CommandParser.parse("Replace text with C++ & café?"))
        assertNull(CommandParser.parse("type " + "x".repeat(2001)))
    }
    @Test fun `grid commands are explicit and bounded`() {
        assertEquals(Command.Grid(GridOperation.SHOW), CommandParser.parse("Show grid!"))
        assertEquals(Command.Grid(GridOperation.HIDE), CommandParser.parse("dismiss grid"))
        assertEquals(Command.Grid(GridOperation.BACK), CommandParser.parse("grid back"))
        assertEquals(Command.Grid(GridOperation.ZOOM, 5), CommandParser.parse("zoom cell five"))
        assertEquals(Command.Grid(GridOperation.TAP, 2), CommandParser.parse("tap cell 2"))
        assertEquals(Command.Grid(GridOperation.LONG_PRESS, 9), CommandParser.parse("long press cell nine"))
        listOf("tap cell 0", "tap cell 10", "zoom cell", "tap cell 5 then 6", "show grid and tap cell 2").forEach { assertNull(it, CommandParser.parse(it)) }
        assertEquals(Command.Tap("5"), CommandParser.parse("tap 5"))
        assertEquals(Command.EditText(TextOperation.INSERT, "tap cell 5"), CommandParser.parse("type tap cell 5"))
    }
    @Test fun `pinch words preserve the physical direction and reject sequences`() {
        listOf("Zoom in!", "pinch out").forEach { assertEquals(Command.Pinch(true), CommandParser.parse(it)) }
        listOf("zoom out", "PINCH IN").forEach { assertEquals(Command.Pinch(false), CommandParser.parse(it)) }
        listOf("zoom in twice", "zoom out then tap Search", "pinch", "zoom in 50 percent").forEach { assertNull(it, CommandParser.parse(it)) }
        assertEquals(Command.Grid(GridOperation.ZOOM, 5), CommandParser.parse("zoom cell 5"))
        assertEquals(Command.EditText(TextOperation.INSERT, "zoom in"), CommandParser.parse("type zoom in"))
    }
    private fun inputDescription(value: String) = "Should reject: $value"
}

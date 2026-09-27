package dev.saygo.app.commands

import org.junit.Assert.*
import org.junit.Test

/** Characterization probes: PASS confirms a current defect; not a desired contract. */
class RescanParserProbe {
    @Test fun legitimateAppNamesWithConjunctionsAreRejected() {
        listOf("Dungeons and Dragons", "Then and Now").forEach { name ->
            assertNull(CommandParser.parse("Open $name"))
        }
    }
    @Test fun meaningfulTrailingSearchPunctuationIsLost() {
        assertEquals(Command.Search(SearchProvider.GOOGLE, "hello"), CommandParser.parse("Search Google for hello!"))
        assertEquals(Command.Search(SearchProvider.GOOGLE, "example"), CommandParser.parse("Search Google for example..."))
    }
}

package dev.saygo.app.speech

import org.junit.Assert.*
import org.junit.Test

class FeedbackQueueTest {
    private var enabled = true
    private val spoken = mutableListOf<String>()
    private var stops = 0
    private val queue = FeedbackQueue({ enabled }, { spoken.add(it) }, { stops++ })

    @Test fun delayedInitializationCannotSpeakAfterNewListeningStarts() {
        queue.offer("Opened YouTube")
        queue.cancel()
        queue.engineReady(true)
        assertTrue(spoken.isEmpty())
        assertEquals(1, stops)
    }
    @Test fun onlyNewestPendingResultSpeaksWhenEngineBecomesReady() {
        queue.offer("First")
        queue.offer("Latest")
        queue.engineReady(true)
        assertEquals(listOf("Latest"), spoken)
    }
    @Test fun disablingFeedbackDuringInitializationSuppressesIt() {
        queue.offer("Opened YouTube")
        enabled = false
        queue.engineReady(true)
        assertTrue(spoken.isEmpty())
    }
    @Test fun disabledFeedbackIsNeverQueuedForLater() {
        enabled = false
        queue.offer("Old result")
        enabled = true
        queue.engineReady(true)
        assertTrue(spoken.isEmpty())
    }
    @Test fun failedInitializationDropsStaleResultButAllowsANewAttempt() {
        queue.offer("Old result")
        queue.engineReady(false)
        queue.offer("New result")
        queue.engineReady(true)
        assertEquals(listOf("New result"), spoken)
    }
    @Test fun readyEngineSpeaksEachNewResultAndCancelStopsIt() {
        queue.engineReady(true)
        queue.offer("New result")
        queue.cancel()
        assertEquals(listOf("New result"), spoken)
        assertEquals(1, stops)
    }
}

package com.twocents.mobile.ui.compose

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class PostPublishFollowUpTest {
    @Test fun slowOrFailedFollowUpDoesNotHoldPublicationOrFailItsCaller() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val notices = mutableListOf<String>()
        val job = launchPostPublishFollowUp("Post published, but refresh failed", { notices += it }) {
            release.await()
            error("Refresh failed after create succeeded")
        }
        yield()
        assertFalse(job.isCompleted)
        assertTrue(notices.isEmpty())
        release.complete(Unit)
        job.join()
        assertEquals(listOf("Post published, but refresh failed"), notices)
        assertFalse(job.isCancelled)
    }

    @Test fun uiFollowUpUsesItsSuppliedFrameClock() = runBlocking {
        val clock = BroadcastFrameClock()
        val ui = CoroutineScope(coroutineContext + clock)
        val notices = mutableListOf<String>()
        var frame = 0L
        val job = ui.launchPostPublishFollowUp("Unexpected UI failure", { notices += it }) {
            frame = withFrameNanos { it }
        }
        yield()
        clock.sendFrame(123L)
        job.join()
        assertEquals(123L, frame)
        assertTrue(notices.isEmpty())
    }
}

package com.twocents.mobile.ui.feed

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class FeedResultRequestsTest {
    @Test fun refreshRevalidatesAndConcurrentRequestsCoalesce() = runBlocking {
        val requests = FeedResultRequests()
        var calls = 0
        suspend fun load() = requests.load("poll:x", false, { calls++; yield(); calls }) {}
        coroutineScope { listOf(async { load() }, async { load() }).awaitAll() }
        assertEquals(1, calls)
        requests.invalidate()
        load()
        assertEquals(2, calls)
    }

    @Test fun staleInflightResultCannotOverwriteRefreshedData() = runBlocking {
        val requests = FeedResultRequests()
        val release = CompletableDeferred<Unit>()
        var published = 0
        val old = async(start = CoroutineStart.UNDISPATCHED) {
            requests.load("pick:x", false, { release.await(); 1 }) { published = it }
        }
        requests.invalidate()
        val fresh = async { requests.load("pick:x", false, { 2 }) { published = it } }
        release.complete(Unit)
        assertFalse(old.await())
        assertTrue(fresh.await())
        assertEquals(2, published)
    }

    @Test fun failuresDoNotPoisonCache() = runBlocking {
        val requests = FeedResultRequests()
        assertFalse(requests.load("likert:x", false, { error("offline") }) { _: Int -> })
        assertTrue(requests.load("likert:x", false, { 1 }) {})
    }
}

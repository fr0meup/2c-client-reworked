package com.twocents.mobile.ui.profile

import com.twocents.mobile.ApiException
import com.twocents.mobile.retryAfterMillis
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ScanRequestPacerTest {
    @Test fun requestsAreSpacedAndServerCooldownIsRespected() = runBlocking {
        var clock = 0L
        val waits = mutableListOf<Long>()
        val messages = mutableListOf<String?>()
        val pacer = ScanRequestPacer(messages::add, { clock }, { waits += it; clock += it }, { 0 })
        pacer.execute { true }
        pacer.execute { true }
        assertEquals(250L, clock)
        var attempts = 0
        val result = pacer.execute {
            if (attempts++ == 0) throw ApiException("rate limited", retryAfterMillis = 90_000)
            "complete"
        }
        assertEquals("complete", result)
        assertTrue(waits.contains(90_000))
        assertTrue(messages.any { it?.contains("pausing") == true })
        assertNull(messages.last())
    }

    @Test fun permanentErrorsAreNotRetried() = runBlocking {
        var attempts = 0
        val pacer = ScanRequestPacer(now = { 0 }, sleep = {}, jitter = { 0 })
        try { pacer.execute { attempts++; throw IllegalArgumentException("invalid user") } }
        catch (_: IllegalArgumentException) { }
        assertEquals(1, attempts)
    }

    @Test fun retryAfterAcceptsSecondsAndHttpDates() {
        assertEquals(30_000L, retryAfterMillis("30"))
        assertEquals(60_000L, retryAfterMillis("Thu, 1 Jan 1970 00:01:00 GMT", 0))
        assertNull(retryAfterMillis("invalid"))
    }

    @Test fun repeatedLimitsStopAfterBoundedRetries() = runBlocking {
        var clock = 0L
        var attempts = 0
        val pacer = ScanRequestPacer(now = { clock }, sleep = { clock += it }, jitter = { 0 })
        try {
            pacer.execute { attempts++; throw ApiException("rate limited") }
            fail("A rejected request must not be converted to success")
        } catch (_: ApiException) { }
        assertEquals(5, attempts)
        assertTrue(clock >= 30_000 + 60_000 + 120_000 + 240_000)
    }
}

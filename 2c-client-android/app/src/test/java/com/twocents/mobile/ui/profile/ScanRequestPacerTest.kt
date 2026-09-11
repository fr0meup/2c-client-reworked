package com.twocents.mobile.ui.profile

import com.twocents.mobile.ApiException
import com.twocents.mobile.retryAfterMillis
import com.twocents.mobile.RpcRequestPolicy
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class ScanRequestPacerTest {
    @Test fun scanPolicyDoesNotLeakIntoOrdinaryRequests() = runBlocking {
        val pacer = ScanRequestPacer()
        assertNull(coroutineContext[RpcRequestPolicy])
        withContext(pacer) { assertSame(pacer, coroutineContext[RpcRequestPolicy]) }
        assertNull(coroutineContext[RpcRequestPolicy])
    }

    @Test fun normalRequestsHaveNoFixedDelayButServerCooldownIsRespected() = runBlocking {
        var clock = 0L
        val waits = mutableListOf<Long>()
        val messages = mutableListOf<String?>()
        val pacer = ScanRequestPacer(messages::add, { clock }, { waits += it; clock += it }, { 0 })
        pacer.execute { true }
        pacer.execute { true }
        assertEquals(0L, clock)
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

    @Test fun healthyScanDoesNotAccumulatePerRequestWaiting() = runBlocking {
        var clock = 0L
        val pacer = ScanRequestPacer(now = { clock }, sleep = { clock += it }, jitter = { 100 })
        repeat(1000) { pacer.execute { true } }
        assertEquals("Phase concurrency, not fixed sleeps, controls normal speed", 0L, clock)
    }

    @Test fun successfulRecoveryRemovesTemporaryThrottling() = runBlocking {
        var clock = 0L
        var first = true
        val pacer = ScanRequestPacer(now = { clock }, sleep = { clock += it }, jitter = { 0 })
        pacer.execute {
            if (first) { first = false; throw ApiException("rate limited") }
            true
        }
        repeat(21) { pacer.execute { true } }
        val recoveredAt = clock
        repeat(100) { pacer.execute { true } }
        assertEquals(recoveredAt, clock)
    }
}

package com.twocents.mobile.ui.profile

import com.twocents.mobile.ApiException
import com.twocents.mobile.ApiRateLimitNotice
import com.twocents.mobile.RpcRequestPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlin.random.Random

/** One shared budget for every discovery phase, including the 20 index workers.
 * Stagger starts rather than releasing synchronized bursts. A 429 pauses all
 * workers, then resumes slowly; no request is treated as a negative follower. */
internal class ScanRequestPacer(
    private val onPause: (String?) -> Unit = {},
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val jitter: () -> Long = { Random.nextLong(0, 101) },
) : RpcRequestPolicy() {
    private val slots = Semaphore(4)
    private val lock = Mutex()
    private var nextStart = 0L
    private var cooldownUntil = 0L
    private var interval = 250L
    private var cooldown = 30_000L
    private var successes = 0

    override suspend fun <T> execute(request: suspend () -> T): T = slots.withPermit {
        var attempts = 0
        while (true) {
            // Recheck after sleeping so a newly reported shared cooldown also
            // stops workers already waiting for their normal admission slot.
            while (true) {
                val wait = lock.withLock {
                    val time = now()
                    val remaining = maxOf(nextStart, cooldownUntil) - time
                    if (remaining <= 0) {
                        nextStart = time + interval + jitter()
                        onPause(null)
                    }
                    remaining
                }
                if (wait <= 0) break
                sleep(wait)
            }
            try {
                val result = request()
                lock.withLock {
                    successes++
                    if (successes >= 40) {
                        interval = (interval - 50).coerceAtLeast(250)
                        successes = 0
                    }
                }
                return@withPermit result
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (!ApiRateLimitNotice.matches(error.message.orEmpty()) && !error.message.orEmpty().contains("429")) throw error
                lock.withLock {
                    val time = now()
                    val serverWait = (error as? ApiException)?.retryAfterMillis ?: 0L
                    // Concurrent rejected responses share a single backoff cycle.
                    if (cooldownUntil <= time) {
                        cooldownUntil = time + maxOf(cooldown, serverWait) + jitter()
                        cooldown = (cooldown * 2).coerceAtMost(300_000)
                        interval = (interval * 2).coerceAtMost(2_000)
                        successes = 0
                    } else cooldownUntil = maxOf(cooldownUntil, time + serverWait)
                    onPause("Rate limited — pausing the scan before retrying…")
                }
                if (++attempts >= 5) throw error
            }
        }
        @Suppress("UNREACHABLE_CODE")
        error("Unreachable")
    }
}

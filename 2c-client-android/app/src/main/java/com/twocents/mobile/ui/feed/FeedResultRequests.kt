package com.twocents.mobile.ui.feed

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Per-controller result freshness, independent from the feed-page cache.
 * A refresh invalidates completed requests without flashing away existing results.
 * Old in-flight responses cannot overwrite results from the new refresh cycle. */
internal class FeedResultRequests {
    private val locks = Array(32) { Mutex() }
    private val completed = mutableMapOf<String, Long>()
    private var generation = 0L

    fun invalidate(): Long { generation++; completed.clear(); return generation }

    suspend fun <T> load(key: String, force: Boolean, fetch: suspend () -> T, publish: (T) -> Unit): Boolean =
        locks[(key.hashCode() and Int.MAX_VALUE) % locks.size].withLock {
            if (!force && completed[key] == generation) return@withLock true
            val started = generation
            try {
                val value = fetch()
                if (started != generation) return@withLock false
                publish(value)
                completed[key] = started
                true
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                false // Failed fetches stay retryable and never become cached successes.
            }
        }
}

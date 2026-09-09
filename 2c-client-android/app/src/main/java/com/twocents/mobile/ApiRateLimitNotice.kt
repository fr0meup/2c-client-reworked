package com.twocents.mobile

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Retain rate-limit state at the transport boundary, including caught background failures.
 * Parallel scan failures update one warning instead of producing a toast storm. */
internal object ApiRateLimitNotice {
    private val pending = mutableMapOf<String, Long>()
    private val mutableActive = MutableStateFlow(false)
    val active = mutableActive.asStateFlow()
    private const val Message = "You're temporarily rate limited by twocents. Wait a little before trying again."

    fun matches(message: String): Boolean = message.contains("rate limit", true) ||
        message.contains("rate-limit", true) || message.contains("too many requests", true) ||
        message.contains("throttl", true) || message.contains("rate exceeded", true)

    @Synchronized
    fun report(method: String): String {
        pending[method] = SystemClock.elapsedRealtime()
        mutableActive.value = true
        return Message
    }

    // A different endpoint succeeding (or an older in-flight response) does not
    // prove that the rejected operation can be retried successfully.
    @Synchronized
    fun succeeded(method: String, startedAt: Long) {
        pending[method]?.let { if (startedAt > it) pending.remove(method) }
        mutableActive.value = pending.isNotEmpty()
    }

    @Synchronized
    fun reset() {
        pending.clear()
        mutableActive.value = false
    }
}

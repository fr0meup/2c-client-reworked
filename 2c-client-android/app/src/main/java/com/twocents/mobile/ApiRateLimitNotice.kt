package com.twocents.mobile

import android.os.SystemClock
import com.twocents.mobile.ui.common.AppToast

/** Notify at the transport boundary: background callers often catch failures.
 * Coalesce parallel scan requests so one rate limit doesn't produce 20 toasts. */
internal object ApiRateLimitNotice {
    private var lastShown: Long? = null
    private const val Message = "You're temporarily rate limited by twocents. Wait a little before trying again."

    fun matches(message: String): Boolean = message.contains("rate limit", true) ||
        message.contains("rate-limit", true) || message.contains("too many requests", true) ||
        message.contains("throttl", true) || message.contains("rate exceeded", true)

    @Synchronized
    fun report(): String {
        val now = SystemClock.elapsedRealtime()
        if (lastShown == null || now - lastShown!! >= 15_000L) {
            lastShown = now
            AppToast.error(Message)
        }
        return Message
    }
}

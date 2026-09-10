package com.twocents.mobile

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Opt-in request scheduling inherited by child jobs; normal app traffic is unchanged. */
internal abstract class RpcRequestPolicy : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<RpcRequestPolicy>
    abstract suspend fun <T> execute(request: suspend () -> T): T
}

internal fun retryAfterMillis(value: String?, now: Long = System.currentTimeMillis()): Long? {
    val text = value?.trim() ?: return null
    text.toLongOrNull()?.takeIf { it >= 0 && it <= Long.MAX_VALUE / 1000 }?.let { return it * 1000 }
    return runCatching {
        (java.time.ZonedDateTime.parse(text, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
            .toInstant().toEpochMilli() - now).coerceAtLeast(0)
    }.getOrNull()
}

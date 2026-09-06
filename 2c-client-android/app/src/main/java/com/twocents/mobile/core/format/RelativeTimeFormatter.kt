package com.twocents.mobile.core.format

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

internal fun parseApiInstant(raw: String): Instant? = runCatching { Instant.parse(raw) }
    .getOrElse { runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull() }

internal fun compactTimeAgo(raw: String, now: Instant = Instant.now()): String {
    val then = parseApiInstant(raw) ?: return ""
    val minutes = ChronoUnit.MINUTES.between(then, now).coerceAtLeast(0)
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        minutes < 1_440 -> "${minutes / 60}h"
        minutes < 10_080 -> "${minutes / 1_440}d"
        else -> "${minutes / 10_080}w"
    }
}

internal fun exactLocalDate(raw: String, pattern: String = "MMM d, yyyy"): String =
    parseApiInstant(raw)?.atZone(ZoneId.systemDefault())?.let {
        DateTimeFormatter.ofPattern(pattern, Locale.US).format(it)
    }.orEmpty()

package com.twocents.mobile.core.format

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

internal fun formatCompactCount(value: Int): String = when {
    value >= 1_000_000 -> "${value / 1_000_000}M"
    value >= 1_000 -> "${value / 1_000}K"
    else -> value.toString()
}

internal fun formatExactNumber(value: Int): String = NumberFormat.getNumberInstance(Locale.US).format(value)

internal fun formatCompactNumber(value: Double): String = when {
    abs(value) >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000).replace(".0M", "M")
    abs(value) >= 1_000 -> String.format(Locale.US, "%.1fK", value / 1_000).replace(".0K", "K")
    else -> value.toInt().toString()
}

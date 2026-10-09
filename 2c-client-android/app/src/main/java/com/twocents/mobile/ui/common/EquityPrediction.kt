package com.twocents.mobile.ui.common

import androidx.compose.runtime.Immutable
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Creation sends only the target; identity, baseline, direction, and resolution are server data. */
@Immutable
data class EquityPrediction(
    val symbol: String,
    val targetPrice: Double,
    val targetDate: String,
    val name: String = symbol,
    val currentPrice: Double? = null,
    val direction: String? = null,
    val resolutionStatus: String = "open",
)

internal fun parseEquityPrediction(root: JSONObject?): EquityPrediction? {
    if (root == null) return null
    val symbol = root.optString("ticker").trim().uppercase(Locale.ROOT)
    val price = root.opt("targetPrice")?.toString()?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 } ?: return null
    val date = root.optString("targetDate")
    if (!validEquitySymbol(symbol) || runCatching { LocalDate.parse(date) }.isFailure) return null
    return EquityPrediction(symbol, price, date,
        root.optString("name").takeUnless { it.isBlank() || it == "null" } ?: symbol,
        root.opt("currentPrice")?.toString()?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 },
        root.optString("direction").takeIf { it == "above" || it == "below" },
        root.optString("resolutionStatus").takeUnless { it.isBlank() || it == "null" } ?: "open")
}

internal fun EquityPrediction.toPredictionRequest(): JSONObject = JSONObject()
    .put("ticker", symbol).put("targetPrice", targetPrice).put("targetDate", targetDate)

/** Drafts and quote fallbacks retain server state, without sending those fields as creation input. */
internal fun EquityPrediction.toPredictionJson(): JSONObject = toPredictionRequest()
    .put("name", name).put("resolutionStatus", resolutionStatus).apply {
        currentPrice?.let { put("currentPrice", it) }
        direction?.let { put("direction", it) }
    }

internal fun parsePredictionPrice(text: String): Double? = text.trim().replace(',', '.')
    .takeIf { Regex("[0-9]+(?:\\.[0-9]{0,6})?").matches(it) }?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }

internal fun predictionPresetPrice(price: Double, percent: Int): Double =
    BigDecimal.valueOf(price).multiply(BigDecimal.valueOf(100L + percent)).divide(BigDecimal.valueOf(100))
        .setScale(2, RoundingMode.HALF_UP).toDouble()

internal fun predictionPercent(prediction: EquityPrediction): Double? = prediction.currentPrice
    ?.takeIf { it.isFinite() && it > 0 }?.let { (prediction.targetPrice - it) / it * 100 }

internal fun validPredictionDate(date: LocalDate, today: LocalDate = LocalDate.now()): Boolean =
    date > today && date.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

/** Weekends are disabled like the official picker; exchange holidays remain a server concern. */
internal fun predictionPresetDate(period: String, today: LocalDate = LocalDate.now()): LocalDate {
    var day = when (period) { "1W" -> today.plusWeeks(1); "2W" -> today.plusWeeks(2); "1M" -> today.plusMonths(1); else -> today.plusDays(1) }
    while (!validPredictionDate(day, today)) day = day.plusDays(1)
    return day
}

internal fun predictionStatusLabel(prediction: EquityPrediction, today: LocalDate = LocalDate.now()): String {
    if (prediction.resolutionStatus.lowercase(Locale.ROOT) != "open") {
        return prediction.resolutionStatus.replace('_', ' ').replaceFirstChar { it.titlecase(Locale.ROOT) }
    }
    val date = runCatching { LocalDate.parse(prediction.targetDate) }.getOrNull() ?: return "Open"
    val days = ChronoUnit.DAYS.between(today, date)
    // Reaching the target date is not proof of success or failure: only the server resolves predictions.
    return when { days < 0 -> "Awaiting resolution"; days == 0L -> "Resolves today"; days == 1L -> "1 day left"; else -> "$days days left" }
}

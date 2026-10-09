package com.twocents.mobile.ui.common

import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** An attached chart is metadata, not a $-mention or a snapshot image. */
@Immutable
data class EquityChartAttachment(
    val symbol: String,
    val name: String = symbol,
    val exchange: String = "",
    val currency: String = "USD",
    val price: Double? = null,
)

internal fun validEquitySymbol(symbol: String): Boolean = Regex("[A-Z][A-Z0-9.\\-]{0,15}").matches(symbol)

internal fun parseEquityChart(item: JSONObject?): EquityChartAttachment? {
    if (item == null) return null
    val symbol = item.optString("ticker").trim().uppercase(Locale.ROOT)
    if (!validEquitySymbol(symbol)) return null
    return EquityChartAttachment(symbol, item.optString("name").takeUnless { it.isBlank() || it == "null" } ?: symbol,
        item.optString("exchange").takeUnless { it == "null" }.orEmpty(),
        item.optString("currency").takeUnless { it.isBlank() || it == "null" } ?: "USD",
        (item.opt("price") as? Number)?.toDouble()?.takeIf(Double::isFinite))
}

/** Normal ticker references must not suddenly become visible chart attachments. */
internal fun parseEquityCharts(value: Any?, chartPost: Boolean = false): List<EquityChartAttachment> {
    val array = when (value) {
        is JSONArray -> value
        is String -> runCatching { JSONArray(value) }.getOrNull()
        else -> null
    } ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index)
        // Feed projections can omit the flag. Type 6 itself then identifies the attachment;
        // an explicit false always remains an ordinary ticker, never an attached chart.
        if (item != null) {
            if (item.optBoolean("is_attachment") || (chartPost && !item.has("is_attachment"))) parseEquityChart(item) else null
        } else if (chartPost && array.opt(index) is String) {
            parseEquityChart(JSONObject().put("ticker", array.optString(index)))
        } else null
    }.distinctBy(EquityChartAttachment::symbol)
}

internal fun EquityChartAttachment.toChartJson(): JSONObject = JSONObject()
    .put("ticker", symbol).put("is_attachment", true).put("name", name)
    .put("exchange", exchange).put("currency", currency).apply { price?.let { put("price", it) } }

internal data class PostTickerReference(val symbol: String, val attachment: Boolean)

internal fun postTickerReferences(text: String, chart: EquityChartAttachment?): List<PostTickerReference> = buildList {
    chart?.let { add(PostTickerReference(it.symbol, true)) }
    tickerSymbols(text).filterNot { it == chart?.symbol }.forEach { add(PostTickerReference(it, false)) }
}

/** Keep the old plain-ticker payload unchanged; attached charts use the official object contract. */
internal fun composeTickerMetadata(text: String, chart: EquityChartAttachment?): JSONArray = JSONArray().apply {
    postTickerReferences(text, chart).forEach {
        if (chart == null) put(it.symbol)
        else put(JSONObject().put("ticker", it.symbol).apply { if (it.attachment) put("is_attachment", true) })
    }
}

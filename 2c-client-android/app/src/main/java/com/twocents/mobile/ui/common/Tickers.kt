package com.twocents.mobile.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.twocents.mobile.AuthState
import com.twocents.mobile.RpcApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private val TickerToken = Regex("(?<![\\w$])\\$([A-Za-z][A-Za-z0-9.\\-]{0,15})(?![\\w.\\-])")
private val TickerQuery = Regex("[A-Za-z0-9.\\-]+")
internal data class TickerContext(val start: Int, val query: String)
internal data class TickerChoice(val symbol: String, val name: String, val exchange: String)

internal fun tickerContext(text: String, cursor: Int = text.length): TickerContext? {
    val prefix = text.substring(0, cursor.coerceIn(0, text.length))
    val start = prefix.lastIndexOf('$')
    if (start < 0 || (start > 0 && (prefix[start - 1].isLetterOrDigit() || prefix[start - 1] == '$'))) return null
    val query = prefix.substring(start + 1)
    if (query.isNotEmpty() && !TickerQuery.matches(query)) return null
    return TickerContext(start, query)
}

internal fun tickerSymbols(text: String): List<String> {
    val urls = Regex("https?://[^\\s<>]+", RegexOption.IGNORE_CASE).findAll(text).map(MatchResult::range).toList()
    return TickerToken.findAll(text).filter { match -> urls.none { match.range.first in it } }
        .map { it.groupValues[1].uppercase(Locale.ROOT) }.distinct().toList()
}

/** The search endpoint has returned both wrapped and direct lists across clients. */
internal fun parseTickerChoices(value: Any?): List<TickerChoice> {
    val root = value as? JSONObject
    val containers = listOfNotNull(root, root?.optJSONObject("data"), root?.optJSONObject("result"))
    val array = value as? JSONArray ?: containers.firstNotNullOfOrNull { container ->
        listOf("tickers", "results", "stocks", "items", "matches", "symbols", "data")
            .firstNotNullOfOrNull(container::optJSONArray)
    }
        ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index)
        val symbol = (item?.optString("ticker")?.ifBlank { item.optString("symbol") }
            ?: array.optString(index)).trim().uppercase(Locale.ROOT)
        if (!Regex("[A-Z][A-Z0-9.\\-]{0,15}").matches(symbol)) return@mapNotNull null
        TickerChoice(symbol, item?.optString("name").orEmpty().ifBlank { item?.optString("description").orEmpty().ifBlank { symbol } },
            item?.optString("exchange").orEmpty())
    }.distinctBy(TickerChoice::symbol)
}

/** Reuses the mention picker's size, placement, and non-focus-stealing behavior. */
@Composable
internal fun TickerSuggestions(
    context: TickerContext?, api: RpcApi?, auth: AuthState?, onSelect: (String) -> Unit,
    modifier: Modifier = Modifier, offset: DpOffset = DpOffset.Zero,
    placeAbove: Boolean = false, gap: Dp = 6.dp,
) {
    val query = context?.query ?: return
    var results by remember(auth?.userUuid) { mutableStateOf(emptyList<TickerChoice>()) }
    var loading by remember(auth?.userUuid) { mutableStateOf(true) }
    var failed by remember(auth?.userUuid) { mutableStateOf(false) }
    LaunchedEffect(api, auth?.userUuid, query) {
        if (query.isEmpty()) {
            results = emptyList()
            loading = false
            failed = false
            return@LaunchedEffect
        }
        if (api == null || auth == null) return@LaunchedEffect
        loading = true
        failed = false
        try {
            delay(200)
            results = parseTickerChoices(api.call("/v1/info/ticker/search", JSONObject().put("query", query), auth))
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            failed = true
        } finally {
            loading = false
        }
    }
    val density = LocalDensity.current
    val offsetPx = with(density) { IntOffset(offset.x.roundToPx(), offset.y.roundToPx()) }
    val gapPx = with(density) { gap.roundToPx() }
    val positionProvider = remember(offsetPx, placeAbove, gapPx) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
                layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val x = anchorBounds.left + offsetPx.x
                val cursorY = anchorBounds.top + offsetPx.y
                val y = if (placeAbove) cursorY - popupContentSize.height - gapPx else cursorY + gapPx
                return IntOffset(x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
                    y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)))
            }
        }
    }
    Popup(popupPositionProvider = positionProvider, onDismissRequest = {},
        properties = PopupProperties(focusable = false, dismissOnClickOutside = true, dismissOnBackPress = true)) {
        Column(modifier.widthIn(min = 286.dp, max = 328.dp).heightIn(max = 174.dp)
            .clip(RoundedCornerShape(12.dp)).background(Color(0xFF141410))
            .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(12.dp))
            .padding(4.dp).verticalScroll(rememberScrollState())) {
            if (loading || failed || results.isEmpty()) {
                Text(if (query.isEmpty()) "Type to search tickers" else if (loading) "Searching tickers…" else if (failed) "Couldn't search tickers. Try again."
                    else "No matching tickers", color = Color.White.copy(alpha = .38f), fontSize = 11.5.sp,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp))
            } else results.forEach { item ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).clickable { onSelect(item.symbol) }
                    .padding(horizontal = 9.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("$${item.symbol}", color = Color(0xFFC8A44D), fontSize = 12.sp,
                        fontWeight = FontWeight.Bold)
                    Text(item.name, color = Color.White.copy(alpha = .8f), fontSize = 11.5.sp,
                        maxLines = 1, modifier = Modifier.weight(1f))
                    if (item.exchange.isNotBlank()) Text(item.exchange, color = Color.White.copy(alpha = .38f), fontSize = 10.sp)
                }
            }
        }
    }
}

/** Adds ticker actions to rendered text without touching existing links or mention spans. */
internal fun annotateTickers(source: AnnotatedString): AnnotatedString = AnnotatedString.Builder(source).apply {
    TickerToken.findAll(source.text).forEach { match ->
        val start = match.range.first
        val end = match.range.last + 1
        if (source.getStringAnnotations("URL", start, end).isEmpty()) {
            addStyle(SpanStyle(color = Color(0xFFC8A44D), fontWeight = FontWeight.SemiBold), start, end)
            addStringAnnotation("URL", "ticker:${match.groupValues[1].uppercase(Locale.ROOT)}", start, end)
        }
    }
}.toAnnotatedString()

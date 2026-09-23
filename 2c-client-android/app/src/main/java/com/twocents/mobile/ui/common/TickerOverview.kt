package com.twocents.mobile.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val Green = Color(0xFF34D399)
private val Red = Color(0xFFF43F5E)
private val Gold = Color(0xFFC8A44D)
private val Muted = Color(0xFF99968E)

@Composable
internal fun TickerCompanyHeader(symbol: String, details: TickerDetails?, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 4.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(36.dp).background(Color.White.copy(alpha = .07f), CircleShape), contentAlignment = Alignment.Center) {
            if (details?.logoUrl != null) AsyncImage(model = details.logoUrl, contentDescription = null,
                modifier = Modifier.size(34.dp).clip(CircleShape), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
            else Text(symbol.take(1), color = Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f)) {
            Text(details?.name ?: symbol, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            val exchange = when (details?.exchange) { "XNYS" -> "NYSE"; "XNAS" -> "NASDAQ"; else -> details?.exchange }
            val meta = listOfNotNull(symbol, exchange?.takeIf(String::isNotBlank),
                details?.marketCap?.takeIf { it > 0 }?.let { "${compactNumber(it)} MC" },
                details?.currency?.takeIf(String::isNotBlank)).joinToString(" · ")
            Text(meta, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Close, "Close ticker", tint = Color.White.copy(alpha = .64f), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
internal fun TickerOverview(
    symbol: String,
    details: TickerDetails?,
    price: TickerPrice?,
    points: List<TickerPoint>,
    period: TickerPeriod,
    chartLoading: Boolean,
    chartError: String?,
    onPeriod: (TickerPeriod) -> Unit,
    onRetryChart: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    var selectedPoint by remember(symbol, period, points) { mutableIntStateOf(-1) }
    var aboutExpanded by remember(symbol) { mutableStateOf(false) }
    val current = points.getOrNull(selectedPoint)
    val shownPrice = current?.close ?: price?.price ?: points.lastOrNull()?.close
    val baseline = if (current != null || price?.price == null) points.firstOrNull()?.close else price.price.minus(price.change ?: 0.0)
    val change = if (current != null || price?.price == null) shownPrice?.let { it - (baseline ?: it) } else price.change
    val percent = if ((current != null || price?.price == null) && baseline != null && baseline != 0.0) change?.div(baseline)?.times(100) else price?.changePercent
    val positive = (change ?: 0.0) >= 0.0
    val accent = if (positive) Green else Red
    val chartAccent = if ((points.lastOrNull()?.close ?: 0.0) >= (points.firstOrNull()?.close ?: 0.0)) Green else Red
    val bounds = remember(points) { tickerChartBounds(points) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 9.dp)) {
        Text(shownPrice?.let(::currencyPrice) ?: "Price unavailable", color = Color.White,
            fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        if (change != null && percent != null) {
            val sign = if (positive) "+" else ""
            val volume = (current?.volume ?: price?.volume)?.let { "   Vol: ${compactNumber(it)}" }
            Text("$sign${currencyPrice(change)} ($sign${"%.2f".format(Locale.US, percent)}%)${volume.orEmpty()}",
                color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        } else Text("Market data unavailable", color = Muted, fontSize = 12.sp)
        // Keep the chart header's height stable while an interval's points are replaced.
        Box(Modifier.fillMaxWidth().height(22.dp), contentAlignment = Alignment.CenterStart) {
            current?.let {
                val time = Instant.ofEpochMilli(it.time).atZone(ZoneId.systemDefault())
                Text(DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a", Locale.getDefault()).format(time),
                    color = Muted, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().height(156.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                when {
                    chartLoading && points.isEmpty() -> TickerChartSkeleton(Modifier.fillMaxSize())
                    points.size > 1 && bounds != null -> TickerPriceChart(points, bounds, chartAccent, selectedPoint, onSelect = { selectedPoint = it })
                    chartError != null -> Text("Chart unavailable · Tap to retry", Modifier.clickable(onClick = onRetryChart), color = Muted, fontSize = 12.sp)
                    else -> Text("No price history for this period", color = Muted, fontSize = 12.sp)
                }
            }
            Column(Modifier.width(42.dp).fillMaxHeight().offset(x = 5.dp),
                verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
                if (points.size > 1 && bounds != null) {
                    Text(axisPrice(bounds.high), color = Muted.copy(alpha = .76f), fontSize = 10.sp)
                    Text(axisPrice((bounds.high + bounds.low) / 2), color = Muted.copy(alpha = .76f), fontSize = 10.sp)
                    Text(axisPrice(bounds.low), color = Muted.copy(alpha = .76f), fontSize = 10.sp)
                } else if (chartLoading) {
                    repeat(3) { TickerAxisPlaceholder() }
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(22.dp).padding(top = 3.dp, end = 42.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            if (points.size > 1) {
                for (index in listOf(0, points.lastIndex / 2, points.lastIndex)) {
                    Text(axisTime(points[index].time, period), color = Muted.copy(alpha = .72f), fontSize = 10.sp)
                }
            } else if (chartLoading) {
                repeat(3) { TickerAxisPlaceholder() }
            }
        }
        Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
            TickerPeriodSelector(period) { selectedPoint = -1; onPeriod(it) }
        }
        if (!details?.description.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha = .035f), RoundedCornerShape(13.dp))
                .border(1.dp, Color.White.copy(alpha = .07f), RoundedCornerShape(13.dp))
                .clickable { aboutExpanded = !aboutExpanded }.padding(horizontal = 11.dp, vertical = 9.dp)) {
                Text("About ${details.name}", color = Gold, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Text(details.description, color = Muted, fontSize = 12.sp, lineHeight = 17.sp,
                    maxLines = if (aboutExpanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
                if (aboutExpanded && !details.homepageUrl.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Visit website ↗", Modifier.clickable { runCatching { uriHandler.openUri(details.homepageUrl) } },
                        color = Gold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun TickerAxisPlaceholder() {
    Box(Modifier.width(25.dp).height(4.dp).background(Color.White.copy(alpha = .1f), CircleShape))
}

@Composable
private fun TickerChartSkeleton(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        for (fraction in listOf(0f, .5f, 1f)) {
            val y = (size.height - 1.dp.toPx()) * fraction
            drawLine(Color.White.copy(alpha = .075f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
        val trace = Path().apply {
            moveTo(0f, size.height * .62f)
            lineTo(size.width * .18f, size.height * .57f)
            lineTo(size.width * .35f, size.height * .69f)
            lineTo(size.width * .51f, size.height * .43f)
            lineTo(size.width * .68f, size.height * .49f)
            lineTo(size.width * .84f, size.height * .33f)
            lineTo(size.width, size.height * .39f)
        }
        drawPath(trace, Color.White.copy(alpha = .14f), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun TickerPriceChart(points: List<TickerPoint>, bounds: TickerChartBounds, color: Color, selected: Int, onSelect: (Int) -> Unit) {
    val drawnIndices = remember(points) { chartDrawIndices(points) }
    Canvas(Modifier.fillMaxSize()
        // The horizontal detector waits for horizontal touch slop, so vertical feed scrolling wins.
        .pointerInput(points) { detectHorizontalDragGestures(onDragEnd = {}, onHorizontalDrag = { change, _ ->
            onSelect(((change.position.x / size.width) * (points.lastIndex)).roundToInt().coerceIn(points.indices))
        }) }
        .pointerInput(points) { detectTapGestures { tap ->
            onSelect(((tap.x / size.width) * (points.lastIndex)).roundToInt().coerceIn(points.indices))
        } }) {
        val span = (bounds.high - bounds.low).coerceAtLeast(.000001)
        fun point(index: Int): Offset = Offset(
            size.width * index / points.lastIndex,
            size.height * (1f - ((points[index].close - bounds.low) / span).toFloat()),
        )
        val line = Path().apply {
            drawnIndices.forEachIndexed { position, i ->
                val p = point(i)
                if (position == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
        }
        val fill = Path().apply { addPath(line); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = .26f), color.copy(alpha = .01f))))
        for (fraction in listOf(0f, .5f, 1f)) {
            val y = (size.height - 1.dp.toPx()) * fraction
            drawLine(Color.White.copy(alpha = .075f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
        drawPath(line, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        if (selected in points.indices) {
            val p = point(selected)
            drawLine(Color.White.copy(alpha = .4f), Offset(p.x, 0f), Offset(p.x, size.height), 1.dp.toPx())
            drawCircle(color, 5.dp.toPx(), p)
            drawCircle(Color(0xFF141410), 2.5.dp.toPx(), p)
        }
    }
}

internal data class TickerChartBounds(val low: Double, val high: Double)

internal fun tickerChartBounds(points: List<TickerPoint>): TickerChartBounds? {
    if (points.isEmpty()) return null
    val min = points.minOf { it.close }
    val max = points.maxOf { it.close }
    val padding = ((max - min) * .13).coerceAtLeast(kotlin.math.abs(max) * .002).coerceAtLeast(.001)
    return TickerChartBounds(min - padding, max + padding)
}

private fun axisPrice(value: Double): String = when {
    kotlin.math.abs(value) >= 1_000 -> "$${compactNumber(value)}"
    kotlin.math.abs(value) < 1 -> "$${"%.3f".format(Locale.US, value)}"
    else -> "$${"%.2f".format(Locale.US, value)}"
}

private fun axisTime(epochMillis: Long, period: TickerPeriod): String {
    val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
    val pattern = when (period) {
        TickerPeriod.Day -> "HH:mm"
        TickerPeriod.Week, TickerPeriod.Month -> "MMM d"
        TickerPeriod.Year -> "MMM yy"
        TickerPeriod.All -> "yyyy"
    }
    return DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(date)
}

/** Keeps high/low spikes when a long history contains more bars than screen pixels. */
internal fun chartDrawIndices(points: List<TickerPoint>, maxBuckets: Int = 600): List<Int> {
    if (points.size <= maxBuckets * 2) return points.indices.toList()
    val result = ArrayList<Int>(maxBuckets * 2 + 2)
    result.add(0)
    for (bucket in 0 until maxBuckets) {
        val start = bucket * points.size / maxBuckets
        val end = ((bucket + 1) * points.size / maxBuckets).coerceAtMost(points.size)
        var low = start
        var high = start
        for (index in start until end) {
            if (points[index].close < points[low].close) low = index
            if (points[index].close > points[high].close) high = index
        }
        if (low < high) { result.add(low); result.add(high) }
        else if (high < low) { result.add(high); result.add(low) }
        else result.add(low)
    }
    result.add(points.lastIndex)
    return result.distinct()
}

private fun currencyPrice(value: Double): String = "${if (value < 0) "-" else ""}$${"%,.2f".format(Locale.US, kotlin.math.abs(value))}"
private fun compactNumber(value: Double): String = when {
    value >= 1e12 -> "%.1fT".format(Locale.US, value / 1e12)
    value >= 1e9 -> "%.1fB".format(Locale.US, value / 1e9)
    value >= 1e6 -> "%.1fM".format(Locale.US, value / 1e6)
    value >= 1e3 -> "%.1fK".format(Locale.US, value / 1e3)
    else -> NumberFormat.getNumberInstance(Locale.US).format(value)
}

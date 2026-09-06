package com.twocents.mobile.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.twocents.mobile.ui.compose.ComposeAuthorProfile
import com.twocents.mobile.ui.compose.ComposeNetworthPill
import com.twocents.mobile.ui.profile.ProfileNavigationBus
import com.twocents.mobile.ui.common.AppHaptics
import java.net.URI
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val Gold = Color(0xFFC8A44D)
private val Emerald = Color(0xFF34D399)
private val Rose = Color(0xFFF43F5E)
private val CardSurface = Color.White.copy(alpha = 0.02f)
private val CardBorder = Color.White.copy(alpha = 0.08f)
private val noFontPaddingStyle = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))

@Composable
internal fun FeedTransactionCard(post: FeedPost) {
    val meta = post.meta
    val context = LocalContext.current
    var lightbox by remember(post.uuid) { mutableStateOf(false) }
    val incoming = meta.categoryIconUrl?.contains("income", ignoreCase = true) == true
    val accent = if (incoming) Emerald else Rose
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.fillMaxWidth().padding(top = 12.dp).clip(shape)
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.065f), Color.Transparent)))
            .border(1.dp, accent.copy(alpha = 0.30f), shape),
    ) {
        Box(Modifier.align(Alignment.CenterStart).padding(vertical = 8.dp).width(3.dp).height(72.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(accent.copy(alpha = .85f), accent.copy(alpha = .15f)))))
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Row(
                    Modifier.clip(CircleShape).background(accent.copy(alpha = .10f)).border(1.dp, accent.copy(alpha = .30f), CircleShape).padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    meta.categoryIconUrl?.let { AsyncImage(it, null, Modifier.size(14.dp).clip(RoundedCornerShape(2.dp))) }
                    Text(
                        buildString { append(if (incoming) "INCOME" else "OUTGOING"); meta.category?.takeIf(String::isNotBlank)?.let { append(" • "); append(it.uppercase()) } },
                        color = accent, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp, style = noFontPaddingStyle,
                    )
                }
                meta.transactionValue?.let {
                    Text((if (incoming) "+" else "−") + formatMoneyExact(kotlin.math.abs(it), meta.currencyCode ?: "USD"), color = accent, fontSize = 27.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp), style = noFontPaddingStyle)
                }
                val detail = listOfNotNull(meta.merchant, meta.date?.let(::formatTransactionDate)).filter(String::isNotBlank).joinToString(" • ").uppercase()
                if (detail.isNotBlank()) Text(detail, color = Color.White.copy(alpha = .28f), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp), style = noFontPaddingStyle)
            }
            meta.receiptImageUrl?.let { image ->
                AsyncImage(
                    model = ImageRequest.Builder(context).data(image).build(), contentDescription = "Transaction attachment", contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.size(82.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .02f)).border(1.dp, Color.White.copy(alpha = .10f), RoundedCornerShape(12.dp)).clickable { lightbox = true },
                )
            }
        }
    }
    if (lightbox) meta.receiptImageUrl?.let { ImageLightbox(listOf(it), 0, null) { lightbox = false } }
}

@Composable
internal fun FeedBudgetCard(post: FeedPost) {
    val meta = post.meta
    val allocated = meta.totalAllocated.takeIf { it > 0 } ?: meta.spendingLimit
    val percent = if (allocated > 0) (meta.totalSpent / allocated).toFloat().coerceIn(0f, 1f) else 0f
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).clip(shape)
            .background(Brush.linearGradient(listOf(Gold.copy(alpha = .035f), Color(0x990F0E0A))))
            .border(1.dp, Gold.copy(alpha = .15f), shape)
            .drawBehind {
                drawLine(
                    color = Gold.copy(alpha = .48f),
                    start = Offset(1.5.dp.toPx(), 5.dp.toPx()),
                    end = Offset(1.5.dp.toPx(), size.height - 5.dp.toPx()),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("BUDGET", color = Gold, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp, modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = .04f)).border(1.dp, Color.White.copy(alpha = .06f), CircleShape).padding(horizontal = 9.dp, vertical = 5.dp), style = noFontPaddingStyle)
            meta.month?.let { Text(formatBudgetMonth(it), color = Color.White.copy(alpha = .35f), fontSize = 11.5.sp, fontWeight = FontWeight.Medium, style = noFontPaddingStyle) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("SPENT", color = Color.White.copy(alpha = .38f), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp, style = noFontPaddingStyle)
                Text(formatMoneyExact(meta.totalSpent), color = Gold, fontSize = 22.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold, style = noFontPaddingStyle)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("TOTAL BUDGET", color = Color.White.copy(alpha = .28f), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = .7.sp, style = noFontPaddingStyle)
                Text(formatMoneyExact(allocated), color = Color.White.copy(alpha = .58f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, style = noFontPaddingStyle)
            }
        }
        ProgressBar(percent, Gold, 12.dp, meta.budgetCategories.mapNotNull { category -> parseColor(category.color)?.let { it to if (allocated > 0) (category.spent / allocated).toFloat() else 0f } })
        Text("${(percent * 100).roundToInt()}% used", color = Color.White.copy(alpha = .30f), fontSize = 10.5.sp, modifier = Modifier.fillMaxWidth().padding(top = 5.dp), textAlign = TextAlign.End, style = noFontPaddingStyle)
        Box(Modifier.fillMaxWidth().padding(top = 11.dp).height(1.dp).background(Color.White.copy(alpha = .055f)))
        meta.budgetCategories.forEach { category ->
            val categoryPercent = if (category.allocated > 0) (category.spent / category.allocated).toFloat().coerceIn(0f, 1f) else 0f
            val over = category.spent > category.allocated && category.allocated > 0
            Column(Modifier.padding(top = 11.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        category.label,
                        color = Color.White.copy(alpha = .72f),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(.44f),
                        style = noFontPaddingStyle,
                    )
                    Text(
                        "${formatMoneyExact(category.spent)}  /  ${formatMoneyExact(category.allocated)}",
                        color = if (over) Rose else Color.White.copy(alpha = .38f),
                        fontSize = 10.5.sp,
                        fontWeight = if (over) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(.56f),
                        style = noFontPaddingStyle,
                    )
                }
                ProgressBar(categoryPercent, parseColor(category.color) ?: Gold, 4.dp)
            }
        }
    }
}

@Composable
private fun ProgressBar(fraction: Float, color: Color, height: androidx.compose.ui.unit.Dp, segments: List<Pair<Color, Float>> = emptyList()) {
    Box(Modifier.fillMaxWidth().height(height).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))) {
        if (segments.isEmpty()) Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(color))
        else Row(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight()) {
            val total = segments.sumOf { it.second.toDouble() }.toFloat().coerceAtLeast(.0001f)
            segments.forEach { (segmentColor, part) -> Box(Modifier.weight((part / total).coerceAtLeast(.0001f)).fillMaxHeight().background(segmentColor.copy(alpha = .85f))) }
        }
    }
}



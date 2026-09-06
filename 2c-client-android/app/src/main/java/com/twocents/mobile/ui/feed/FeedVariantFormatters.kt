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

internal fun netWorthTierColor(balance: Double) = when {
    kotlin.math.abs(balance) >= 1_000_000 -> Gold
    kotlin.math.abs(balance) >= 100_000 -> Color(0xFFC0C0D2)
    else -> Color(0xFFCD7F32)
}
internal fun pollNetWorth(value: Double) = buildAnnotatedString {
    if (value < 0) append("-")
    pushStyle(SpanStyle(fontSize = 9.sp))
    append("$")
    pop()
    append(formatNumber(kotlin.math.abs(value).roundToInt()))
}
internal fun likertNetWorth(value: Double) = buildAnnotatedString {
    pushStyle(SpanStyle(fontSize = 8.5.sp))
    append("$")
    pop()
    append(formatCompact(value))
}
internal fun formatCompact(value: Double): String = when {
    kotlin.math.abs(value) >= 1_000_000_000 -> "${trimDecimal(value / 1_000_000_000)}B"
    kotlin.math.abs(value) >= 1_000_000 -> "${trimDecimal(value / 1_000_000)}M"
    kotlin.math.abs(value) >= 1_000 -> "${trimDecimal(value / 1_000)}k"
    else -> NumberFormat.getIntegerInstance(Locale.US).format(value.roundToInt())
}
internal fun trimDecimal(value: Double) = String.format(Locale.US, "%.1f", value).removeSuffix(".0")
internal fun formatNumber(value: Int) = NumberFormat.getIntegerInstance(Locale.US).format(kotlin.math.abs(value))
internal fun formatCurrency(value: Double) = "$" + NumberFormat.getIntegerInstance(Locale.US).format(value.roundToInt())
internal fun formatMoneyExact(value: Double, currency: String = "USD") = NumberFormat.getCurrencyInstance(Locale.US).apply { this.currency = java.util.Currency.getInstance(currency); minimumFractionDigits = 2; maximumFractionDigits = 2 }.format(value)
internal fun formatTransactionDate(raw: String): String = runCatching { DateTimeFormatter.ofPattern("MMMM d", Locale.US).format(java.time.LocalDate.parse(raw)) }.getOrDefault(raw)
internal fun formatBudgetMonth(raw: String): String = runCatching { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US).format(java.time.YearMonth.parse(raw)) }.getOrDefault(raw)
internal fun formatDeadline(raw: String): String = runCatching {
    DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US).format(Instant.parse(raw).atZone(ZoneId.systemDefault()))
}.getOrDefault(raw)
internal fun parseColor(raw: String?): Color? = raw?.removePrefix("#")?.let { hex ->
    runCatching { Color((if (hex.length == 6) "FF$hex" else hex).toLong(16)) }.getOrNull()
}



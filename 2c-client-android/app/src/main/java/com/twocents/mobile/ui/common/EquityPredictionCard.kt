package com.twocents.mobile.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PredictionGold = Color(0xFFC8A44D)
private val PredictionMuted = Color.White.copy(alpha = .45f)

internal fun predictionMoney(value: Double): String = "$${"%,.2f".format(Locale.US, value)}"

/** The same summary is used in the editor, its picker, and published/quoted posts. */
@Composable
internal fun EquityPredictionSummary(prediction: EquityPrediction) {
    val percent = predictionPercent(prediction)
    val down = prediction.direction == "below" || (prediction.direction == null && (percent ?: 0.0) < 0)
    val accent = if (down) Color(0xFFF43F5E) else Color(0xFF34D399)
    val target = predictionMoney(prediction.targetPrice)
    val date = runCatching { LocalDate.parse(prediction.targetDate) }.getOrNull()
    val dateLabel = date?.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())) ?: prediction.targetDate
    val baseline = prediction.currentPrice?.let(::predictionMoney) ?: "Price unavailable"
    val change = percent?.let {
        (if (it > 0) "+" else "") + "%.2f".format(Locale.US, it).trimEnd('0').trimEnd('.') + "%"
    } ?: prediction.direction?.replaceFirstChar(Char::uppercaseChar) ?: "—"
    val measurer = rememberTextMeasurer()
    val typography = LocalTextStyle.current
    val density = LocalDensity.current
    // Measure real text with the current font scale, not a device-specific breakpoint.
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        fun width(text: String, size: Int, bold: Boolean = false): Int = measurer.measure(text,
            typography.copy(fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal),
            softWrap = false).size.width
        val required = maxOf(width(prediction.symbol, 16, true), width(baseline, 11)) +
            maxOf(width(change, 16, true), with(density) { 18.dp.roundToPx() }) +
            maxOf(width(target, 18, true), width(dateLabel, 11)) + with(density) { 24.dp.roundToPx() }
        if (required <= constraints.maxWidth) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(prediction.symbol, color = PredictionGold, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { TickerNavigation.open(prediction.symbol) })
                    Text(baseline, color = PredictionMuted, fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(change, color = accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = PredictionMuted, modifier = Modifier.size(18.dp))
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(target, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(dateLabel, color = PredictionMuted, fontSize = 11.sp)
                }
            }
        } else {
            // Long amounts or large accessibility fonts get another row, never missing digits.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(prediction.symbol, color = PredictionGold, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).clickable { TickerNavigation.open(prediction.symbol) })
                    Text(change, color = accent, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(baseline, color = PredictionMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = PredictionMuted, modifier = Modifier.size(18.dp))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text(target, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        Text(dateLabel, color = PredictionMuted, fontSize = 11.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    }
                }
            }
        }
    }
}

/** A live quote is optional; the prediction baseline and resolution remain the server's authoritative values. */
@Composable
internal fun EquityPredictionCard(prediction: EquityPrediction, onEdit: (() -> Unit)? = null, onRemove: (() -> Unit)? = null) {
    val data = LocalTickerData.current
    val view = LocalView.current
    var quote by remember(data, prediction.symbol) { mutableStateOf<TickerPrice?>(null) }
    var retry by remember(prediction.symbol) { mutableIntStateOf(0) }
    LaunchedEffect(data, prediction.symbol, retry) {
        try { quote = data?.price(prediction.symbol) }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { /* Keep the original prediction visible when the optional live quote is unavailable. */ }
    }
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.fillMaxWidth().padding(top = 8.dp).clip(shape).background(Color(0xFF141410))
        .border(1.dp, Color.White.copy(alpha = .08f), shape).padding(horizontal = 12.dp, vertical = 6.dp)) {
        if (onEdit != null || onRemove != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ShowChart, null, tint = PredictionGold, modifier = Modifier.size(16.dp))
            Text("Equity prediction", color = PredictionMuted, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (onEdit != null) Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).clickable { AppHaptics.open(view); onEdit() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Edit, "Edit prediction", tint = PredictionGold, modifier = Modifier.size(17.dp))
            }
            if (onRemove != null) Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).clickable { AppHaptics.toggle(view); onRemove() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Close, "Remove equity prediction", tint = PredictionMuted, modifier = Modifier.size(17.dp))
            }
        }
        EquityPredictionSummary(prediction)
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .07f)))
        FlowRow(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text((if (quote?.price != null) "Now " else "At prediction ") +
                    ((quote?.price ?: prediction.currentPrice)?.let(::predictionMoney) ?: "unavailable"),
                    color = Color.White.copy(alpha = .7f), fontSize = 11.sp, modifier = Modifier.weight(1f, fill = false))
                if (quote?.price == null) Box(Modifier.size(32.dp).clickable { retry++ }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Refresh, "Refresh price", tint = PredictionGold, modifier = Modifier.size(15.dp))
                }
            }
            Text(predictionStatusLabel(prediction), color = PredictionGold, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp, top = 3.dp, bottom = 3.dp))
        }
    }
}

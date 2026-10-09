package com.twocents.mobile.ui.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.twocents.mobile.AuthState
import com.twocents.mobile.RpcApi
import com.twocents.mobile.ui.common.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Gold = Color(0xFFC8A44D)

/** Owns an uncommitted prediction; only Add to post changes the compose draft. */
@Composable
internal fun EquityPredictionSheet(
    api: RpcApi?, auth: AuthState?, initial: EquityPrediction?, onDismiss: () -> Unit, onAdd: (EquityPrediction) -> Unit,
) {
    val data = LocalTickerData.current
    val view = LocalView.current
    val today = remember { LocalDate.now() }
    var stock by remember { mutableStateOf(initial?.let { EquityChartAttachment(it.symbol, it.name, price = it.currentPrice) }) }
    var choosingStock by remember { mutableStateOf(initial == null) }
    var targetText by remember { mutableStateOf(initial?.targetPrice?.let { "%.2f".format(Locale.US, it) }.orEmpty()) }
    var date by remember { mutableStateOf(initial?.targetDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }) }
    var pricePreset by remember { mutableStateOf<Int?>(null) }
    var datePreset by remember { mutableStateOf<String?>(null) }
    var calendarOpen by remember { mutableStateOf(false) }
    var quote by remember(stock?.symbol) { mutableStateOf<TickerPrice?>(null) }
    var details by remember(stock?.symbol) { mutableStateOf<TickerDetails?>(null) }
    var quoteLoading by remember(stock?.symbol) { mutableStateOf(stock != null) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(data, stock?.symbol, retry) {
        val symbol = stock?.symbol ?: return@LaunchedEffect
        quoteLoading = true
        launch {
            try { details = data?.details(symbol) }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { /* Company details are optional; the selected ticker remains valid. */ }
        }
        try { quote = data?.price(symbol) }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { /* Manual target prices remain usable when presets cannot fetch a baseline. */ }
        finally { quoteLoading = false }
    }
    if (choosingStock) {
        EquityChartPickerSheet(api, auth,
            onDismiss = { if (stock == null) onDismiss() else choosingStock = false },
            onSelect = { choice ->
                if (choice.symbol != stock?.symbol) { targetText = ""; pricePreset = null }
                stock = choice
                choosingStock = false
            }, title = "Equity prediction", guidance = "Choose the stock you want to predict.")
        return
    }
    val selected = stock ?: return
    val baseline = (quote?.price ?: selected.price)?.takeIf { it.isFinite() && it > 0 }
    val target = parsePredictionPrice(targetText)
    val canAdd = target != null && date?.let { validPredictionDate(it, today) } == true
    val prediction = if (canAdd) EquityPrediction(selected.symbol, requireNotNull(target), requireNotNull(date).toString(),
        details?.name ?: selected.name, baseline, baseline?.let { if (target >= it) "above" else "below" }) else null
    val motion = remember { Animatable(1f) }
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val threshold = with(LocalDensity.current) { 70.dp.toPx() }
    var closing by remember { mutableStateOf(false) }
    fun close() {
        if (closing) return
        closing = true
        scope.launch { motion.animateTo(1f, tween(180)); onDismiss() }
    }
    LaunchedEffect(Unit) { motion.animateTo(0f, tween(210)) }
    Dialog(onDismissRequest = ::close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        EdgeToEdgeDialogWindow(navigationBarColor = android.graphics.Color.TRANSPARENT)
        BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .72f * (1f - motion.value))).clickable { close() })
            val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = maxHeight * .92f)
                .graphicsLayer { translationY = motion.value * (size.height + 40.dp.toPx()) + drag.value }
                .clip(shape).background(Color(0xFF141410)).border(1.dp, Color.White.copy(alpha = .08f), shape)
                .clickable(onClick = {}).navigationBarsPadding()) {
                Box(Modifier.fillMaxWidth().height(20.dp).appBottomSheetDragHandle(
                    onDrag = { amount -> scope.launch { drag.snapTo((drag.value + amount).coerceAtLeast(0f)) } },
                    onDragEnd = { if (drag.value > threshold) close() else scope.launch { drag.animateTo(0f, tween(180)) } },
                    onDragCancel = { scope.launch { drag.animateTo(0f, tween(180)) } },
                ), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(36.dp).height(4.dp).background(Color.White.copy(alpha = .2f), RoundedCornerShape(4.dp)))
                }
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Equity prediction", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Box(Modifier.size(44.dp).clickable { AppHaptics.toggle(view); close() }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Close, "Close prediction", tint = Color.White.copy(alpha = .6f), modifier = Modifier.size(19.dp))
                    }
                }
                Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PredictionPanel {
                        Row(Modifier.fillMaxWidth().clickable { AppHaptics.open(view); choosingStock = true },
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(32.dp).background(Color.White.copy(alpha = .06f), CircleShape), contentAlignment = Alignment.Center) {
                                val logo = details?.logoUrl
                                if (logo != null) AsyncImage(logo, null, modifier = Modifier.size(30.dp).clip(CircleShape))
                                else Text(selected.symbol.take(1), color = Gold, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(details?.name ?: selected.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(selected.symbol, color = Gold, fontSize = 11.sp)
                            }
                            Icon(Icons.Outlined.Edit, "Change stock", tint = Gold, modifier = Modifier.size(17.dp))
                        }
                        FlowRow(Modifier.fillMaxWidth().padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                                Text(baseline?.let(::predictionMoney) ?: if (quoteLoading) "Loading price…" else "Price unavailable",
                                    color = Color.White.copy(alpha = .8f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                quote?.changePercent?.let { change ->
                                    Text("${if (change > 0) "+" else ""}${"%.2f".format(Locale.US, change)}%",
                                        color = if (change >= 0) Color(0xFF34D399) else Color(0xFFF43F5E), fontSize = 11.sp)
                                }
                                if (!quoteLoading && quote?.price == null) Text("Retry price", color = Gold, fontSize = 11.sp, modifier = Modifier.clickable { retry++ })
                        }
                    }
                    PredictionPanel {
                        Text("Target price", color = Color.White.copy(alpha = .65f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = .035f)).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("$", color = Gold, fontSize = 16.sp)
                            BasicTextField(targetText, { text ->
                                if (text.length <= 18 && text.all { it.isDigit() || it == '.' || it == ',' }) { targetText = text; pricePreset = null }
                            }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.End),
                                cursorBrush = SolidColor(Gold), modifier = Modifier.weight(1f).padding(start = 8.dp, top = 9.dp, bottom = 9.dp),
                                decorationBox = { inner -> Box(contentAlignment = Alignment.CenterEnd) {
                                    if (targetText.isEmpty()) Text("0.00", color = Color.White.copy(alpha = .3f), fontSize = 16.sp); inner()
                                } })
                        }
                        PredictionSegments(listOf("-20%", "-10%", "-5%", "+5%", "+10%", "+20%"), pricePreset?.let { "${if (it > 0) "+" else ""}$it%" }, baseline != null) { label ->
                            val percent = label.removeSuffix("%").toInt()
                            pricePreset = percent
                            targetText = "%.2f".format(Locale.US, predictionPresetPrice(requireNotNull(baseline), percent))
                        }
                    }
                    PredictionPanel {
                        Text("Target date", color = Color.White.copy(alpha = .65f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 5.dp))
                        PredictionSegments(listOf("1D", "1W", "2W", "1M"), datePreset) {
                            datePreset = it; date = predictionPresetDate(it, today); calendarOpen = false
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(8.dp))
                            .clickable { AppHaptics.open(view); calendarOpen = !calendarOpen }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Icon(Icons.Outlined.CalendarMonth, null, tint = Gold, modifier = Modifier.size(17.dp))
                            Text(date?.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")) ?: "Choose a date",
                                color = Gold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Icon(if (calendarOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null,
                                tint = Gold, modifier = Modifier.size(18.dp))
                        }
                        if (calendarOpen) EquityPredictionCalendar(date, today) { date = it; datePreset = null; calendarOpen = false }
                        Text("Resolves at market close", color = Color.White.copy(alpha = .4f), fontSize = 10.sp)
                    }
                    if (prediction != null) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .08f)))
                        EquityPredictionSummary(prediction)
                    }
                }
                Text("Add to post", color = if (canAdd) Gold else Color.White.copy(alpha = .22f), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(9.dp))
                        .background(Gold.copy(alpha = if (canAdd) .17f else .04f)).border(1.dp, Gold.copy(alpha = if (canAdd) .35f else .08f), RoundedCornerShape(9.dp))
                        .clickable(enabled = canAdd && !closing) { AppHaptics.confirm(view); onAdd(requireNotNull(prediction)) }.padding(vertical = 10.dp))
            }
        }
    }
}

@Composable
private fun PredictionPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), content = content)
}

@Composable
private fun PredictionSegments(labels: List<String>, selected: String?, enabled: Boolean = true, onSelect: (String) -> Unit) {
    val view = LocalView.current
    val shape = RoundedCornerShape(8.dp)
    val measurer = rememberTextMeasurer()
    val style = LocalTextStyle.current.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().clip(shape).background(Color.White.copy(alpha = .025f))
        .border(1.dp, Color.White.copy(alpha = .1f), shape)) {
        val itemWidth = labels.maxOf { measurer.measure(it, style, softWrap = false).size.width } +
            with(density) { 24.dp.roundToPx() }
        val fits = (constraints.maxWidth / itemWidth.coerceAtLeast(1)).coerceIn(1, labels.size)
        val rows = (labels.size + fits - 1) / fits
        val columns = (labels.size + rows - 1) / rows
        Column {
            labels.chunked(columns).forEachIndexed { rowIndex, row ->
                if (rowIndex > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .08f)))
                Row(Modifier.fillMaxWidth()) {
                    row.forEachIndexed { index, label ->
                        if (index > 0) Box(Modifier.width(1.dp).height(36.dp).background(Color.White.copy(alpha = .08f)))
                        Box(Modifier.weight(1f).background(if (selected == label) Gold.copy(alpha = .17f) else Color.Transparent)
                            .clickable(enabled = enabled) { AppHaptics.toggle(view); onSelect(label) }
                            .heightIn(min = 36.dp).padding(horizontal = 4.dp, vertical = 7.dp), contentAlignment = Alignment.Center) {
                            Text(label, color = if (selected == label) Gold else Color.White.copy(alpha = if (enabled) .65f else .2f),
                                style = style, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

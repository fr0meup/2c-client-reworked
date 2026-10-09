package com.twocents.mobile.ui.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.twocents.mobile.AuthState
import com.twocents.mobile.RpcApi
import com.twocents.mobile.ui.common.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Explicit attachment picker; searching never inserts a $-token into the editor. */
@Composable
internal fun EquityChartPickerSheet(
    api: RpcApi?, auth: AuthState?, onDismiss: () -> Unit, onSelect: (EquityChartAttachment) -> Unit,
    title: String = "Equity chart",
    guidance: String = "Choose a ticker to attach its interactive chart.",
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<TickerChoice>()) }
    var loading by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val motion = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val view = LocalView.current
    var closing by remember { mutableStateOf(false) }
    val drag = remember { Animatable(0f) }
    val closeThreshold = with(LocalDensity.current) { 70.dp.toPx() }
    fun close() {
        if (closing) return
        closing = true
        scope.launch { motion.animateTo(1f, tween(180)); onDismiss() }
    }
    LaunchedEffect(Unit) { motion.animateTo(0f, tween(210)); focus.requestFocus() }
    LaunchedEffect(api, auth?.userUuid, query.trim(), retry) {
        val search = query.trim()
        if (search.isEmpty()) { results = emptyList(); loading = false; failure = false; return@LaunchedEffect }
        loading = true
        failure = false
        try {
            delay(200)
            if (api == null || auth == null) error("Ticker search unavailable")
            results = parseTickerChoices(api.call("/v1/info/ticker/search", JSONObject().put("query", search), auth))
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { failure = true }
        finally { loading = false }
    }
    Dialog(onDismissRequest = ::close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        EdgeToEdgeDialogWindow(navigationBarColor = android.graphics.Color.TRANSPARENT)
        Box(Modifier.fillMaxSize().imePadding()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .72f * (1f - motion.value))).clickable { close() })
            val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(.8f)
                .graphicsLayer { translationY = motion.value * (size.height + 40.dp.toPx()) + drag.value }
                .clip(shape).background(Color(0xFF141410)).border(1.dp, Color.White.copy(alpha = .08f), shape)
                .clickable(onClick = {}).navigationBarsPadding()) {
                Box(Modifier.fillMaxWidth().height(20.dp).appBottomSheetDragHandle(
                    onDrag = { amount -> scope.launch { drag.snapTo((drag.value + amount).coerceAtLeast(0f)) } },
                    onDragEnd = { if (drag.value > closeThreshold) close() else scope.launch { drag.animateTo(0f, tween(180)) } },
                    onDragCancel = { scope.launch { drag.animateTo(0f, tween(180)) } },
                ), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(36.dp).height(4.dp).background(Color.White.copy(alpha = .2f), CircleShape))
                }
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Box(Modifier.size(44.dp).clickable { AppHaptics.toggle(view); close() }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Close, "Close ticker search", tint = Color.White.copy(alpha = .6f), modifier = Modifier.size(19.dp))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp).clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = .045f)).border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Search, null, tint = Color.White.copy(alpha = .4f), modifier = Modifier.size(18.dp))
                    BasicTextField(query, { query = it.take(80) }, singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp), cursorBrush = SolidColor(Color(0xFFC8A44D)),
                        modifier = Modifier.weight(1f).focusRequester(focus), decorationBox = { inner ->
                            Box { if (query.isEmpty()) Text("Search ticker or company", color = Color.White.copy(alpha = .35f), fontSize = 14.sp); inner() }
                        })
                }
                Text(when {
                    loading -> "Searching tickers…"
                    failure -> "Couldn't search tickers · Tap to retry"
                    query.isBlank() -> guidance
                    results.isEmpty() -> "No matching tickers"
                    else -> "Choose a ticker"
                }, color = Color.White.copy(alpha = .4f), fontSize = 11.5.sp,
                    modifier = Modifier.fillMaxWidth().clickable(enabled = failure) { retry++ }.padding(horizontal = 16.dp, vertical = 12.dp))
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                    items(results, key = TickerChoice::symbol) { choice ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(enabled = !closing && !loading && !failure) {
                            AppHaptics.confirm(view)
                            onSelect(EquityChartAttachment(choice.symbol, choice.name, choice.exchange, choice.currency))
                        }.padding(horizontal = 8.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(choice.symbol, color = Color(0xFFC8A44D), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(choice.name, color = Color.White.copy(alpha = .8f), fontSize = 12.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (choice.exchange.isNotBlank()) Text(choice.exchange, color = Color.White.copy(alpha = .35f), fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

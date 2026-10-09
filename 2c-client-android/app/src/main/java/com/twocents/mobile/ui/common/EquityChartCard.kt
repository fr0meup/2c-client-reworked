package com.twocents.mobile.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** One reusable attachment for previews, feeds, quotes, and native post embeds; no discussion requests. */
@Composable
internal fun EquityChartCard(
    attachment: EquityChartAttachment,
    stateKey: String,
    onChange: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    val data = LocalTickerData.current
    val view = LocalView.current
    var period by rememberSaveable(stateKey, attachment.symbol) { mutableStateOf(TickerPeriod.Day) }
    var retry by remember(attachment.symbol) { mutableIntStateOf(0) }
    var points by remember(data, attachment.symbol, period) { mutableStateOf(emptyList<TickerPoint>()) }
    var loading by remember(data, attachment.symbol, period) { mutableStateOf(true) }
    var error by remember(data, attachment.symbol, period) { mutableStateOf<String?>(null) }
    var price by remember(data, attachment.symbol) {
        mutableStateOf(attachment.price?.let { TickerPrice(it, null, null, null) })
    }
    var details by remember(data, attachment.symbol) {
        mutableStateOf<TickerDetails?>(TickerDetails(attachment.name, attachment.exchange, attachment.currency, 0.0, null, null, ""))
    }
    LaunchedEffect(data, attachment.symbol, retry) {
        if (data == null) return@LaunchedEffect
        launch {
            try { data.price(attachment.symbol).takeIf { it.price != null }?.let { price = it } }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { /* A failed snapshot must not prevent historical chart rendering. */ }
        }
        launch {
            try { details = data.details(attachment.symbol) }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { /* Preserve the server's attachment identity if company details fail. */ }
        }
    }
    LaunchedEffect(data, attachment.symbol, period, retry) {
        loading = true
        error = null
        try {
            points = checkNotNull(data) { "Chart unavailable" }.chart(attachment.symbol, period)
        } catch (cancel: CancellationException) { throw cancel }
        catch (failure: Exception) { error = friendlyError(failure, "Chart unavailable") }
        finally { loading = false }
    }
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.fillMaxWidth().padding(top = 9.dp).clip(shape)
        .background(Color(0xFF141410)).border(1.dp, Color.White.copy(alpha = .08f), shape).padding(top = 7.dp, bottom = 3.dp)) {
        // These are the actual ticker components, not a compact reimplementation.
        TickerCompanyHeader(attachment.symbol, details, trailing = if (onRemove == null) null else {
            {
                Box(Modifier.size(36.dp).clickable { AppHaptics.toggle(view); onRemove() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Close, "Remove equity chart", tint = Color.White.copy(alpha = .64f), modifier = Modifier.size(18.dp))
                }
            }
        })
        TickerOverview(attachment.symbol, details, price, points, period, loading, error,
            onPeriod = { AppHaptics.toggle(view); period = it }, onRetryChart = { retry++ })
        if (onChange != null) Text("Change ticker", color = Color(0xFFC8A44D), fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth().clickable { AppHaptics.open(view); onChange() }.padding(horizontal = 12.dp, vertical = 10.dp))
    }
}

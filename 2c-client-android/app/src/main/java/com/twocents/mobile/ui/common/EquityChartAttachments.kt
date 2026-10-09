package com.twocents.mobile.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twocents.mobile.ui.feed.FeedPost
import kotlinx.coroutines.CancellationException

/** A type-6 post must never silently degrade to text because its feed metadata is incomplete. */
@Composable
internal fun EquityChartAttachments(post: FeedPost) {
    if (post.meta.equityCharts.isEmpty() && post.postType != 6) return
    val data = LocalTickerData.current
    var retry by remember(post.uuid) { mutableIntStateOf(0) }
    var charts by remember(data, post.uuid, post.meta.equityCharts) { mutableStateOf(post.meta.equityCharts) }
    var loading by remember(data, post.uuid, post.meta.equityCharts) { mutableStateOf(charts.isEmpty()) }
    LaunchedEffect(data, post.uuid, post.meta.equityCharts, retry) {
        if (post.meta.equityCharts.isNotEmpty()) return@LaunchedEffect
        loading = true
        try { charts = checkNotNull(data) { "Ticker data unavailable" }.attachments(post.uuid, force = retry > 0) }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { /* Keep a visible, retryable attachment instead of swallowing a missing chart. */ }
        finally { loading = false }
    }
    if (charts.isNotEmpty()) charts.forEach { EquityChartCard(it, stateKey = post.uuid) }
    else {
        val shape = RoundedCornerShape(14.dp)
        Column(Modifier.fillMaxWidth().padding(top = 9.dp).background(Color(0xFF141410), shape)
            .border(1.dp, Color.White.copy(alpha = .08f), shape).padding(17.dp)) {
            if (loading) {
                Text("Loading equity chart…", color = Color.White.copy(alpha = .4f), fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                TickerChartSkeleton(Modifier.fillMaxWidth().height(156.dp))
            } else Box(Modifier.fillMaxWidth().heightIn(min = 100.dp).clickable { retry++ }, contentAlignment = Alignment.Center) {
                Text("Equity chart unavailable · Tap to retry", color = Color.White.copy(alpha = .5f), fontSize = 12.sp)
            }
        }
    }
}

package com.twocents.mobile.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twocents.mobile.ui.feed.FeedPost
import kotlinx.coroutines.CancellationException

/** Handle condensed feed responses without dropping the type-11 attachment or loading another feed. */
@Composable
internal fun EquityPredictionAttachment(post: FeedPost) {
    if (post.meta.equityPrediction == null && post.postType != 11) return
    val data = LocalTickerData.current
    var retry by remember(post.uuid) { mutableIntStateOf(0) }
    var prediction by remember(data, post.uuid, post.meta.equityPrediction) { mutableStateOf(post.meta.equityPrediction) }
    var loading by remember(data, post.uuid, post.meta.equityPrediction) { mutableStateOf(prediction == null) }
    LaunchedEffect(data, post.uuid, post.meta.equityPrediction, retry) {
        if (post.meta.equityPrediction != null) return@LaunchedEffect
        loading = true
        try { prediction = data?.prediction(post.uuid, force = retry > 0) }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { /* Show a retryable attachment failure, not a silently text-only post. */ }
        finally { loading = false }
    }
    val loaded = prediction
    if (loaded != null) EquityPredictionCard(loaded)
    else {
        val shape = RoundedCornerShape(14.dp)
        Text(if (loading) "Loading equity prediction…" else "Equity prediction unavailable · Tap to retry",
            color = Color.White.copy(alpha = .45f), fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 9.dp).background(Color(0xFF141410), shape)
                .border(1.dp, Color.White.copy(alpha = .08f), shape).clickable(enabled = !loading) { retry++ }
                .padding(horizontal = 14.dp, vertical = 30.dp))
    }
}

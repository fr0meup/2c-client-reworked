package com.twocents.mobile.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val PickGold = Color(0xFFC8A44D)
// Match poll typography instead of inheriting Material's body line height/spacing.
internal val PickTextStyle = androidx.compose.ui.text.TextStyle(
    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
)

/** Mobile-first presentation; the existing controller still owns fetching and voting. */
@Composable
internal fun PicksCardContent(post: FeedPost, userVote: String?, result: FeedPicksResult?, onVote: (String) -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    // Keep consensus hidden until participation, except after resolution.
    val showResults = userVote in setOf("yes", "no") || result?.resolved == true
    Column(Modifier.fillMaxWidth().padding(top = 10.dp).clip(shape)
        .background(Color.White.copy(alpha = .018f))
        .border(.7.dp, Color.White.copy(alpha = .07f), shape).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pick", style = PickTextStyle, color = Color.White.copy(alpha = .5f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Text(if (result?.resolved == true) "Resolved${result.correctAnswer?.let { ": ${it.uppercase()}" }.orEmpty()}"
                else post.meta.resolutionDeadline?.let { "Resolves ${formatDeadline(it)}" } ?: if (result == null) "" else "Open",
                modifier = Modifier.weight(1f), color = if (result?.resolved == true) PickGold else Color.White.copy(alpha = .4f), fontSize = 10.5.sp,
                style = PickTextStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        PicksHistoryChart(post.meta.priceHistory)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PickChoice("yes", userVote, result?.yesAverageBalance?.takeIf { showResults }, result?.resolved == true, Modifier.weight(1f), onVote)
            PickChoice("no", userVote, result?.noAverageBalance?.takeIf { showResults }, result?.resolved == true, Modifier.weight(1f), onVote)
        }
        // Don't invent a 50/50 result while the request is still in flight.
        if (showResults && result != null) {
            Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = .09f))) {
                    Box(Modifier.fillMaxWidth(result.yesPercent.coerceIn(0, 100) / 100f).fillMaxHeight()
                        .background(Brush.horizontalGradient(listOf(PickGold, PickGold.copy(alpha = .4f)))))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${result.yesPercent}% Yes", style = PickTextStyle, color = PickGold.copy(alpha = .8f), fontSize = 10.5.sp)
                    Text("${result.noPercent}% No", style = PickTextStyle, color = Color.White.copy(alpha = .4f), fontSize = 10.5.sp)
                }
            }
        } else if (showResults) Text("Pick results unavailable", style = PickTextStyle, color = Color.White.copy(alpha = .35f), fontSize = 10.sp)
    }
}

@Composable
private fun PickChoice(choice: String, selected: String?, average: Double?, resolved: Boolean, modifier: Modifier, onVote: (String) -> Unit) {
    val active = selected == choice
    val shape = RoundedCornerShape(11.dp)
    Row(modifier.heightIn(min = 40.dp).clip(shape)
        .background(Brush.linearGradient(if (active) listOf(PickGold.copy(alpha = .08f), PickGold.copy(alpha = .02f))
            else listOf(Color.White.copy(alpha = .03f), Color.White.copy(alpha = .03f))))
        .border(.7.dp, if (active) PickGold.copy(alpha = .3f) else Color.White.copy(alpha = .06f), shape)
        .clickable(enabled = selected == null && !resolved) { onVote(choice) }.padding(horizontal = 7.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (active) {
            Box(Modifier.size(17.dp).clip(CircleShape).background(Color(0xFF34D399).copy(alpha = .18f)), contentAlignment = Alignment.Center) {
                FeedCheckIcon(Modifier.size(10.dp), Color(0xFF34D399), 3.2f)
            }
        }
        Text(choice.replaceFirstChar { it.uppercase() }, color = if (active) PickGold else Color.White.copy(alpha = if (selected != null) .4f else .8f),
            style = PickTextStyle, fontSize = 13.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        if (average != null) {
            // Measure the full amount before the flexible spacer. Giving both a
            // weight split the available width in half and clipped even modest NWs.
            Text(pollNetWorth(average), color = netWorthTierColor(average).copy(alpha = if (active || selected == null) 1f else .5f),
                style = PickTextStyle, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        }
    }
}

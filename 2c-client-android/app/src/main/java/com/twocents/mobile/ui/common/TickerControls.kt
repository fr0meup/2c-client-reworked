package com.twocents.mobile.ui.common

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Gold = Color(0xFFC8A44D)
private val Quiet = Color.White.copy(alpha = .58f)

/** Same compact sliding selection language as the profile's Posts/Comments/Votes tabs. */
@Composable
internal fun TickerFeedFilters(feed: TickerFeed, onTab: (TickerTab) -> Unit, onSort: (TickerSort) -> Unit) {
    val view = LocalView.current
    Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
        val tabWidth = 74.dp
        val tabPosition by animateDpAsState(
            targetValue = if (feed.tab == TickerTab.Posts) 3.dp else 3.dp + tabWidth,
            animationSpec = spring(dampingRatio = .72f, stiffness = 520f),
            label = "ticker-tab-selection",
        )
        Box(Modifier.align(Alignment.Center).clip(CircleShape).background(Color.White.copy(alpha = .035f))
            .border(1.dp, Color.White.copy(alpha = .08f), CircleShape)) {
            Box(Modifier.offset(x = tabPosition, y = 3.dp).width(tabWidth).height(28.dp)
                .clip(CircleShape).background(Gold.copy(alpha = .18f))
                .border(1.dp, Gold.copy(alpha = .42f), CircleShape))
            Row(Modifier.padding(3.dp)) {
                TickerPrimaryTab("Posts", feed.tab == TickerTab.Posts, tabWidth) {
                    if (feed.tab != TickerTab.Posts) { AppHaptics.navigate(view); onTab(TickerTab.Posts) }
                }
                TickerPrimaryTab("Replies", feed.tab == TickerTab.Replies, tabWidth) {
                    if (feed.tab != TickerTab.Replies) { AppHaptics.navigate(view); onTab(TickerTab.Replies) }
                }
            }
        }
        TickerSortDropdown(feed.sort, Modifier.align(Alignment.CenterEnd)) { selected ->
            if (feed.sort != selected) { AppHaptics.navigate(view); onSort(selected) }
        }
    }
}

@Composable
private fun TickerPrimaryTab(label: String, selected: Boolean, width: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Box(Modifier.width(width).height(28.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) {
        Text(label, color = if (selected) Gold else Quiet, fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun TickerSortDropdown(value: TickerSort, modifier: Modifier, onChange: (TickerSort) -> Unit) {
    val view = LocalView.current
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(Modifier.clip(CircleShape).clickable { AppHaptics.open(view); expanded = true }
            .padding(horizontal = 4.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value.apiValue.replaceFirstChar(Char::uppercase), color = Quiet, fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold)
            Icon(Icons.Rounded.KeyboardArrowDown, "Sort ticker activity", tint = Quiet,
                modifier = Modifier.size(14.dp).graphicsLayer { rotationZ = if (expanded) 180f else 0f })
        }
        AppDropdown(expanded = expanded, onDismissRequest = { expanded = false },
            modifier = Modifier.width(140.dp), shape = RoundedCornerShape(14.dp),
            containerColor = Color(0xFF141410), tonalElevation = 0.dp, shadowElevation = 24.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .1f))) {
            TickerSort.entries.forEach { option ->
                val selected = option == value
                DropdownMenuItem(
                    text = { Text(option.apiValue.replaceFirstChar(Char::uppercase),
                        color = if (selected) Gold else Color.White.copy(alpha = .7f), fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) },
                    onClick = { expanded = false; onChange(option) },
                    trailingIcon = { if (selected) Icon(Icons.Rounded.Check, null,
                        tint = Gold, modifier = Modifier.size(14.dp)) },
                    modifier = Modifier.padding(horizontal = 4.dp).height(35.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (selected) Gold.copy(alpha = .14f) else Color.Transparent),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
                )
            }
        }
    }
}

@Composable
internal fun TickerPeriodSelector(active: TickerPeriod, onChange: (TickerPeriod) -> Unit) {
    val view = LocalView.current
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        TickerPeriod.entries.forEach { option ->
            val selected = option == active
            Box(Modifier.width(48.dp).height(28.dp).clip(CircleShape)
                .background(if (selected) Gold.copy(alpha = .14f) else Color.Transparent)
                .border(1.dp, if (selected) Gold.copy(alpha = .34f) else Color.Transparent, CircleShape)
                .clickable { if (!selected) { AppHaptics.toggle(view); onChange(option) } },
                contentAlignment = Alignment.Center) {
                Text(option.label, color = if (selected) Gold else Quiet, fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
            }
        }
    }
}

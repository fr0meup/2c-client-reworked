package com.twocents.mobile.ui.feed

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.twocents.mobile.core.format.exactLocalDate

/** Expands in place; constrained rows ellipsize rather than displacing actions. */
@Composable
internal fun CommentTimestamp(createdAt: String, modifier: Modifier = Modifier) {
    var expanded by remember(createdAt) { mutableStateOf(false) }
    Text(
        if (expanded) exactLocalDate(createdAt, "MMM d, yyyy · HH:mm:ss") else feedTimeAgo(createdAt),
        modifier = modifier.clickable { expanded = !expanded },
        color = Color.White.copy(alpha = .4f), fontSize = 12.sp,
        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
    )
}

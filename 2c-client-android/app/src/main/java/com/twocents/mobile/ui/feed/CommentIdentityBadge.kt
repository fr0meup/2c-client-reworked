package com.twocents.mobile.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Small, non-interactive labels: OP beside the author, poll choice inside comment metadata. */
@Composable
internal fun CommentIdentityBadge(label: String, description: String) {
    val gold = Color(0xFFC8A44D)
    Text(label, color = gold, fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold,
        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
        modifier = Modifier.semantics { contentDescription = description }
            .background(gold.copy(alpha = .12f), RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 2.dp))
}

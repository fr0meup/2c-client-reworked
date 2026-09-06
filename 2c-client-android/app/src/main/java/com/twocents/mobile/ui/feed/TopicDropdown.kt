package com.twocents.mobile.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twocents.mobile.ui.theme.Background
import com.twocents.mobile.ui.theme.Gold

@Composable
internal fun TopicDropdown(
    activeTopic: String,
    maxHeight: Dp,
    onSelect: (String) -> Unit,
) {
    val cardShape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .shadow(elevation = 20.dp, shape = cardShape, clip = false)
            .clip(cardShape)
            .background(Background)
            .drawBehind {
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(0f, size.height - 20.dp.toPx()),
                    strokeWidth = 1f,
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = androidx.compose.ui.geometry.Offset(size.width, 0f),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height - 20.dp.toPx()),
                    strokeWidth = 1f,
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = androidx.compose.ui.geometry.Offset(20.dp.toPx(), size.height - 1f),
                    end = androidx.compose.ui.geometry.Offset(size.width - 20.dp.toPx(), size.height - 1f),
                    strokeWidth = 1f,
                )
            }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        TOPIC_GROUPS.forEachIndexed { groupIndex, group ->
            if (groupIndex > 0) {
                Spacer(Modifier.fillMaxWidth().height(3.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.06f)),
                )
                Spacer(Modifier.fillMaxWidth().height(6.dp))
            }
            Text(
                text = group.category.uppercase(),
                color = Color.White.copy(alpha = 0.38f),
                fontSize = 10.5.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
            group.items.forEach { item ->
                val selected = item == activeTopic
                Text(
                    text = item,
                    color = if (selected) Gold else Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    fontWeight = if (selected) {
                        androidx.compose.ui.text.font.FontWeight.Bold
                    } else {
                        androidx.compose.ui.text.font.FontWeight.Medium
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) Gold.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { onSelect(item) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

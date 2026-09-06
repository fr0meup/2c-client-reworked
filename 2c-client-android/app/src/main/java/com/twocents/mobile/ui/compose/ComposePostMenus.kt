package com.twocents.mobile.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twocents.mobile.ui.common.pressScale
import com.twocents.mobile.ui.common.rememberPressScale
import com.twocents.mobile.ui.theme.Gold

private val MenuSurface = Color(0xFF141410)

@Composable
fun ComposePostOptionsMenu(
    visible: Boolean,
    activeOption: ComposePostOption?,
    mediaCount: Int,
    hasVideo: Boolean,
    onDismiss: () -> Unit,
    onImages: () -> Unit,
    onPoll: () -> Unit,
    onLikert: () -> Unit,
    onGif: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(150)) + slideInVertically(tween(180, easing = FastOutSlowInEasing)) { it / 3 },
        exit = fadeOut(tween(150)) + slideOutVertically(tween(180, easing = FastOutSlowInEasing)) { it / 3 },
    ) {
        MenuCard {
            ComposeMenuItem(
                icon = MenuGlyph.Images,
                title = "Images & Video",
                subtitle = "Attach up to 4 images or 1 video",
                tint = if (mediaCount > 0) Gold else null,
                trailing = when {
                    hasVideo -> "1/1"
                    mediaCount > 0 -> "$mediaCount/4"
                    else -> null
                },
                onClick = onImages,
            )
            ComposeMenuItem(
                icon = MenuGlyph.Poll,
                title = "Poll",
                subtitle = "Add multiple-choice voting",
                tint = if (activeOption == ComposePostOption.Poll) Gold else null,
                onClick = onPoll,
            )
            ComposeMenuItem(
                icon = MenuGlyph.Likert,
                title = "Likert",
                subtitle = "1 to 5 agreement scale",
                tint = if (activeOption == ComposePostOption.Likert) Gold else null,
                onClick = onLikert,
            )
            ComposeMenuItem(
                icon = MenuGlyph.Gif,
                title = "GIFs",
                subtitle = "Search or choose from saved GIFs",
                onClick = onGif,
            )
        }
    }
}

@Composable
fun ComposePostTopicsMenu(
    visible: Boolean,
    topic: String,
    pinnedTopic: String,
    onSelect: (String) -> Unit,
    onPin: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(150)) + slideInVertically(tween(180, easing = FastOutSlowInEasing)) { it / 3 },
        exit = fadeOut(tween(150)) + slideOutVertically(tween(180, easing = FastOutSlowInEasing)) { it / 3 },
    ) {
        MenuCard {
            ComposeTopics.forEach { item ->
                val selected = item.name.equals(topic, ignoreCase = true)
                val pinned = item.name == pinnedTopic
                val icon = when (item.icon) {
                    ComposeTopicIcon.Chat -> MenuGlyph.Chat
                    ComposeTopicIcon.Dollar -> MenuGlyph.Dollar
                    ComposeTopicIcon.Bug -> MenuGlyph.Bug
                }
                ComposeMenuItem(
                    icon = icon,
                    title = item.name,
                    subtitle = item.subtitle,
                    tint = if (selected) Gold else null,
                    selected = selected,
                    trailingIcon = MenuGlyph.Pin,
                    trailingTint = if (pinned) Gold else Color.White.copy(alpha = 0.3f),
                    onTrailingClick = { onPin(item.name) },
                    onClick = { onSelect(item.name) },
                )
            }
        }
    }
}

@Composable
private fun MenuCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .width(250.dp)
            .shadow(12.dp, RoundedCornerShape(16.dp), clip = false)
            .clip(RoundedCornerShape(16.dp))
            .background(MenuSurface)
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        content()
    }
}

@Composable
private fun ComposeMenuItem(
    icon: MenuGlyph,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tint: Color? = null,
    selected: Boolean = false,
    trailing: String? = null,
    trailingIcon: MenuGlyph? = null,
    trailingTint: Color = Color.White.copy(alpha = 0.3f),
    onTrailingClick: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color.White.copy(alpha = 0.03f) else Color.Transparent)
            .pressScale(rememberPressScale(interaction))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MenuIcon(icon, tint ?: Color.White.copy(alpha = 0.75f))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = tint ?: Color.White, fontSize = 13.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Color.White.copy(alpha = 0.35f), fontSize = 11.sp, lineHeight = 13.sp)
        }
        if (trailing != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Gold.copy(alpha = 0.2f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(trailing, color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (trailingIcon != null) {
            val trailingInteraction = remember { MutableInteractionSource() }
            MenuIcon(
                glyph = trailingIcon,
                tint = trailingTint,
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = trailingInteraction,
                        indication = null,
                        onClick = { onTrailingClick?.invoke() },
                    )
                    .padding(3.dp),
            )
        }
    }
}

private enum class MenuGlyph { Images, Poll, Likert, Gif, Chat, Dollar, Bug, Pin }

@Composable
private fun MenuIcon(glyph: MenuGlyph, tint: Color, modifier: Modifier = Modifier.size(18.dp)) {
    Canvas(modifier) {
        val scale = size.minDimension / 24f
        val stroke = 2.dp.toPx() / scale
        val path = Path()

        fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
            path.moveTo(x1, y1)
            path.lineTo(x2, y2)
        }

        fun roundedRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float) {
            path.moveTo(left + radius, top)
            path.lineTo(right - radius, top)
            path.cubicTo(right - radius / 2, top, right, top + radius / 2, right, top + radius)
            path.lineTo(right, bottom - radius)
            path.cubicTo(right, bottom - radius / 2, right - radius / 2, bottom, right - radius, bottom)
            path.lineTo(left + radius, bottom)
            path.cubicTo(left + radius / 2, bottom, left, bottom - radius / 2, left, bottom - radius)
            path.lineTo(left, top + radius)
            path.cubicTo(left, top + radius / 2, left + radius / 2, top, left + radius, top)
        }

        withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
            when (glyph) {
                MenuGlyph.Images -> {
                    path.moveTo(21f, 12f)
                    path.lineTo(21f, 19f)
                    path.cubicTo(21f, 20.1f, 20.1f, 21f, 19f, 21f)
                    path.lineTo(5f, 21f)
                    path.cubicTo(3.9f, 21f, 3f, 20.1f, 3f, 19f)
                    path.lineTo(3f, 5f)
                    path.cubicTo(3f, 3.9f, 3.9f, 3f, 5f, 3f)
                    path.lineTo(12f, 3f)
                    line(16f, 5f, 22f, 5f)
                    line(19f, 2f, 19f, 8f)
                    path.addOval(androidx.compose.ui.geometry.Rect(7f, 7f, 11f, 11f))
                    path.moveTo(6f, 21f)
                    path.lineTo(15.1f, 11.9f)
                    path.cubicTo(15.9f, 11.1f, 17.1f, 11.1f, 17.9f, 11.9f)
                    path.lineTo(21f, 15f)
                }
                MenuGlyph.Poll -> {
                    line(3f, 3f, 3f, 21f)
                    line(3f, 21f, 21f, 21f)
                    line(18f, 17f, 18f, 9f)
                    line(13f, 17f, 13f, 5f)
                    line(8f, 17f, 8f, 14f)
                }
                MenuGlyph.Likert -> {
                    line(4f, 21f, 4f, 14f)
                    line(4f, 10f, 4f, 3f)
                    line(12f, 21f, 12f, 12f)
                    line(12f, 8f, 12f, 3f)
                    line(20f, 21f, 20f, 16f)
                    line(20f, 12f, 20f, 3f)
                    line(1f, 14f, 7f, 14f)
                    line(9f, 8f, 15f, 8f)
                    line(17f, 16f, 23f, 16f)
                }
                MenuGlyph.Gif -> {
                    roundedRect(2f, 2f, 22f, 22f, 2.18f)
                    line(7f, 2f, 7f, 22f)
                    line(17f, 2f, 17f, 22f)
                    line(2f, 12f, 22f, 12f)
                    line(2f, 7f, 7f, 7f)
                    line(2f, 17f, 7f, 17f)
                    line(17f, 17f, 22f, 17f)
                    line(17f, 7f, 22f, 7f)
                }
                MenuGlyph.Chat -> {
                    path.moveTo(21f, 15f)
                    path.cubicTo(21f, 16.1f, 20.1f, 17f, 19f, 17f)
                    path.lineTo(7f, 17f)
                    path.lineTo(3f, 21f)
                    path.lineTo(3f, 5f)
                    path.cubicTo(3f, 3.9f, 3.9f, 3f, 5f, 3f)
                    path.lineTo(19f, 3f)
                    path.cubicTo(20.1f, 3f, 21f, 3.9f, 21f, 5f)
                    path.close()
                }
                MenuGlyph.Dollar -> {
                    line(12f, 2f, 12f, 22f)
                    path.moveTo(17f, 5f)
                    path.lineTo(9.5f, 5f)
                    path.cubicTo(7.6f, 5f, 6f, 6.6f, 6f, 8.5f)
                    path.cubicTo(6f, 10.4f, 7.6f, 12f, 9.5f, 12f)
                    path.lineTo(14.5f, 12f)
                    path.cubicTo(16.4f, 12f, 18f, 13.6f, 18f, 15.5f)
                    path.cubicTo(18f, 17.4f, 16.4f, 19f, 14.5f, 19f)
                    path.lineTo(6f, 19f)
                }
                MenuGlyph.Bug -> {
                    line(8f, 2f, 9.88f, 3.88f)
                    line(14.12f, 3.88f, 16f, 2f)
                    line(9f, 7.13f, 9f, 6.13f)
                    line(15f, 7.13f, 15f, 6.13f)
                    path.moveTo(12f, 20f)
                    path.cubicTo(8.7f, 20f, 6f, 17.3f, 6f, 14f)
                    path.lineTo(6f, 11f)
                    path.cubicTo(6f, 8.8f, 7.8f, 7f, 10f, 7f)
                    path.lineTo(14f, 7f)
                    path.cubicTo(16.2f, 7f, 18f, 8.8f, 18f, 11f)
                    path.lineTo(18f, 14f)
                    path.cubicTo(18f, 17.3f, 15.3f, 20f, 12f, 20f)
                    line(12f, 20f, 12f, 11f)
                    line(6.53f, 9f, 3f, 5f)
                    line(6f, 13f, 2f, 13f)
                    line(6.8f, 17f, 3f, 21f)
                    line(17.47f, 9f, 21f, 5f)
                    line(18f, 13f, 22f, 13f)
                    line(17.2f, 17f, 21f, 21f)
                }
                MenuGlyph.Pin -> {
                    line(12f, 17f, 12f, 22f)
                    path.moveTo(5f, 17f)
                    path.lineTo(19f, 17f)
                    path.lineTo(19f, 15.24f)
                    path.cubicTo(19f, 14.48f, 18.57f, 13.79f, 17.89f, 13.45f)
                    path.lineTo(16.11f, 12.55f)
                    path.cubicTo(15.43f, 12.21f, 15f, 11.52f, 15f, 10.76f)
                    path.lineTo(15f, 6f)
                    path.lineTo(16f, 6f)
                    path.cubicTo(18.2f, 6f, 18.2f, 2f, 16f, 2f)
                    path.lineTo(8f, 2f)
                    path.cubicTo(5.8f, 2f, 5.8f, 6f, 8f, 6f)
                    path.lineTo(9f, 6f)
                    path.lineTo(9f, 10.76f)
                    path.cubicTo(9f, 11.52f, 8.57f, 12.21f, 7.89f, 12.55f)
                    path.lineTo(6.11f, 13.45f)
                    path.cubicTo(5.43f, 13.79f, 5f, 14.48f, 5f, 15.24f)
                    path.close()
                }
            }
            drawPath(path, tint, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

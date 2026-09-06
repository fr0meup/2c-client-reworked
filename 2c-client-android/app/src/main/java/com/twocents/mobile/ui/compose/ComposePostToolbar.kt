package com.twocents.mobile.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import com.twocents.mobile.ui.common.pressScale
import com.twocents.mobile.ui.common.rememberPressScale
import com.twocents.mobile.ui.theme.Gold

@Composable
fun ComposePostToolbar(
    optionsOpen: Boolean,
    topicOpen: Boolean,
    topic: String,
    hasSelection: Boolean,
    canPost: Boolean,
    isSubmitting: Boolean,
    draftsCount: Int,
    savedFeedback: Boolean,
    onToggleOptions: () -> Unit,
    onToggleTopic: () -> Unit,
    onObfuscate: () -> Unit,
    onSaveDraft: () -> Unit,
    onOpenDrafts: () -> Unit,
    onPost: () -> Unit,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val bottomColor = Color(0xFF141410)
    Row(
        modifier = modifier
            .background(bottomColor)
            .drawBehind {
                drawLine(
                    color = Color.White.copy(alpha = 0.07f),
                    start = androidx.compose.ui.geometry.Offset(0f, 0.5f),
                    end = androidx.compose.ui.geometry.Offset(size.width, 0.5f),
                    strokeWidth = 1f,
                )
            }
            .padding(start = 14.dp, top = 8.dp, end = 14.dp, bottom = bottomPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ToolbarIconButton(
            onClick = onToggleOptions,
            active = optionsOpen,
            contentDescription = "Post options",
        ) {
            ToolbarGlyph(
                ToolbarGlyphType.More,
                if (optionsOpen) Color(0xFFC8A44D) else Color.White.copy(alpha = 0.7f),
                Modifier.size(18.dp),
            )
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            ToolbarTextButton(
                label = "ZWJ",
                enabled = hasSelection,
                onClick = onObfuscate,
            )
            ToolbarBareIconButton(
                onClick = onOpenDrafts,
                contentDescription = "Saved drafts",
            ) {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.CenterStart) {
                    ToolbarGlyph(
                        ToolbarGlyphType.Drafts,
                        Color.White.copy(alpha = 0.45f),
                        Modifier.size(17.dp),
                    )
                    if (draftsCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Gold),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                draftsCount.coerceAtMost(9).toString(),
                                color = Color(0xFF0F0E0A),
                                fontSize = 8.sp,
                                lineHeight = 9.sp,
                                fontWeight = FontWeight.Black,
                            )
                        }
                    }
                }
            }
            ToolbarBareIconButton(
                onClick = onSaveDraft,
                enabled = canPost,
                contentDescription = "Save draft",
            ) {
                ToolbarGlyph(
                    ToolbarGlyphType.Save,
                    if (savedFeedback) Color(0xFF34D399) else Color.White.copy(alpha = 0.45f),
                    Modifier.size(17.dp),
                )
            }
            TopicButton(
                topic = topic,
                active = topicOpen,
                onClick = onToggleTopic,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .widthIn(max = 120.dp),
            )
            Box(
                modifier = Modifier
                    .widthIn(min = 58.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (canPost) Gold else Gold.copy(alpha = 0.35f))
                    .clickable(enabled = canPost && !isSubmitting, onClick = onPost)
                    .padding(horizontal = 15.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(color = Color(0xFF0F0E0A), strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                } else {
                    Text(
                        "Post",
                        color = Color(0xFF0F0E0A),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolbarIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    active: Boolean = false,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (active) Gold.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f))
            .border(1.dp, if (active) Gold.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f), CircleShape)
            .pressScale(rememberPressScale(interaction))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun ToolbarBareIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(32.dp)
            .pressScale(rememberPressScale(interaction))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun ToolbarTextButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .pressScale(rememberPressScale(interaction))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = if (enabled) 0.95f else 0.22f),
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.offset(x = 1.dp),
        )
    }
}

@Composable
private fun TopicButton(
    topic: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .then(modifier)
            .clip(RoundedCornerShape(20.dp))
            .background(if (active) Gold.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.05f))
            .border(1.dp, if (active) Gold.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .pressScale(rememberPressScale(interaction))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            topic,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.5.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        ToolbarGlyph(ToolbarGlyphType.Chevron, Color.White.copy(alpha = 0.4f), Modifier.size(12.dp))
    }
}

private enum class ToolbarGlyphType { More, Save, Drafts, Chevron }

@Composable
private fun ToolbarGlyph(type: ToolbarGlyphType, tint: Color, modifier: Modifier = Modifier.size(17.dp)) {
    Canvas(modifier) {
        val scale = size.minDimension / 24f
        val stroke = 2.dp.toPx() / scale
        withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
            when (type) {
                ToolbarGlyphType.More -> {
                    val dotStroke = 2.5.dp.toPx() / scale
                    drawCircle(tint, 1f, Offset(5f, 12f), style = Stroke(dotStroke))
                    drawCircle(tint, 1f, Offset(12f, 12f), style = Stroke(dotStroke))
                    drawCircle(tint, 1f, Offset(19f, 12f), style = Stroke(dotStroke))
                }
                ToolbarGlyphType.Save -> {
                    val path = Path().apply {
                        moveTo(19f, 21f)
                        lineTo(5f, 21f)
                        cubicTo(3.9f, 21f, 3f, 20.1f, 3f, 19f)
                        lineTo(3f, 5f)
                        cubicTo(3f, 3.9f, 3.9f, 3f, 5f, 3f)
                        lineTo(16f, 3f)
                        lineTo(21f, 8f)
                        lineTo(21f, 19f)
                        cubicTo(21f, 20.1f, 20.1f, 21f, 19f, 21f)
                        moveTo(17f, 21f)
                        lineTo(17f, 13f)
                        lineTo(7f, 13f)
                        lineTo(7f, 21f)
                        moveTo(7f, 3f)
                        lineTo(7f, 8f)
                        lineTo(15f, 8f)
                    }
                     drawPath(path, tint, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                ToolbarGlyphType.Drafts -> {
                    val path = Path().apply {
                        moveTo(14.5f, 2f)
                        lineTo(6f, 2f)
                        cubicTo(4.9f, 2f, 4f, 2.9f, 4f, 4f)
                        lineTo(4f, 20f)
                        cubicTo(4f, 21.1f, 4.9f, 22f, 6f, 22f)
                        lineTo(18f, 22f)
                        cubicTo(19.1f, 22f, 20f, 21.1f, 20f, 20f)
                        lineTo(20f, 7.5f)
                        close()
                        moveTo(14f, 2f)
                        lineTo(14f, 8f)
                        lineTo(20f, 8f)
                        moveTo(16f, 13f)
                        lineTo(8f, 13f)
                        moveTo(16f, 17f)
                        lineTo(8f, 17f)
                        moveTo(10f, 9f)
                        lineTo(8f, 9f)
                    }
                     drawPath(path, tint, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                ToolbarGlyphType.Chevron -> {
                    val path = Path().apply {
                        moveTo(6f, 9f)
                        lineTo(12f, 15f)
                        lineTo(18f, 9f)
                    }
                     drawPath(path, tint, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }
}

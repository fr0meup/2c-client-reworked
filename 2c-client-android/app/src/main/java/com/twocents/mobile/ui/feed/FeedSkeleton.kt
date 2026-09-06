package com.twocents.mobile.ui.feed

import com.twocents.mobile.ui.common.drawSkeletonRect

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

private val SkeletonFill = Color.White.copy(alpha = 0.075f)
private val SkeletonMutedFill = Color.White.copy(alpha = 0.055f)
private val SkeletonSeparator = Color.White.copy(alpha = 0.08f)
private val SkeletonDot = Color.White.copy(alpha = 0.15f)

private const val TEXT_CARD_HEIGHT_DP = 183f
private const val FIRST_MEDIA_CARD_HEIGHT_DP = 395f
private const val SECOND_MEDIA_CARD_HEIGHT_DP = 355f
private const val FEED_SKELETON_HEIGHT_DP =
    TEXT_CARD_HEIGHT_DP * 2f + FIRST_MEDIA_CARD_HEIGHT_DP + SECOND_MEDIA_CARD_HEIGHT_DP

/**
 * Four feed placeholders rendered as one static draw node.
 *
 * There is intentionally no shimmer or pulse: loading skeletons should not
 * keep a frame clock alive or spend battery while the network is stalled.
 */
@Composable
fun FeedSkeleton(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(FEED_SKELETON_HEIGHT_DP.dp),
    ) {
        var top = 0f
        top = drawFeedSkeletonCard(top = top, mediaHeight = null)
        top = drawFeedSkeletonCard(top = top, mediaHeight = 220.dp.toPx())
        top = drawFeedSkeletonCard(top = top, mediaHeight = null)
        drawFeedSkeletonCard(top = top, mediaHeight = 180.dp.toPx())
    }
}

private fun DrawScope.drawFeedSkeletonCard(top: Float, mediaHeight: Float?): Float {
    val horizontalPadding = 12.dp.toPx()
    val contentWidth = (size.width - horizontalPadding * 2f).coerceAtLeast(0f)
    val headerTop = top + 16.dp.toPx()

    skeletonRoundRect(
        x = horizontalPadding,
        y = headerTop,
        width = 68.dp.toPx(),
        height = 26.dp.toPx(),
        radius = 13.dp.toPx(),
    )

    val headerCenter = headerTop + 13.dp.toPx()
    var headerX = horizontalPadding + 75.dp.toPx()
    skeletonRoundRect(headerX, headerCenter - 6.dp.toPx(), 26.dp.toPx(), 12.dp.toPx(), 6.dp.toPx(), SkeletonMutedFill)
    headerX += 33.dp.toPx()
    drawCircle(SkeletonDot, radius = 1.5.dp.toPx(), center = Offset(headerX + 1.5.dp.toPx(), headerCenter))
    headerX += 10.dp.toPx()
    skeletonRoundRect(headerX, headerCenter - 6.5.dp.toPx(), 13.dp.toPx(), 13.dp.toPx(), 4.dp.toPx(), SkeletonMutedFill)
    headerX += 20.dp.toPx()
    drawCircle(SkeletonDot, radius = 1.5.dp.toPx(), center = Offset(headerX + 1.5.dp.toPx(), headerCenter))
    headerX += 10.dp.toPx()
    val availableTopicWidth = (size.width - horizontalPadding - 23.dp.toPx() - headerX).coerceAtLeast(16.dp.toPx())
    skeletonRoundRect(
        headerX,
        headerCenter - 6.dp.toPx(),
        minOf(45.dp.toPx(), availableTopicWidth),
        12.dp.toPx(),
        6.dp.toPx(),
        SkeletonMutedFill,
    )
    skeletonRoundRect(
        x = size.width - horizontalPadding - 16.dp.toPx(),
        y = headerCenter - 8.dp.toPx(),
        width = 16.dp.toPx(),
        height = 16.dp.toPx(),
        radius = 8.dp.toPx(),
        color = SkeletonMutedFill,
    )

    var cursorY = headerTop + 36.dp.toPx()
    skeletonRoundRect(
        x = horizontalPadding,
        y = cursorY,
        width = contentWidth * 0.68f,
        height = 17.dp.toPx(),
        radius = 8.5.dp.toPx(),
    )
    cursorY += 25.dp.toPx()
    skeletonRoundRect(
        x = horizontalPadding,
        y = cursorY,
        width = contentWidth * 0.92f,
        height = 14.dp.toPx(),
        radius = 7.dp.toPx(),
        color = SkeletonMutedFill,
    )

    if (mediaHeight == null) {
        cursorY += 20.dp.toPx()
        skeletonRoundRect(
            x = horizontalPadding,
            y = cursorY,
            width = contentWidth * 0.78f,
            height = 14.dp.toPx(),
            radius = 7.dp.toPx(),
            color = SkeletonMutedFill,
        )
        cursorY += 30.dp.toPx()
    } else {
        cursorY += 26.dp.toPx()
        skeletonRoundRect(
            x = horizontalPadding,
            y = cursorY,
            width = contentWidth,
            height = mediaHeight,
            radius = 14.dp.toPx(),
            color = SkeletonMutedFill,
        )
        cursorY += mediaHeight + 16.dp.toPx()
    }

    skeletonRoundRect(
        x = horizontalPadding,
        y = cursorY,
        width = minOf(125.dp.toPx(), contentWidth * 0.48f),
        height = 34.dp.toPx(),
        radius = 17.dp.toPx(),
    )
    val actionWidth = minOf(118.dp.toPx(), contentWidth * 0.45f)
    skeletonRoundRect(
        x = size.width - horizontalPadding - actionWidth,
        y = cursorY,
        width = actionWidth,
        height = 34.dp.toPx(),
        radius = 17.dp.toPx(),
    )

    val cardBottom = cursorY + 56.dp.toPx()
    drawRect(
        color = SkeletonSeparator,
        topLeft = Offset(0f, cardBottom - 1.dp.toPx()),
        size = Size(size.width, 1.dp.toPx()),
    )
    return cardBottom
}

private fun DrawScope.skeletonRoundRect(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    radius: Float,
    color: Color = SkeletonFill,
) {
    drawSkeletonRect(x, y, width, height, radius, color)
}

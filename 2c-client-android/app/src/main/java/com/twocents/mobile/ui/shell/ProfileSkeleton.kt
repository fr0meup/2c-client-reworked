package com.twocents.mobile.ui.shell

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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

private val ProfileSkeletonFill = Color.White.copy(alpha = 0.08f)
private val ProfileSkeletonDot = Color.White.copy(alpha = 0.15f)
private val ProfileCardSurface = Color.White.copy(alpha = 0.04f)
private val ProfileCardBorder = Color.White.copy(alpha = 0.08f)

private const val PROFILE_SKELETON_HEIGHT_DP = 756f

/** Static RN-sized profile loading state with no shimmer or frame clock. */
@Composable
internal fun ProfileSkeleton(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(PROFILE_SKELETON_HEIGHT_DP.dp),
    ) {
        val outerPadding = 12.dp.toPx()
        val contentWidth = (size.width - outerPadding * 2f).coerceAtLeast(0f)

        drawProfileCard(
            x = outerPadding,
            y = 10.dp.toPx(),
            width = contentWidth,
        )

        val tabsY = 330.dp.toPx()
        profileSkeletonRect((size.width - 284.dp.toPx()) / 2f, tabsY, 284.dp.toPx(), 36.dp.toPx(), 18.dp.toPx())
        val firstPostTop = 378.dp.toPx()
        drawProfilePostCard(outerPadding, firstPostTop, contentWidth)
        drawProfilePostCard(outerPadding, firstPostTop + 199.dp.toPx(), contentWidth)
    }
}

private fun DrawScope.drawProfileCard(x: Float, y: Float, width: Float) {
    val height = 312.dp.toPx()
    val radius = 20.dp.toPx()
    drawRoundRect(
        color = ProfileCardSurface,
        topLeft = Offset(x, y),
        size = Size(width, height),
        cornerRadius = CornerRadius(radius, radius),
    )
    drawRoundRect(
        color = ProfileCardBorder,
        topLeft = Offset(x, y),
        size = Size(width, height),
        cornerRadius = CornerRadius(radius, radius),
        style = Stroke(width = 1.dp.toPx()),
    )

    val innerX = x + 14.dp.toPx()
    val innerWidth = (width - 28.dp.toPx()).coerceAtLeast(0f)
    profileSkeletonRect(
        x = x + (width - 70.dp.toPx()) / 2f,
        y = y + 11.dp.toPx(),
        width = 70.dp.toPx(),
        height = 10.dp.toPx(),
        radius = 5.dp.toPx(),
    )
    repeat(5) { index ->
        val gridY = y + 39.dp.toPx() + index * 28.dp.toPx()
        drawRect(Color.White.copy(alpha = if (index == 0 || index == 4) .06f else .035f), Offset(innerX, gridY), Size(innerWidth, 1.dp.toPx()))
    }
    val chart = androidx.compose.ui.graphics.Path().apply {
        moveTo(innerX, y + 142.dp.toPx())
        cubicTo(innerX + innerWidth * .2f, y + 112.dp.toPx(), innerX + innerWidth * .32f, y + 130.dp.toPx(), innerX + innerWidth * .48f, y + 85.dp.toPx())
        cubicTo(innerX + innerWidth * .62f, y + 50.dp.toPx(), innerX + innerWidth * .78f, y + 100.dp.toPx(), innerX + innerWidth, y + 56.dp.toPx())
    }
    drawPath(chart, Color(0xFFC8A44D).copy(alpha = .26f), style = Stroke(2.dp.toPx()))
    val labelWidth = innerWidth * 0.6f
    profileSkeletonRect(
        x = innerX + (innerWidth - labelWidth) / 2f,
        y = y + 176.dp.toPx(),
        width = labelWidth,
        height = 14.dp.toPx(),
        radius = 8.dp.toPx(),
    )

    val statsY = y + 207.dp.toPx()
    val statWidth = 70.dp.toPx()
    val statHeight = 40.dp.toPx()
    for (index in 0..2) {
        val centerX = innerX + innerWidth * ((index * 2f + 1f) / 6f)
        profileSkeletonRect(
            x = centerX - statWidth / 2f,
            y = statsY,
            width = statWidth,
            height = 16.dp.toPx(),
            radius = 8.dp.toPx(),
        )
    }
    profileSkeletonRect(x + (width - 244.dp.toPx()) / 2f, y + 244.dp.toPx(), 244.dp.toPx(), 30.dp.toPx(), 15.dp.toPx())
    profileSkeletonRect(x + (width - 104.dp.toPx()) / 2f, y + 286.dp.toPx(), 104.dp.toPx(), 8.dp.toPx(), 4.dp.toPx())
}

private fun DrawScope.drawProfilePostCard(x: Float, y: Float, width: Float) {
    val horizontalPadding = 12.dp.toPx()
    val innerX = x + horizontalPadding
    val innerWidth = (width - horizontalPadding * 2f).coerceAtLeast(0f)
    val headerTop = y + 16.dp.toPx()

    profileSkeletonRect(innerX, headerTop, 68.dp.toPx(), 26.dp.toPx(), 13.dp.toPx())

    val headerCenter = headerTop + 13.dp.toPx()
    var headerX = innerX + 75.dp.toPx()
    profileSkeletonRect(headerX, headerCenter - 6.dp.toPx(), 26.dp.toPx(), 12.dp.toPx(), 6.dp.toPx())
    headerX += 33.dp.toPx()
    drawCircle(ProfileSkeletonDot, 1.5.dp.toPx(), Offset(headerX + 1.5.dp.toPx(), headerCenter))
    headerX += 10.dp.toPx()
    profileSkeletonRect(headerX, headerCenter - 6.5.dp.toPx(), 13.dp.toPx(), 13.dp.toPx(), 4.dp.toPx())
    headerX += 20.dp.toPx()
    drawCircle(ProfileSkeletonDot, 1.5.dp.toPx(), Offset(headerX + 1.5.dp.toPx(), headerCenter))
    headerX += 10.dp.toPx()
    profileSkeletonRect(
        x = headerX,
        y = headerCenter - 6.dp.toPx(),
        width = minOf(45.dp.toPx(), (x + width - horizontalPadding - 23.dp.toPx() - headerX).coerceAtLeast(0f)),
        height = 12.dp.toPx(),
        radius = 6.dp.toPx(),
    )
    profileSkeletonRect(
        x = x + width - horizontalPadding - 16.dp.toPx(),
        y = headerCenter - 8.dp.toPx(),
        width = 16.dp.toPx(),
        height = 16.dp.toPx(),
        radius = 8.dp.toPx(),
    )

    profileSkeletonRect(innerX, y + 52.dp.toPx(), innerWidth * 0.68f, 17.dp.toPx(), 6.dp.toPx())
    profileSkeletonRect(innerX, y + 77.dp.toPx(), innerWidth * 0.92f, 14.dp.toPx(), 6.dp.toPx())
    profileSkeletonRect(innerX, y + 97.dp.toPx(), innerWidth * 0.78f, 14.dp.toPx(), 6.dp.toPx())

    val bottomY = y + 127.dp.toPx()
    profileSkeletonRect(innerX, bottomY, minOf(125.dp.toPx(), innerWidth * 0.48f), 34.dp.toPx(), 17.dp.toPx())
    val actionWidth = minOf(118.dp.toPx(), innerWidth * 0.45f)
    profileSkeletonRect(
        x = x + width - horizontalPadding - actionWidth,
        y = bottomY,
        width = actionWidth,
        height = 34.dp.toPx(),
        radius = 17.dp.toPx(),
    )

    drawRect(
        color = ProfileCardBorder,
        topLeft = Offset(x, y + 182.dp.toPx()),
        size = Size(width, 1.dp.toPx()),
    )
}

private fun DrawScope.profileSkeletonRect(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    radius: Float,
) {
    drawSkeletonRect(x, y, width, height, radius, ProfileSkeletonFill)
}

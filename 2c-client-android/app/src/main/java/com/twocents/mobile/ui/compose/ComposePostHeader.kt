package com.twocents.mobile.ui.compose

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest

private val ComposeSurface = Color(0xFF141410)
private val BronzeText = Color(0xFF3F1815)
private val HeaderOutCubic = Easing { fraction -> 1f - (1f - fraction) * (1f - fraction) * (1f - fraction) }
private const val LOCATION_ICON_URL =
    "https://www.twocents.money/_next/image?url=%2F_next%2Fstatic%2Fmedia%2Flocation-icon.432s1sddmkeug.png&w=48&q=75&dpl=dpl_5ovAARAu8zMP9MtrCL9RTcRsDq7b"

private val PillImages = mapOf(
    "bronze" to "https://www.twocents.money/pills/bronze2.png",
    "silver" to "https://www.twocents.money/pills/silver2.png",
    "gold" to "https://www.twocents.money/pills/gold4.png",
    "platinum" to "https://www.twocents.money/pills/infiniteSafe2.png",
    "ultra" to "https://www.twocents.money/pills/ultra.png",
    "evil" to "https://www.twocents.money/pills/evil2.png",
    "admin" to "https://www.twocents.money/pills/andi2.png",
    "mod" to "https://www.twocents.money/pills/mod.png",
    "news" to "https://api.twocents.money/ugc/20260814/images/459874cc-cea1-4802-990a-5d830c871876/a49422bd-3c2d-4944-817a-a55cd7aeb6b3.png",
    "staff" to "https://twocents.money/pills/dark_staff_badge_bg.png",
    "penny" to "https://www.twocents.money/pills/penny.png",
)

@Composable
fun ComposePostHeader(
    profile: ComposeAuthorProfile?,
    authUuid: String?,
    title: String,
    onTitleChange: (String) -> Unit,
    dismissConfirmationVisible: Boolean,
    onRequestClose: () -> Unit,
    onConfirmClose: () -> Unit,
    onDismissControlBoundsChanged: (Rect) -> Unit,
    dragHandleModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(dragHandleModifier)
                .padding(top = 8.dp, bottom = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ComposeSurface)
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 2.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth()
                        .padding(end = 48.dp)
                        .clipToBounds(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ComposeNetworthPill(profile = profile, authUuid = authUuid)
                    if (!profile?.gender.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            GenderGlyph(gender = profile?.gender, modifier = Modifier.size(15.dp))
                            profile?.age?.let {
                                Text(it.toString(), color = Color.White.copy(alpha = 0.4f), fontSize = 14.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    if (!profile?.arena.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current).data(LOCATION_ICON_URL).build(),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(profile?.arena.orEmpty(), color = Color.White.copy(alpha = 0.4f), fontSize = 14.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                InlineDismissControl(
                    expanded = dismissConfirmationVisible,
                    onExpand = onRequestClose,
                    onConfirm = onConfirmClose,
                    onBoundsChanged = onDismissControlBoundsChanged,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = title,
                    onValueChange = { onTitleChange(it.take(150)) },
                    modifier = Modifier.weight(1f).padding(top = 6.dp, bottom = 2.dp),
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 17.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Bold,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    decorationBox = { innerTextField ->
                        Box {
                            if (title.isEmpty()) Text(
                                "Title",
                                color = Color.White.copy(alpha = 0.2f),
                                fontSize = 17.sp,
                                lineHeight = 22.sp,
                                fontWeight = FontWeight.Bold,
                                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                            )
                            innerTextField()
                        }
                    },
                )
                Text("optional", color = Color.White.copy(alpha = 0.2f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.06f)),
            )
        }
    }
}

@Composable
private fun InlineDismissControl(
    expanded: Boolean,
    onExpand: () -> Unit,
    onConfirm: () -> Unit,
    onBoundsChanged: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = androidx.compose.ui.platform.LocalView.current
    val transition = updateTransition(targetState = expanded, label = "compose-discard")
    val width = transition.animateDp(
        transitionSpec = { tween(180, easing = HeaderOutCubic) },
        label = "compose-discard-width",
    ) { open -> if (open) 136.dp else 48.dp }
    val labelAlpha = transition.animateFloat(
        transitionSpec = { tween(if (targetState) 140 else 80) },
        label = "compose-discard-label-alpha",
    ) { open -> if (open) 1f else 0f }
    val closeInteraction = remember { MutableInteractionSource() }
    val expandedWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { 136.dp.toPx() }

    Box(
        modifier = modifier
            .width(width.value)
            .height(44.dp)
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInRoot()
                onBoundsChanged(
                    Rect(
                        left = bounds.right - expandedWidthPx,
                        top = bounds.top,
                        right = bounds.right,
                        bottom = bounds.bottom,
                    ),
                )
            }
            .clickable(
                interactionSource = closeInteraction,
                indication = null,
                onClick = {
                    if (expanded) {
                        com.twocents.mobile.ui.common.AppHaptics.confirm(view)
                        onConfirm()
                    } else {
                        com.twocents.mobile.ui.common.AppHaptics.open(view)
                        onExpand()
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clipToBounds(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = labelAlpha.value }
                    .clip(RoundedCornerShape(17.dp))
                    .background(Color(0xFF211E18))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(17.dp),
                    ),
            )
            Text(
                text = "Are you sure?",
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 11.dp, end = 38.dp)
                    .graphicsLayer { alpha = labelAlpha.value },
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(48.dp)
                    .height(34.dp),
                contentAlignment = Alignment.Center,
            ) {
                CloseGlyph()
            }
        }
    }
}

@Composable
private fun CloseGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val scale = size.minDimension / 24f
        val stroke = 2.dp.toPx() / scale
        withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
            val path = Path().apply {
                moveTo(18f, 6f)
                lineTo(6f, 18f)
                moveTo(6f, 6f)
                lineTo(18f, 18f)
            }
            drawPath(
                path,
                Color.White.copy(alpha = 0.6f),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun GenderGlyph(gender: String?, modifier: Modifier = Modifier) {
    val male = gender.equals("m", true) || gender.equals("male", true)
    Canvas(modifier) {
        val color = if (male) Color(0xFFDBE5EC) else Color(0xFFEDDBE3)
        val stroke = 1.8.dp.toPx()
        if (male) {
            drawCircle(color, radius = size.minDimension * 0.25f, center = Offset(size.width * .38f, size.height * .62f), style = Stroke(stroke))
            drawLine(color, Offset(size.width * .55f, size.height * .45f), Offset(size.width * .9f, size.height * .1f), stroke, StrokeCap.Round)
            drawLine(color, Offset(size.width * .9f, size.height * .1f), Offset(size.width * .62f, size.height * .1f), stroke, StrokeCap.Round)
            drawLine(color, Offset(size.width * .9f, size.height * .1f), Offset(size.width * .9f, size.height * .38f), stroke, StrokeCap.Round)
        } else {
            drawCircle(color, radius = size.minDimension * .25f, center = Offset(size.width * .5f, size.height * .3f), style = Stroke(stroke))
            drawLine(color, Offset(size.width * .5f, size.height * .55f), Offset(size.width * .5f, size.height * .92f), stroke, StrokeCap.Round)
            drawLine(color, Offset(size.width * .28f, size.height * .75f), Offset(size.width * .72f, size.height * .75f), stroke, StrokeCap.Round)
        }
    }
}

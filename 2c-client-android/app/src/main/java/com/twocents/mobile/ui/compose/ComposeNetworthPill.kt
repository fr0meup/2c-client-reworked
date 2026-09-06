package com.twocents.mobile.ui.compose

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.BitmapImage
import coil3.DrawableImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private const val PILL_ASSET_WIDTH = 178f
private const val PILL_ASSET_HEIGHT = 56f
private const val PILL_HEIGHT_DP = 33
private const val PILL_MIN_WIDTH_DP = 62
private const val COMPACT_PILL_HEIGHT_DP = 27
private const val COMPACT_PILL_MIN_WIDTH_DP = 54

private enum class ComposePillTier {
    Bronze, Silver, Gold, Platinum, Ultra, Evil, Admin, Mod, News, Staff, Penny, Unverified,
}

private val PillImages = mapOf(
    ComposePillTier.Admin to "https://www.twocents.money/pills/andi2.png",
    ComposePillTier.Mod to "https://www.twocents.money/pills/mod.png",
    ComposePillTier.Evil to "https://www.twocents.money/pills/evil2.png",
    ComposePillTier.News to "https://api.twocents.money/ugc/20260814/images/459874cc-cea1-4802-990a-5d830c871876/a49422bd-3c2d-4944-817a-a55cd7aeb6b3.png",
    ComposePillTier.Staff to "https://twocents.money/pills/dark_staff_badge_bg.png",
    ComposePillTier.Bronze to "https://www.twocents.money/pills/bronze2.png",
    ComposePillTier.Silver to "https://www.twocents.money/pills/silver2.png",
    ComposePillTier.Gold to "https://www.twocents.money/pills/gold4.png",
    ComposePillTier.Platinum to "https://www.twocents.money/pills/infiniteSafe2.png",
    ComposePillTier.Ultra to "https://www.twocents.money/pills/ultra.png",
    ComposePillTier.Penny to "https://www.twocents.money/pills/penny.png",
)

private val PillColors = mapOf(
    ComposePillTier.Bronze to Color(0xFF3F1815),
    ComposePillTier.Gold to Color(0xFF3D2319),
    ComposePillTier.Silver to Color(0xFF1F2225),
    ComposePillTier.Platinum to Color(0xFF002A4B),
    ComposePillTier.Admin to Color(0xFF07080A),
    ComposePillTier.Evil to Color(0xFFFF4E5A),
    ComposePillTier.Mod to Color(0xFFFFB34B),
    ComposePillTier.News to Color(0xFF3B4E69),
    ComposePillTier.Staff to Color.White,
    ComposePillTier.Unverified to Color(0xFFACC4C1),
    ComposePillTier.Ultra to Color(0xFF232323),
    ComposePillTier.Penny to Color(0xFF3D1A08),
)

private val SpecialLabels = mapOf(
    ComposePillTier.Admin to "ANDI",
    ComposePillTier.Mod to "MOD",
    ComposePillTier.News to "POLLS",
    ComposePillTier.Staff to "STAFF",
    ComposePillTier.Penny to "PENNY",
)

private val ModeratorUuids = setOf(
    "3d940646-0265-4c72-bc93-a98dd9b8d68e",
    "4814e409-b55a-4a71-86f6-34da365d4119",
    "e5dbcd9b-6dc9-4831-ad30-43f50d72487d",
    "444cc266-c0ca-43f2-bd49-5be4d80114e3",
    "afbde593-9103-4046-8caf-d28cfd5ced61",
    "69c8c5e3-135c-43d3-8649-17c3dedea54e",
)

@Composable
fun ComposeNetworthPill(
    profile: ComposeAuthorProfile?,
    authUuid: String?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val balance = profile?.balance?.takeIf(Double::isFinite) ?: 0.0
    val uuid = (profile?.uuid ?: authUuid).orEmpty()
    val tier = resolvePillTier(
        uuid = uuid,
        subscriptionType = profile?.subscriptionType ?: 1,
        balance = balance,
        role = profile?.role,
    )
    val pillColor = PillColors.getValue(tier)
    val specialLabel = SpecialLabels[tier]
    val formattedValue = NumberFormat.getIntegerInstance(Locale.US).format(balance.roundToLong())
    val displayValue = specialLabel ?: formattedValue

    if (tier == ComposePillTier.Unverified) {
        UnverifiedPill(value = formattedValue, modifier = modifier, compact = compact)
        return
    }

    val pillImage = PillImages.getValue(tier)
    val isStaff = tier == ComposePillTier.Staff
    // This pill is also rendered inside Material dropdowns, whose popup layout
    // queries intrinsic width. A SubcomposeLayout cannot answer that query and
    // caused a runtime crash when mention suggestions opened. Compose both
    // layers up front and preserve the same foreground-first measurement here.
    Layout(
        modifier = modifier.clip(CircleShape),
        content = {
            PillBackground(
                imageUrl = pillImage,
                fallbackColor = pillColor,
                isStaff = isStaff,
                modifier = Modifier.fillMaxSize(),
            )
            PillForeground(
                displayValue = displayValue,
                pillColor = pillColor,
                hasSpecialLabel = specialLabel != null,
                isNegative = balance < 0,
                compact = compact,
            )
        },
    ) { measurables, constraints ->
        val heightDp = if (compact) COMPACT_PILL_HEIGHT_DP else PILL_HEIGHT_DP
        val minWidthDp = if (compact) COMPACT_PILL_MIN_WIDTH_DP else PILL_MIN_WIDTH_DP
        val height = heightDp.dp.roundToPx()
        val foreground = measurables[1].measure(
            constraints.copy(
                minWidth = 0,
                minHeight = height,
                maxHeight = height,
            ),
        )
        val width = maxOf(minWidthDp.dp.roundToPx(), foreground.width)
            .coerceAtMost(constraints.maxWidth)
        val background = measurables[0].measure(Constraints.fixed(width, height))

        layout(width, height) {
            background.placeRelative(0, 0)
            foreground.placeRelative((width - foreground.width) / 2, 0)
        }
    }
}

private fun resolvePillTier(
    uuid: String,
    subscriptionType: Int,
    balance: Double,
    role: String?,
): ComposePillTier {
    val lower = uuid.lowercase(Locale.US)
    return when {
        lower == "admin" -> ComposePillTier.Admin
        lower == "penny" -> ComposePillTier.Penny
        lower == "staff" || role == "staff" -> ComposePillTier.Staff
        lower == "news" || role == "news" -> ComposePillTier.News
        role == "moderator" || ModeratorUuids.contains(lower) -> ComposePillTier.Mod
        subscriptionType == 0 -> ComposePillTier.Unverified
        balance >= 10_000_000 -> ComposePillTier.Ultra
        balance >= 1_000_000 -> ComposePillTier.Platinum
        balance >= 300_000 -> ComposePillTier.Gold
        balance >= 50_000 -> ComposePillTier.Silver
        balance >= 0 -> ComposePillTier.Bronze
        else -> ComposePillTier.Evil
    }
}

@Composable
private fun PillForeground(
    displayValue: String,
    pillColor: Color,
    hasSpecialLabel: Boolean,
    isNegative: Boolean,
    compact: Boolean,
) {
    Row(
        modifier = Modifier
            .padding(
                start = if (hasSpecialLabel) {
                    if (compact) 11.dp else 13.5.dp
                } else {
                    if (compact) 8.5.dp else 11.dp
                },
                end = if (compact) 11.dp else 13.5.dp,
            )
            .fillMaxHeight(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (hasSpecialLabel) 0.dp else 2.5.dp),
    ) {
        if (!hasSpecialLabel) {
            DollarIcon(color = pillColor, isNegative = isNegative, compact = compact)
        }
        Text(
            text = displayValue,
            color = pillColor,
            fontSize = if (compact) 12.5.sp else 14.5.sp,
            fontWeight = if (hasSpecialLabel) FontWeight.ExtraBold else FontWeight.Bold,
            letterSpacing = (-0.2).sp,
            maxLines = 1,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
            modifier = if (hasSpecialLabel) Modifier.offset(y = 0.5.dp) else Modifier,
        )
    }
}

@Composable
private fun DollarIcon(
    color: Color,
    isNegative: Boolean,
    compact: Boolean,
) {
    Box(
        modifier = Modifier
            .size(if (compact) 14.5.dp else 17.dp)
            .clip(CircleShape)
            .border(1.2.dp, color.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$",
            color = color,
            fontSize = if (compact) 9.5.sp else 11.5.sp,
            fontWeight = FontWeight.ExtraBold,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
            modifier = if (isNegative) {
                Modifier.graphicsLayer { rotationZ = 180f }
            } else {
                Modifier
            },
        )
    }
}

@Composable
private fun PillBackground(
    imageUrl: String,
    fallbackColor: Color,
    isStaff: Boolean,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val imageLoader = remember(context) { context.imageLoader }
    val bitmap = produceState<Bitmap?>(initialValue = null, imageLoader, imageUrl) {
        val result = imageLoader.execute(
            ImageRequest.Builder(context)
                .data(imageUrl)
                .build(),
        )
        value = (result as? SuccessResult)?.image?.toBitmap()
    }.value

    Canvas(modifier.clip(CircleShape)) {
        drawRoundRect(
            color = fallbackColor,
            size = size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
        )
        bitmap?.let { loadedBitmap ->
            if (isStaff) {
                drawBitmapToBounds(loadedBitmap)
            } else {
                drawThreeSlice(loadedBitmap)
            }
        }
    }
}

private fun coil3.Image.toBitmap(): Bitmap? = when (this) {
    is BitmapImage -> bitmap
    is DrawableImage -> {
        val drawableWidth = width.coerceAtLeast(1)
        val drawableHeight = height.coerceAtLeast(1)
        Bitmap.createBitmap(drawableWidth, drawableHeight, Bitmap.Config.ARGB_8888).also { target ->
            val canvas = android.graphics.Canvas(target)
            drawable.setBounds(0, 0, drawableWidth, drawableHeight)
            drawable.draw(canvas)
        }
    }
    else -> null
}

private fun DrawScope.drawBitmapToBounds(bitmap: Bitmap) {
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawBitmap(
            bitmap,
            null,
            RectF(0f, 0f, size.width, size.height),
            PillPaint,
        )
    }
}

private fun DrawScope.drawThreeSlice(bitmap: Bitmap) {
    val capWidth = size.height * 0.5f
    val fullImageWidth = size.height * (PILL_ASSET_WIDTH / PILL_ASSET_HEIGHT)

    drawIntoCanvas { canvas ->
        val nativeCanvas = canvas.nativeCanvas

        nativeCanvas.save()
        nativeCanvas.clipRect(0f, 0f, capWidth, size.height)
        nativeCanvas.drawBitmap(
            bitmap,
            null,
            RectF(0f, 0f, fullImageWidth, size.height),
            PillPaint,
        )
        nativeCanvas.restore()

        nativeCanvas.save()
        nativeCanvas.clipRect(capWidth, 0f, size.width - capWidth, size.height)
        nativeCanvas.drawBitmap(
            bitmap,
            null,
            RectF(0f, 0f, size.width, size.height),
            PillPaint,
        )
        nativeCanvas.restore()

        nativeCanvas.save()
        nativeCanvas.clipRect(size.width - capWidth, 0f, size.width, size.height)
        nativeCanvas.drawBitmap(
            bitmap,
            null,
            RectF(size.width - fullImageWidth, 0f, size.width, size.height),
            PillPaint,
        )
        nativeCanvas.restore()
    }
}

private val PillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    isFilterBitmap = true
    isDither = true
}

@Composable
private fun UnverifiedPill(value: String, modifier: Modifier, compact: Boolean) {
    Row(
        modifier = modifier
            .height(if (compact) COMPACT_PILL_HEIGHT_DP.dp else PILL_HEIGHT_DP.dp)
            .defaultMinSize(minWidth = if (compact) 52.dp else 60.dp)
            .clip(CircleShape)
            .border(1.5.dp, Color(0xFF4B5563), CircleShape)
            .padding(
                start = if (compact) 9.dp else 11.dp,
                end = if (compact) 11.dp else 13.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = "fag",
            color = Color.White,
            fontSize = if (compact) 12.5.sp else 14.5.sp,
            fontWeight = FontWeight.ExtraBold,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
        )
        Text(
            text = value,
            color = Color.White.copy(alpha = 0.35f),
            fontSize = if (compact) 10.sp else 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
        )
    }
}

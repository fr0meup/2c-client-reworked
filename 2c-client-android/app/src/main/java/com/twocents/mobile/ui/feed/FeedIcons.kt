package com.twocents.mobile.ui.feed

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val WEB_PATH = "M12 2a10 10 0 100 20 10 10 0 000-20zM2 12h20M12 2a15.3 15.3 0 014 10 15.3 15.3 0 01-4 10 15.3 15.3 0 01-4-10 15.3 15.3 0 014-10z"
private const val EYE_OUTER_PATH = "M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"
private const val EYE_INNER_PATH = "M12 9a3 3 0 100 6 3 3 0 000-6z"
private const val COMMENT_PATH = "M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"
private const val UPVOTE_PATH = "M13.73 4a2 2 0 0 0-3.46 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z"
private const val DOWNVOTE_PATH = "M13.73 20a2 2 0 0 1-3.46 0l-8-14A2 2 0 0 1 4 3h16a2 2 0 0 1 1.73 3Z"
private const val CHECK_PATH = "M20 6L9 17l-5-5"

private const val MALE_PATH = "M15 0C15.0689 0.0000069 15.1362 0.006261 15.2012 0.0195312C15.2767 0.0349486 15.3487 0.0603917 15.417 0.0917969C15.4552 0.109374 15.4919 0.129302 15.5273 0.151367C15.5651 0.174891 15.6016 0.200315 15.6357 0.228516C15.685 0.269191 15.7308 0.31402 15.7715 0.363281C15.9144 0.536206 16 0.758164 16 1V6C16 6.55228 15.5523 7 15 7C14.4477 6.99997 14 6.55226 14 6V3.41211L10.8887 6.52344C11.5872 7.50419 11.9999 8.70322 12 9.99902C12 13.3127 9.31371 15.999 6 15.999C2.68631 15.999 0 13.3127 0 9.99902C0.0003358 6.68561 2.68652 3.99904 6 3.99902C7.29536 3.99902 8.49399 4.41138 9.47461 5.10938L12.585 2H10C9.44788 1.99993 9.00019 1.55207 9 1C9.00004 0.447759 9.44778 0.0000322 10 0H15ZM6 5.99902C3.79109 5.99904 2.00034 7.79018 2 9.99902C2 12.2081 3.79088 13.999 6 13.999C8.20914 13.999 10 12.2082 10 9.99902C9.99985 8.99384 9.62801 8.07565 9.01562 7.37305C8.93608 7.32887 8.86052 7.2736 8.79297 7.20605C8.72512 7.13813 8.66925 7.06244 8.625 6.98242C7.92244 6.37068 7.00472 5.99902 6 5.99902Z"
private const val FEMALE_PATH = "M12 3C15.3137 3 18 5.68629 18 9C18 11.9745 15.835 14.4408 12.9951 14.915C12.9975 14.9431 13 14.9713 13 15V16.9951H15.0049C15.5572 16.9951 16.0049 17.4428 16.0049 17.9951C16.0048 18.5473 15.5571 18.9951 15.0049 18.9951H13V21C13 21.5523 12.5523 22 12 22C11.4477 22 11 21.5523 11 21V18.9951H9.00488C8.45269 18.9951 8.00494 18.5473 8.00488 17.9951C8.00488 17.4429 8.45265 16.9952 9.00488 16.9951H11V15C11 14.9714 11.0015 14.9431 11.0039 14.915C8.16448 14.4404 6 11.9742 6 9C6 5.68629 8.68629 3 12 3ZM12 5C9.79086 5 8 6.79086 8 9C8 11.2091 9.79086 13 12 13C14.2091 13 16 11.2091 16 9C16 6.79086 14.2091 5 12 5Z"
private const val NB_PATH = "M6 -0.000137C6.55224 -0.000137 6.99992 0.447644 7 0.999863V2.3817L8.55273 1.60533L8.64648 1.56432C9.12037 1.38462 9.66295 1.58957 9.89453 2.0526C10.1261 2.51568 9.96421 3.07229 9.53613 3.34361L9.44727 3.39439L8.23633 3.99986L9.44727 4.60533L9.53613 4.65611C9.96422 4.9274 10.126 5.48404 9.89453 5.94713C9.66297 6.41025 9.12042 6.61515 8.64648 6.43541L8.55273 6.39439L7 5.61803V6.99986C7 7.02814 6.99742 7.05617 6.99512 7.08385C9.83505 7.55802 11.9999 10.0253 12 12.9999C12 16.3136 9.31371 18.9999 6 18.9999C2.68629 18.9999 0 16.3136 0 12.9999C0.0000701 10.0257 2.16444 7.55841 5.00391 7.08385C5.00161 7.05619 5 7.02811 5 6.99986V5.61803L3.44727 6.39439L3.35352 6.43541C2.87958 6.61515 2.33703 6.41025 2.10547 5.94713C1.874 5.48404 2.03578 4.9274 2.46387 4.65611L2.55273 4.60533L3.76367 3.99986L2.55273 3.39439L2.46387 3.34361C2.03579 3.07229 1.87393 2.51568 2.10547 2.0526C2.33705 1.58957 2.87963 1.38462 3.35352 1.56432L3.44727 1.60533L5 2.3817V0.999863C5.00008 0.447644 5.44776 -0.000137 6 -0.000137ZM6 8.99986C3.79091 8.99986 2.00008 10.7908 2 12.9999C2 15.209 3.79086 16.9999 6 16.9999C8.20914 16.9999 10 15.209 10 12.9999C9.99992 10.7908 8.20909 8.99986 6 8.99986Z"

@Composable
internal fun FeedWebIcon(modifier: Modifier = Modifier, color: Color = Color.White.copy(alpha = 0.4f)) {
    Icon(Icons.Outlined.Language, contentDescription = "Web", tint = color, modifier = modifier)
}

@Composable
internal fun FeedEyeIcon(modifier: Modifier = Modifier, color: Color = Color.White.copy(alpha = 0.4f)) {
    Icon(Icons.Outlined.Visibility, contentDescription = "Views", tint = color, modifier = modifier)
}

@Composable
internal fun FeedCommentIcon(modifier: Modifier = Modifier, color: Color) =
    FilledStrokedSvgIcon(COMMENT_PATH, modifier, color, 1.8f)

@Composable
internal fun FeedVoteIcon(up: Boolean, modifier: Modifier = Modifier, color: Color) =
    FilledStrokedSvgIcon(if (up) UPVOTE_PATH else DOWNVOTE_PATH, modifier, color, 2f)

@Composable
internal fun FeedCheckIcon(modifier: Modifier = Modifier, color: Color = Color.White, strokeWidth: Float = 3.5f) =
    StrokedSvgIcon(CHECK_PATH, modifier, color, strokeWidth)

@Composable
internal fun FeedGenderIcon(gender: String, size: Dp, modifier: Modifier = Modifier) {
    val normalized = gender.trim().lowercase()
    val male = normalized == "male" || normalized == "m"
    val female = normalized == "female" || normalized == "f"
    val pathData = if (male) MALE_PATH else if (female) FEMALE_PATH else NB_PATH
    val path = remember(pathData) { PathParser().parsePathString(pathData).toPath() }
    val femaleScale = 0.93f
    val width = if (male) size else if (female) size * femaleScale else size * (12f / 19f)
    val height = if (male) size else if (female) size * femaleScale * (19f / 15f) else size
    val viewportWidth = if (male) 16f else if (female) 15f else 12f
    val viewportHeight = if (male) 16f else 19f
    val originX = if (female) 4f else 0f
    val originY = if (female) 2f else 0f
    val colors = when {
        male -> listOf(Color(0xFFDBE5EC), Color(0xFF9FB4C2))
        female -> listOf(Color(0xFFEDDBE3), Color(0xFFBFA1AD))
        else -> listOf(Color(0xFFEBE1D8), Color(0xFFBDAE9F))
    }
    Canvas(modifier.size(width, height)) {
        val sx = this.size.width / viewportWidth
        val sy = this.size.height / viewportHeight
        withTransform({
            scale(sx, sy, pivot = Offset.Zero)
            translate(-originX, -originY)
        }) {
            drawPath(
                path = path,
                brush = Brush.linearGradient(
                    colors = colors,
                    start = Offset(viewportWidth / 2f + originX, originY),
                    end = Offset(viewportWidth / 2f + originX, viewportHeight + originY),
                ),
            )
        }
    }
}

@Composable
private fun StrokedSvgIcon(pathData: String, modifier: Modifier, color: Color, strokeWidth: Float) {
    val path = remember(pathData) { PathParser().parsePathString(pathData).toPath() }
    Canvas(modifier) {
        val scale = minOf(size.width, size.height) / 24f
        val dx = (size.width - 24f * scale) / 2f
        val dy = (size.height - 24f * scale) / 2f
        withTransform({ translate(dx, dy); scale(scale, scale, pivot = Offset.Zero) }) {
            drawPath(path, color, style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
private fun FilledStrokedSvgIcon(pathData: String, modifier: Modifier, color: Color, strokeWidth: Float) {
    val path = remember(pathData) { PathParser().parsePathString(pathData).toPath() }
    Canvas(modifier) {
        val scale = minOf(size.width, size.height) / 24f
        val dx = (size.width - 24f * scale) / 2f
        val dy = (size.height - 24f * scale) / 2f
        withTransform({ translate(dx, dy); scale(scale, scale, pivot = Offset.Zero) }) {
            drawPath(path, color)
            drawPath(path, color, style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

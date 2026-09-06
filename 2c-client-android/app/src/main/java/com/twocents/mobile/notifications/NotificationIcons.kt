package com.twocents.mobile.notifications

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Exact 24x24 Lucide paths used by the RN notification UI. */
object NotificationIcons {
    val ArrowBigUp = lucide("ArrowBigUp", "M9 19a1 1 0 0 0 1 1h4a1 1 0 0 0 1-1v-6a1 1 0 0 1 1-1h3.293a.707.707 0 0 0 .5-1.207l-7.086-7.086a1 1 0 0 0-1.414 0l-7.086 7.086a.707.707 0 0 0 .5 1.207H8a1 1 0 0 1 1 1z")
    val ArrowBigDown = lucide("ArrowBigDown", "M9 5a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v6a1 1 0 0 0 1 1h3.293a.707.707 0 0 1 .5 1.207l-7.086 7.086a1 1 0 0 1-1.414 0l-7.086-7.086a.707.707 0 0 1 .5-1.207H8a1 1 0 0 0 1-1z")
    val MessageSquareText = lucide(
        "MessageSquareText",
        "M22 17a2 2 0 0 1-2 2H6.828a2 2 0 0 0-1.414.586l-2.202 2.202A.71.71 0 0 1 2 21.286V5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2z",
        "M7 11h10",
        "M7 15h6",
        "M7 7h8",
    )
    val Target = lucide(
        "Target",
        "M22 12a10 10 0 1 1-20 0a10 10 0 1 1 20 0",
        "M18 12a6 6 0 1 1-12 0a6 6 0 1 1 12 0",
        "M14 12a2 2 0 1 1-4 0a2 2 0 1 1 4 0",
    )
    val CheckCircle2 = lucide(
        "CheckCircle2",
        "M22 12a10 10 0 1 1-20 0a10 10 0 1 1 20 0",
        "m9 12 2 2 4-4",
    )
    val TrendingUp = lucide("TrendingUp", "M16 7h6v6", "m22 7-8.5 8.5-5-5L2 17")
    val BarChart3 = lucide(
        "BarChart3",
        "M3 3v16a2 2 0 0 0 2 2h16",
        "M18 17V9",
        "M13 17V5",
        "M8 17v-3",
    )
    val UserPlus = lucide(
        "UserPlus",
        "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2",
        "M13 7a4 4 0 1 1-8 0a4 4 0 1 1 8 0",
        "M19 8v6",
        "M22 11h-6",
    )
    val Users = lucide(
        "Users",
        "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2",
        "M13 7a4 4 0 1 1-8 0a4 4 0 1 1 8 0",
        "M22 21v-2a4 4 0 0 0-3-3.87",
        "M16 3.13a4 4 0 0 1 0 7.75",
    )
    val Bell = lucide(
        "Bell",
        "M10.268 21a2 2 0 0 0 3.464 0",
        "M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326",
    )
    val CheckCheck = lucide("CheckCheck", "M18 6 7 17l-5-5", "m22 10-7.5 7.5L13 16")
    val RotateCw = lucide("RotateCw", "M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8", "M21 3v5h-5")
}

private fun lucide(name: String, vararg paths: String): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    paths.forEach { path ->
        builder.addPath(
            pathData = PathParser().parsePathString(path).toNodes(),
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return builder.build()
}

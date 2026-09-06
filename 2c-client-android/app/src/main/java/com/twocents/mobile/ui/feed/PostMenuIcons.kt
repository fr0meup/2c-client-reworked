package com.twocents.mobile.ui.feed

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** The same Lucide glyphs used by 2c-client. */
internal object PostMenuIcons {
    val Link2 = lucide("Link2", "M9 17H7A5 5 0 0 1 7 7h2", "M15 7h2a5 5 0 1 1 0 10h-2", "M8 12h8")
    val Quote = lucide("Quote", "M3 21c3 0 7-1 7-8V5c0-1.25-.756-2-2-2H4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2h6", "M15 21c3 0 7-1 7-8V5c0-1.25-.757-2-2-2h-4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2h6")
    val Bookmark = lucide("Bookmark", "M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z")
    val UserPlus = lucide("UserPlus", "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2", "M13 7a4 4 0 1 1-8 0a4 4 0 1 1 8 0", "M19 8v6", "M22 11h-6")
    val UserCheck = lucide("UserCheck", "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2", "M13 7a4 4 0 1 1-8 0a4 4 0 1 1 8 0", "m16 11 2 2 4-4")
    val Mail = lucide("Mail", "M22 7l-8.991 5.727a2 2 0 0 1-2.009 0L2 7", "M4 4h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2")
    val MessageSquare = lucide("MessageSquare", "M21 15a4 4 0 0 1-4 4H8l-5 3V7a4 4 0 0 1 4-4h10a4 4 0 0 1 4 4z")
    val Ban = lucide("Ban", "M4.929 4.929a10 10 0 1 0 14.142 14.142A10 10 0 0 0 4.929 4.929", "m4.9 4.9 14.2 14.2")
    val VolumeX = lucide("VolumeX", "M11 5 6 9H2v6h4l5 4z", "m22 9-6 6", "m16 9 6 6")
    val Trash2 = lucide("Trash2", "M3 6h18", "M8 6V4h8v2", "M19 6l-1 14H6L5 6", "M10 11v5", "M14 11v5")
    val Copy = lucide("Copy", "M8 8h12v12H8z", "M16 8V4H4v12h4")
}

private fun lucide(name: String, vararg paths: String): ImageVector {
    val builder = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    paths.forEach { raw ->
        builder.addPath(
            pathData = PathParser().parsePathString(raw).toNodes(),
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return builder.build()
}

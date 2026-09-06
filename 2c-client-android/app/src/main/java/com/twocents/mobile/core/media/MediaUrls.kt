package com.twocents.mobile.core.media

import java.util.Locale

internal val DirectMediaUrl = Regex(
    "https?://\\S+?(?:gif|png|jpe?g|webp)(?:\\?\\S*)?",
    RegexOption.IGNORE_CASE,
)

internal fun String.looksLikeVideoUrl(): Boolean = substringBefore('?').lowercase(Locale.US).let { path ->
    path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".mov") || path.endsWith(".m4v")
}

internal fun String.looksLikeGifUrl(): Boolean = substringBefore('?').lowercase(Locale.US).let { path ->
    path.endsWith(".gif") || path.endsWith(".gifv") || path.contains("/giphy.gif") ||
        Regex("/\\d+\\.gif$").containsMatchIn(path)
}

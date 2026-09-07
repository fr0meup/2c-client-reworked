package com.twocents.mobile.notifications

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

/** Presentation parsing is isolated from row layout so wording rules have one owner. */
internal data class ParsedNotification(
    val actor: String? = null,
    val preview: String? = null,
    val isDownvote: Boolean = false,
    val action: String? = null,
)

internal fun parseNotificationMessage(type: String, message: String): ParsedNotification {
    if (message.isBlank()) return ParsedNotification()
    if (type in setOf("trending_post", "pick_post", "pick_resolved", "balance_updated")) {
        return ParsedNotification(preview = message.replace("**", ""))
    }
    if (type == "room_reply") {
        Regex("^Reply from\\s+\\*\\*(.+?)\\*\\*\\s+in\\s+\\*\\*(.+?)\\*\\*:\\s*(.*)$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(message)?.let { match ->
                return ParsedNotification(
                    actor = match.groupValues[1].trim(),
                    preview = match.groupValues[3].replace("**", "").trim().ifBlank { null },
                    action = "replied to your message in ${match.groupValues[2].trim()}",
                )
            }
    }
    Regex("^(.+?)\\s+by\\s+(?:\\*\\*)?([^:*]+?)(?:\\*\\*)?:\\s*(.*)$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .find(message)?.let { match ->
            return ParsedNotification(
                actor = match.groupValues[2].trim(),
                preview = match.groupValues[3].replace("**", "").trim().ifBlank { null },
                isDownvote = match.groupValues[1].startsWith("downvoted", ignoreCase = true),
            )
        }
    Regex("^(?:New reply from|Replied by|Reply from|Comment from)\\s+(?:\\*\\*)?([^:*]+?)(?:\\*\\*)?:\\s*(.*)$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .find(message)?.let { match ->
            return ParsedNotification(match.groupValues[1].trim(), match.groupValues[2].replace("**", "").trim().ifBlank { null })
        }
    Regex("^(?:\\*\\*)?(.+?)(?:\\*\\*)?\\s+followed you", RegexOption.IGNORE_CASE)
        .find(message)?.let { return ParsedNotification(actor = it.groupValues[1].trim()) }
    Regex("^(?:\\*\\*)?(.+?)(?:\\*\\*)?\\s+(replied|commented|upvoted|downvoted|voted|started following)(?:\\s+(?:to\\s+)?your\\s+[^:]*)?:\\s*(.*)$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .find(message)?.let { match ->
            return ParsedNotification(
                actor = match.groupValues[1].trim(),
                preview = match.groupValues[3].replace("**", "").trim().ifBlank { null },
                isDownvote = match.groupValues[2].startsWith("downvoted", ignoreCase = true),
            )
        }
    Regex("^\\*\\*(.+?)\\*\\*\\s*(.*)$", RegexOption.DOT_MATCHES_ALL)
        .find(message)?.let { match ->
            return ParsedNotification(match.groupValues[1].trim(), match.groupValues[2].replace("**", "").trim().ifBlank { null })
        }
    return ParsedNotification(preview = message.replace("**", ""))
}

internal fun notificationActionLabel(type: String, downvote: Boolean): String = when (type) {
    "post_voted" -> if (downvote) "downvoted your post" else "upvoted your post"
    "comment_voted" -> if (downvote) "downvoted your comment" else "upvoted your comment"
    "post_replied" -> "replied to your post"
    "comment_replied" -> "replied to your comment"
    "room_reply" -> "replied to your message"
    "pick_post" -> "posted a new pick"
    "pick_resolved" -> "pick was resolved"
    "trending_post" -> "is trending"
    "poll_voted" -> "voted on your poll"
    "followed" -> "followed you"
    "followed_by" -> "followed you from a post"
    "balance_updated" -> "balance update"
    else -> ""
}

internal fun timeAgo(raw: String): String {
    val instant = runCatching { Instant.parse(raw) }.getOrElse {
        runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrElse { return "now" }
    }
    val now = Instant.now()
    val minutes = ChronoUnit.MINUTES.between(instant, now).coerceAtLeast(0)
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        minutes < 1_440 -> "${minutes / 60}h"
        minutes < 10_080 -> "${minutes / 1_440}d"
        else -> "${minutes / 10_080}w"
    }
}

internal val NoFontPadding = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))

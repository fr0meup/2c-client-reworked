package com.twocents.mobile.ui.common

import android.net.Uri
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import com.twocents.mobile.ui.theme.Gold
import com.twocents.mobile.ui.profile.ProfileNavigationBus

private val UrlPattern = Regex("(?:https?://|www\\.)[^\\s<>]+", RegexOption.IGNORE_CASE)
private val InlineMentionPattern = Regex("\\[(@[^]]+)]\\(/user/([0-9a-fA-F-]{32,36})\\)")

@Suppress("DEPRECATION")
@Composable
fun LinkifiedText(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
    linkColor: androidx.compose.ui.graphics.Color = Gold,
    maxLines: Int = Int.MAX_VALUE,
    onTextClick: (() -> Unit)? = null,
) {
    val uriHandler = LocalUriHandler.current
    val annotated = remember(text, linkColor) { linkify(text, linkColor) }
    val style = TextStyle(
        color = color,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
    if (annotated.getStringAnnotations("URL", 0, annotated.length).isEmpty()) {
        Text(text = annotated, modifier = modifier, maxLines = maxLines, style = style)
    } else {
        ClickableText(
            text = annotated,
            modifier = modifier,
            maxLines = maxLines,
            style = style,
            onClick = { offset ->
                val raw = annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.item
                if (raw == null) onTextClick?.invoke() else {
                    val mentionUuid = raw.removePrefix("mention:").takeIf { raw.startsWith("mention:") }
                    if (mentionUuid != null) ProfileNavigationBus.open(mentionUuid)
                    else {
                        val normalized = if (raw.startsWith("www.", true)) "https://$raw" else raw
                        if (!AppLinkRouter.open(normalized)) runCatching { uriHandler.openUri(normalized) }
                    }
                }
            },
        )
    }
}

private fun linkify(raw: String, linkColor: androidx.compose.ui.graphics.Color): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    while (cursor < raw.length) {
        val mention = InlineMentionPattern.find(raw, cursor)
        val url = UrlPattern.find(raw, cursor)
        val useMention = mention != null && (url == null || mention.range.first <= url.range.first)
        val match = if (useMention) mention else url
        if (match == null) break
        if (match.range.first > cursor) append(raw.substring(cursor, match.range.first))
        if (useMention) {
            val start = length
            append(match.groupValues[1])
            addStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold), start, length)
            addStringAnnotation("URL", "mention:${match.groupValues[2]}", start, length)
        } else {
            val cleanUrl = match.value.trimEnd('.', ',', ')', ']', '!', '?')
            val suffix = match.value.removePrefix(cleanUrl)
            val start = length
            append(compactUrl(cleanUrl))
            addStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline), start, length)
            addStringAnnotation("URL", cleanUrl, start, length)
            append(suffix)
        }
        cursor = match.range.last + 1
    }
    if (cursor < raw.length) append(raw.substring(cursor))
}

private fun compactUrl(raw: String): String {
    if (raw.length <= 48) return raw
    val normalized = if (raw.startsWith("www.", true)) "https://$raw" else raw
    val parsed = runCatching { Uri.parse(normalized) }.getOrNull()
    val host = parsed?.host?.removePrefix("www.").orEmpty()
    val path = parsed?.encodedPath.orEmpty().trimEnd('/')
    val compact = if (host.isNotBlank()) host + path else raw
    return compact.take(45).trimEnd('/', '-', '_') + "…"
}

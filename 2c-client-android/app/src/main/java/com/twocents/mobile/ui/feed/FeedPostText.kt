package com.twocents.mobile.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.foundation.text.ClickableText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.platform.LocalUriHandler
import com.twocents.mobile.ui.common.AppLinkRouter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twocents.mobile.ui.common.LinkifiedText

private val InlinePattern = Regex(
    "(\\[([^]]+)]\\(([^)\\s]+)\\))|(\\*\\*\\*[\\s\\S]+?\\*\\*\\*)|(\\*\\*[\\s\\S]+?\\*\\*)|(\\*[^*\\n]+?\\*)|((?:https?://|www\\.)[^\\s<>\\[\\]()]+)",
    setOf(RegexOption.IGNORE_CASE),
)
private val MediaPattern = Regex(
    "https?://[^\\s<>\"'`]+?\\.(?:gif|gifv|webp|png|jpe?g|apng|avif|bmp|svg|heic|heif|tiff?|ico)(?:[?#][^\\s<>\"'`]*)?",
    RegexOption.IGNORE_CASE,
)

internal data class FeedPostContent(
    val visibleText: String,
    val gifUrls: List<String>,
    val isLong: Boolean,
)

internal fun prepareFeedPostContent(post: FeedPost): FeedPostContent {
    val clean = cleanFeedText(post.text)
    val textMedia = MediaPattern.findAll(clean).map { normalizeMediaUrl(it.value) }.distinct().toList()
    val metaGif = post.meta.giphyUrl?.let(::normalizeMediaUrl)
    val gifs = buildList {
        addAll(textMedia)
        if (metaGif != null && metaGif !in textMedia) add(metaGif)
    }
    val visible = textMedia.fold(clean) { text, url ->
        text.replace(url, "").replace(url.replace("/giphy.gif", "/200.gif"), "")
    }.let { text -> post.meta.giphyUrl?.let { text.replace(it, "") } ?: text }
        .trim()
        .takeUnless { it == "\u200B" }
        .orEmpty()
    return FeedPostContent(visible, gifs, visible.length > 400)
}

internal fun cleanFeedText(text: String): String = text
    .replace("&nbsp;", " ", ignoreCase = true)
    .replace("\u00A0", " ")
    .replace("&amp;", "&", ignoreCase = true)
    .replace("&lt;", "<", ignoreCase = true)
    .replace("&gt;", ">", ignoreCase = true)
    .replace("&quot;", "\"", ignoreCase = true)
    .replace("&apos;", "'", ignoreCase = true)
    .replace("&#39;", "'", ignoreCase = true)
    .replace(Regex("[\\u200B-\\u200F\\u2028-\\u202F\\u2060-\\u206F\\uFEFF\\u00AD\\u061C\\u180E\\uFFFC\\uFFF9-\\uFFFB\\u034F]"), "")
    .trim()

internal fun normalizeMediaUrl(url: String) = url.replace(Regex("/\\d+\\.gif(?=([?#]|$))", RegexOption.IGNORE_CASE), "/giphy.gif")

@Composable
internal fun FeedPostText(
    text: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
) {
    if (text.isBlank()) return
    val bodySize = if (compact) 13.sp else 15.sp
    // Keep automatic wraps tight. Paragraph spacing is inserted separately
    // below, so it must not be baked into every wrapped line.
    val lineHeight = if (compact) 17.sp else 19.sp
    val bodyColor = Color.White.copy(alpha = if (compact) 0.8f else 0.9f)
    val lines = text.split('\n')

    Column(modifier = modifier.fillMaxWidth()) {
        var index = 0
        var pendingBlankLines = 0
        // Any run of blank source lines collapses to one visual paragraph row.
        val paragraphGap = if (compact) 17.dp else 19.dp
        while (index < lines.size) {
            val line = lines[index]
            when {
                line.startsWith(">") || line.startsWith("│") -> {
                    val quoteLines = mutableListOf<String>()
                    while (index < lines.size && (lines[index].startsWith(">") || lines[index].startsWith("│"))) {
                        quoteLines += lines[index].removePrefix(">").removePrefix("│").trimStart()
                        index++
                    }
                    if (pendingBlankLines >= 1) Spacer(Modifier.height(paragraphGap))
                    Row(modifier = Modifier.padding(vertical = 4.dp)) {
                        Box(
                            Modifier
                                .width(2.dp)
                                .height(((if (compact) 17 else 19) * quoteLines.size).dp)
                                .background(Color(0xFFC8A44D).copy(alpha = 0.35f)),
                        )
                        MarkdownLine(
                            text = markdownInline(quoteLines.joinToString("\n")),
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = if (compact) 12.5.sp else 14.5.sp,
                            lineHeight = lineHeight,
                            fontStyle = FontStyle.Italic,
                            maxLines = maxLines,
                            modifier = Modifier.padding(start = 10.dp),
                        )
                    }
                    pendingBlankLines = 0
                }
                line.startsWith("- ") || line.startsWith("• ") -> {
                    if (pendingBlankLines >= 1) Spacer(Modifier.height(paragraphGap))
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(vertical = 3.dp)) {
                        while (index < lines.size && (lines[index].startsWith("- ") || lines[index].startsWith("• "))) {
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    text = "•",
                                    color = Color(0xFFC8A44D).copy(alpha = 0.7f),
                                    fontSize = bodySize,
                                    lineHeight = lineHeight,
                                    modifier = Modifier.width(16.dp),
                                )
                                MarkdownLine(
                                    text = markdownInline(lines[index].drop(2)),
                                    color = bodyColor,
                                    fontSize = bodySize,
                                    lineHeight = lineHeight,
                                    maxLines = maxLines,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            index++
                        }
                    }
                    pendingBlankLines = 0
                }
                line.isBlank() || line == "\u3164" || line == "ㅤ" -> {
                    pendingBlankLines++
                    index++
                }
                else -> {
                    if (pendingBlankLines >= 1) Spacer(Modifier.height(paragraphGap))
                    else if (index > 0 && pendingBlankLines == 0 && lines[index - 1].isNotBlank()) {
                        // An authored newline needs a little separation from the
                        // previous source line; automatic wraps stay at lineHeight.
                        Spacer(Modifier.height(3.dp))
                    }
                    MarkdownLine(
                        text = markdownInline(line),
                        color = bodyColor,
                        fontSize = bodySize,
                        lineHeight = lineHeight,
                        maxLines = maxLines,
                    )
                    pendingBlankLines = 0
                    index++
                }
            }
        }
    }
}

/** Shared by post detail and the profile Comments tab so their text rhythm cannot drift. */
@Composable
internal fun CommentBodyText(text: String, modifier: Modifier = Modifier) {
    if (text.isBlank()) return
    LinkifiedText(
        text = text,
        color = Color.White.copy(alpha = .9f),
        fontSize = 13.5.sp,
        lineHeight = 17.sp,
        modifier = modifier,
        linkColor = Color(0xFFC8A44D),
    )
}

/** Comment text uses the same 400-character disclosure threshold as feed posts. */
@Composable
internal fun ExpandableCommentBody(text: String, stableKey: String, modifier: Modifier = Modifier) {
    if (text.isBlank()) return
    // Comment rows are recycled by LazyColumn. Keying saveable state to the
    // comment keeps an explicit Show more choice when it leaves the viewport.
    var expanded by rememberSaveable(stableKey) { mutableStateOf(false) }
    val collapsible = text.length > 400
    val visible = if (collapsible && !expanded) text.take(400).trimEnd() + "…" else text
    Column(modifier) {
        CommentBodyText(visible)
        if (collapsible) {
            Text(
                text = if (expanded) "Show less" else "Show more",
                color = Color(0xFFC8A44D),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { expanded = !expanded }.padding(top = 4.dp, bottom = 2.dp),
            )
        }
    }
}

private fun markdownInline(raw: String): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    InlinePattern.findAll(raw).forEach { match ->
        if (match.range.first > cursor) append(raw.substring(cursor, match.range.first))
        val token = match.value
        when {
            token.startsWith("[") -> {
                val label = match.groups[2]?.value ?: token
                val url = match.groups[3]?.value
                val start = length
                pushStyle(SpanStyle(color = Color(0xFFC8A44D), textDecoration = TextDecoration.Underline))
                append(label)
                pop()
                if (url != null) addStringAnnotation("URL", url, start, length)
            }
            token.startsWith("***") -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, color = Color.White))
                append(token.removeSurrounding("***"))
                pop()
            }
            token.startsWith("**") -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White))
                append(token.removeSurrounding("**"))
                pop()
            }
            token.startsWith("*") -> {
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                append(token.removeSurrounding("*"))
                pop()
            }
            else -> {
                val start = length
                pushStyle(SpanStyle(color = Color(0xFFC8A44D), textDecoration = TextDecoration.Underline))
                append(compactFeedUrl(token))
                pop()
                addStringAnnotation("URL", token, start, length)
            }
        }
        cursor = match.range.last + 1
    }
    if (cursor < raw.length) append(raw.substring(cursor))
}

@Suppress("DEPRECATION")
@Composable
private fun MarkdownLine(
    text: AnnotatedString,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    modifier: Modifier = Modifier,
    fontStyle: FontStyle? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    val uriHandler = LocalUriHandler.current
    val style = TextStyle(
        color = color,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontStyle = fontStyle,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
    if (text.getStringAnnotations("URL", 0, text.length).isEmpty()) {
        Text(
            text = text,
            modifier = modifier,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            style = style,
        )
    } else {
        ClickableText(
            text = text,
            modifier = modifier,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            style = style,
            onClick = { offset ->
                text.getStringAnnotations("URL", offset, offset).firstOrNull()?.item?.let { raw ->
                    val normalized = if (raw.startsWith("www.", true)) "https://$raw" else raw
                    if (!AppLinkRouter.open(normalized)) runCatching { uriHandler.openUri(normalized) }
                }
            },
        )
    }
}

private fun compactFeedUrl(raw: String): String {
    if (raw.length <= 48) return raw
    val withoutScheme = raw.removePrefix("https://").removePrefix("http://").removePrefix("www.")
    return withoutScheme.take(45).trimEnd('/', '-', '_') + "…"
}

package com.twocents.mobile.ui.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.TextRange
import kotlin.random.Random
import com.twocents.mobile.ui.common.transformMentionMarkup

enum class ComposeMark {
    Bold,
    Italic,
}

data class ComposeStyleRange(
    val start: Int,
    val end: Int,
    val mark: ComposeMark,
)

/** Keeps the editor text stable while applying RN-style formatting as spans. */
class ComposeRichTextTransformation(
    private val ranges: List<ComposeStyleRange>,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val styled = AnnotatedString.Builder(text.text).apply {
            ranges.forEach { range ->
                val start = range.start.coerceIn(0, text.length)
                val end = range.end.coerceIn(start, text.length)
                if (start < end) {
                    addStyle(
                        style = when (range.mark) {
                            ComposeMark.Bold -> SpanStyle(fontWeight = FontWeight.Bold)
                            ComposeMark.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
                        },
                        start = start,
                        end = end,
                    )
                }
            }

            text.text.lineSequence().forEachIndexed { index, line ->
                val lineStart = text.text.lineStart(index)
                when {
                    line.startsWith("│ ") -> addStyle(
                        SpanStyle(color = Color.White.copy(alpha = 0.68f), fontStyle = FontStyle.Italic),
                        lineStart,
                        (lineStart + line.length).coerceAtMost(text.length),
                    )
                    line.startsWith("• ") -> addStyle(
                        SpanStyle(color = Color.White.copy(alpha = 0.92f)),
                        lineStart,
                        (lineStart + line.length).coerceAtMost(text.length),
                    )
                }
            }
        }.toAnnotatedString()
        return transformMentionMarkup(styled)
    }
}

private fun String.lineStart(lineIndex: Int): Int {
    if (lineIndex <= 0) return 0
    var seen = 0
    for (index in indices) {
        if (this[index] == '\n') {
            seen += 1
            if (seen == lineIndex) return index + 1
        }
    }
    return length
}

fun remapComposeStyleRanges(
    oldText: String,
    newText: String,
    ranges: List<ComposeStyleRange>,
    activeMarks: Set<ComposeMark>,
): List<ComposeStyleRange> {
    if (oldText == newText) return ranges
    val prefixLength = commonPrefixLength(oldText, newText)
    val suffixLength = commonSuffixLength(oldText, newText, prefixLength)
    val removedEnd = oldText.length - suffixLength
    val insertedEnd = newText.length - suffixLength
    val removedLength = (removedEnd - prefixLength).coerceAtLeast(0)
    val insertedLength = (insertedEnd - prefixLength).coerceAtLeast(0)
    val delta = insertedLength - removedLength

    val remapped = ranges.flatMap { range ->
        buildList {
            val beforeEnd = minOf(range.end, prefixLength)
            if (range.start < beforeEnd) {
                add(range.copy(end = beforeEnd))
            }

            val oldAfterStart = maxOf(range.start, removedEnd)
            if (oldAfterStart < range.end) {
                val afterStart = (oldAfterStart + delta).coerceIn(insertedEnd, newText.length)
                val afterEnd = (range.end + delta).coerceIn(afterStart, newText.length)
                if (afterStart < afterEnd) add(range.copy(start = afterStart, end = afterEnd))
            }
        }
    }.toMutableList()

    if (insertedLength > 0 && activeMarks.isNotEmpty()) {
        activeMarks.forEach { mark ->
            remapped += ComposeStyleRange(prefixLength, insertedEnd, mark)
        }
    }
    return normalizeComposeStyleRanges(remapped, newText.length)
}

fun normalizeComposeStyleRanges(ranges: List<ComposeStyleRange>, textLength: Int): List<ComposeStyleRange> {
    val valid = ranges
        .mapNotNull { range ->
            val start = range.start.coerceIn(0, textLength)
            val end = range.end.coerceIn(start, textLength)
            if (start < end) range.copy(start = start, end = end) else null
        }
        .distinct()
        .sortedWith(compareBy<ComposeStyleRange>({ it.mark.ordinal }, { it.start }, { it.end }))

    val merged = mutableListOf<ComposeStyleRange>()
    valid.forEach { range ->
        val previous = merged.lastOrNull()
        if (previous != null && previous.mark == range.mark && range.start <= previous.end) {
            merged[merged.lastIndex] = previous.copy(end = maxOf(previous.end, range.end))
        } else {
            merged += range
        }
    }
    return merged
}

fun composeSelectionHasMark(
    selection: TextRange,
    ranges: List<ComposeStyleRange>,
    mark: ComposeMark,
): Boolean {
    val start = selection.min
    val end = selection.max
    if (start == end) {
        val target = (start - 1).coerceAtLeast(0)
        return ranges.any { it.mark == mark && target >= it.start && target < it.end }
    }

    var coveredUntil = start
    ranges
        .asSequence()
        .filter { it.mark == mark && it.end > start && it.start < end }
        .sortedBy { it.start }
        .forEach { range ->
            if (range.start > coveredUntil) return false
            coveredUntil = maxOf(coveredUntil, range.end)
        }
    return coveredUntil >= end
}

fun TextFieldValue.toggleComposeMark(
    mark: ComposeMark,
    ranges: List<ComposeStyleRange>,
): Pair<TextFieldValue, List<ComposeStyleRange>> {
    val start = selection.min
    val end = selection.max
    if (start == end) return this to ranges
    val covered = composeSelectionHasMark(selection, ranges, mark)
    val next = if (covered) {
        ranges.flatMap { range ->
            if (range.mark != mark || range.end <= start || range.start >= end) {
                listOf(range)
            } else {
                listOfNotNull(
                    range.takeIf { it.start < start }?.copy(end = start),
                    range.takeIf { it.end > end }?.copy(start = end),
                )
            }
        }
    } else {
        ranges + ComposeStyleRange(start, end, mark)
    }
    return this to normalizeComposeStyleRanges(next, text.length)
}

data class ComposeLinePrefixEdit(
    val value: TextFieldValue,
    val active: Boolean,
)

fun TextFieldValue.toggleComposeLinePrefix(prefix: String): ComposeLinePrefixEdit {
    val cursor = selection.start.coerceIn(0, text.length)
    val lineStart = if (cursor == 0) 0 else {
        text.lastIndexOf('\n', cursor - 1).let { if (it < 0) 0 else it + 1 }
    }
    val hasPrefix = text.startsWith(prefix, lineStart)
    val nextText: String
    val nextCursor: Int
    if (hasPrefix) {
        nextText = text.removeRange(lineStart, lineStart + prefix.length)
        nextCursor = (cursor - prefix.length).coerceAtLeast(lineStart)
    } else {
        nextText = text.substring(0, lineStart) + prefix + text.substring(lineStart)
        nextCursor = cursor + prefix.length
    }
    return ComposeLinePrefixEdit(
        value = copy(text = nextText, selection = TextRange(nextCursor), composition = null),
        active = !hasPrefix,
    )
}

fun TextFieldValue.obfuscateComposeSelection(): TextFieldValue {
    val start = selection.min
    val end = selection.max
    if (start == end) return this
    var selected = text.substring(start, end)
    repeat(2) {
        selected = buildString(selected.length * 2) {
            selected.forEach { character ->
                append(character)
                if (character != ' ') {
                    append('\u200D')
                    if (Random.nextBoolean()) append('\u200D')
                }
            }
        }
    }
    val nextText = text.substring(0, start) + selected + text.substring(end)
    return copy(text = nextText, selection = TextRange(start + selected.length), composition = null)
}

data class ComposeSmartTextEdit(
    val value: TextFieldValue,
    val bulletsActive: Boolean,
    val quoteActive: Boolean,
)

/** Mirrors the RN editor's visible bullet/quote newline behavior. */
fun TextFieldValue.applyComposeSmartNewline(
    previousText: String,
    bulletsActive: Boolean,
    quoteActive: Boolean,
): ComposeSmartTextEdit {
    if (text.length != previousText.length + 1) {
        return ComposeSmartTextEdit(this, bulletsActive, quoteActive)
    }
    val insertion = commonPrefixLength(previousText, text)
    if (insertion >= text.length || text[insertion] != '\n') {
        return ComposeSmartTextEdit(this, bulletsActive, quoteActive)
    }

    val lineStart = previousText.lastIndexOf('\n', (insertion - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
    val previousLine = previousText.substring(lineStart, insertion)
    if (previousLine.trim() == "•") {
        val nextText = previousText.substring(0, lineStart) + "\n" + previousText.substring(insertion)
        return ComposeSmartTextEdit(
            copy(text = nextText, selection = TextRange(lineStart + 1), composition = null),
            bulletsActive = false,
            quoteActive = quoteActive,
        )
    }
    if (previousLine.trim() == "│") {
        val nextText = previousText.substring(0, lineStart) + "\n" + previousText.substring(insertion)
        return ComposeSmartTextEdit(
            copy(text = nextText, selection = TextRange(lineStart + 1), composition = null),
            bulletsActive = bulletsActive,
            quoteActive = false,
        )
    }

    val prefix = when {
        bulletsActive || previousLine.startsWith("• ") -> "• "
        quoteActive || previousLine.startsWith("│ ") -> "│ "
        else -> null
    } ?: return ComposeSmartTextEdit(this, bulletsActive, quoteActive)

    val nextText = text.substring(0, insertion + 1) + prefix + text.substring(insertion + 1)
    val cursor = (insertion + 1 + prefix.length).coerceIn(0, nextText.length)
    return ComposeSmartTextEdit(
        value = copy(text = nextText, selection = TextRange(cursor), composition = null),
        bulletsActive = prefix == "• " || bulletsActive,
        quoteActive = prefix == "│ " || quoteActive,
    )
}

/** Converts the clean visual editor representation into the Markdown sent by RN. */
fun composeBodyToMarkdown(text: String, ranges: List<ComposeStyleRange>): String {
    if (text.isEmpty()) return ""
    val normalizedRanges = normalizeComposeStyleRanges(ranges, text.length)
    var globalLineStart = 0
    return text.split('\n').joinToString("\n") { line ->
        var contentStart = 0
        val blockPrefix = buildString {
            if (line.startsWith("│ ") || line.startsWith("| ") || line.startsWith("> ")) {
                append("> ")
                contentStart += 2
            }
            val remainder = line.substring(contentStart)
            if (remainder.startsWith("• ") || remainder.startsWith("- ") || remainder.startsWith("* ")) {
                append("- ")
                contentStart += 2
            }
        }
        val content = line.substring(contentStart)
        val serialized = serializeComposeInline(
            text = content,
            globalStart = globalLineStart + contentStart,
            ranges = normalizedRanges,
        )
        globalLineStart += line.length + 1
        blockPrefix + serialized
    }
}

private fun serializeComposeInline(
    text: String,
    globalStart: Int,
    ranges: List<ComposeStyleRange>,
): String {
    if (text.isEmpty()) return ""
    val boundaries = sortedSetOf(0, text.length)
    ranges.forEach { range ->
        val start = (range.start - globalStart).coerceIn(0, text.length)
        val end = (range.end - globalStart).coerceIn(0, text.length)
        if (start < end) {
            boundaries += start
            boundaries += end
        }
    }

    data class StyledRun(val text: String, val bold: Boolean, val italic: Boolean)
    val runs = boundaries.zipWithNext().mapNotNull { (start, end) ->
        if (start >= end) return@mapNotNull null
        val absoluteStart = globalStart + start
        val absoluteEnd = globalStart + end
        StyledRun(
            text = text.substring(start, end),
            bold = ranges.any { it.mark == ComposeMark.Bold && it.start < absoluteEnd && it.end > absoluteStart },
            italic = ranges.any { it.mark == ComposeMark.Italic && it.start < absoluteEnd && it.end > absoluteStart },
        )
    }.fold(mutableListOf<StyledRun>()) { merged, run ->
        val previous = merged.lastOrNull()
        if (previous != null && previous.bold == run.bold && previous.italic == run.italic) {
            merged[merged.lastIndex] = previous.copy(text = previous.text + run.text)
        } else {
            merged += run
        }
        merged
    }

    return runs.joinToString("") { run ->
        val tag = when {
            run.bold && run.italic -> "***"
            run.bold -> "**"
            run.italic -> "*"
            else -> return@joinToString run.text
        }
        wrapComposeInline(run.text, tag)
    }
}

private fun wrapComposeInline(text: String, tag: String): String {
    val firstContent = text.indexOfFirst { !it.isWhitespace() }
    if (firstContent < 0) return text
    val lastContent = text.indexOfLast { !it.isWhitespace() }
    return text.substring(0, firstContent) +
        tag + text.substring(firstContent, lastContent + 1) + tag +
        text.substring(lastContent + 1)
}

/** Matches the newline encoding performed by the RN create-post mutation. */
fun formatComposeTextForApi(raw: String): String {
    if (raw.isEmpty()) return raw
    val filler = '\u3164'
    val placeholder = "___HANGUL_FILLER_GAP___"
    var text = raw.replace("\r\n", "\n").replace('\r', '\n')
    text = text.replace(Regex("(?:[ \\t]*\\n[ \\t]*){2,}"), "\n\n$filler\n\n")
    text = text.replace("\n\n$filler\n\n", placeholder)
    text = text.replace("\n", "\n\n")
    text = text.replace(placeholder, "\n\n$filler\n\n")
    return text.trim()
}

fun composeTopicSlug(name: String): String = name
    .removePrefix("$")
    .trim()
    .lowercase()
    .replace(Regex("[^a-z0-9_-]"), "-")
    .replace(Regex("-+"), "-")

private fun commonPrefixLength(a: String, b: String): Int {
    val max = minOf(a.length, b.length)
    var index = 0
    while (index < max && a[index] == b[index]) index++
    return index
}

private fun commonSuffixLength(a: String, b: String, prefixLength: Int): Int {
    val max = minOf(a.length, b.length) - prefixLength
    var index = 0
    while (index < max && a[a.length - 1 - index] == b[b.length - 1 - index]) index++
    return index
}

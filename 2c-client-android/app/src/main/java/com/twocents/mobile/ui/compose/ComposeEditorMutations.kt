package com.twocents.mobile.ui.compose

import androidx.compose.ui.text.input.TextFieldValue

/** Result of one editor mutation, including toolbar state derived from the caret. */
internal data class ComposeBodyMutation(
    val value: TextFieldValue,
    val ranges: List<ComposeStyleRange>,
    val bold: Boolean,
    val italic: Boolean,
    val bullets: Boolean,
    val quote: Boolean,
)

internal fun mutateComposeBody(
    current: TextFieldValue,
    incoming: TextFieldValue,
    ranges: List<ComposeStyleRange>,
    bold: Boolean,
    italic: Boolean,
    bullets: Boolean,
    quote: Boolean,
): ComposeBodyMutation {
    val smartEdit = incoming.applyComposeSmartNewline(current.text, bullets, quote)
    val next = smartEdit.value
    val nextRanges = remapComposeStyleRanges(
        oldText = current.text,
        newText = next.text,
        ranges = ranges,
        activeMarks = mutableSetOf<ComposeMark>().apply {
            if (bold) add(ComposeMark.Bold)
            if (italic) add(ComposeMark.Italic)
        },
    )
    var nextBold = bold
    var nextItalic = italic
    var nextBullets = smartEdit.bulletsActive
    var nextQuote = smartEdit.quoteActive
    if (current.text == next.text && current.selection != next.selection) {
        nextBold = composeSelectionHasMark(next.selection, nextRanges, ComposeMark.Bold)
        nextItalic = composeSelectionHasMark(next.selection, nextRanges, ComposeMark.Italic)
        val cursor = next.selection.min.coerceIn(0, next.text.length)
        val lineStart = next.text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val lineEnd = next.text.indexOf('\n', cursor).let { if (it < 0) next.text.length else it }
        val currentLine = next.text.substring(lineStart, lineEnd)
        nextBullets = currentLine.startsWith("• ")
        nextQuote = currentLine.startsWith("│ ")
    }
    return ComposeBodyMutation(next, nextRanges, nextBold, nextItalic, nextBullets, nextQuote)
}

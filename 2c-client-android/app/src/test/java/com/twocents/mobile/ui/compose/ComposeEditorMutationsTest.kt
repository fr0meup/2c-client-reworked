package com.twocents.mobile.ui.compose

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.*
import org.junit.Test

class ComposeEditorMutationsTest {
    @Test fun movingCaretToLeadingBlankLineDoesNotCrash() {
        val text = "\n" + "a".repeat(32)
        val current = TextFieldValue(text, TextRange(text.length))
        val result = mutateComposeBody(current, current.copy(selection = TextRange.Zero), emptyList(), false, false, false, false)
        assertEquals(text, result.value.text)
        assertFalse(result.bullets)
        assertFalse(result.quote)
    }

    @Test fun everyCaretPositionIncludingEmptyLinesIsSafe() {
        for (text in listOf("", "\n", "\n\ntext", "• first\n│ second\n")) {
            for (cursor in 0..text.length) {
                val current = TextFieldValue(text, TextRange(text.length))
                val result = mutateComposeBody(current, current.copy(selection = TextRange(cursor)), emptyList(), false, false, false, false)
                assertEquals(text, result.value.text)
            }
        }
    }

    @Test fun leadingNewlineInsertionIsSafe() {
        val result = TextFieldValue("\nabc", TextRange(1)).applyComposeSmartNewline("abc", false, false)
        assertEquals("\nabc", result.value.text)
    }
}

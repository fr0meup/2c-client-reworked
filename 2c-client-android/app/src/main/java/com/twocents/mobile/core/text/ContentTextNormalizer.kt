package com.twocents.mobile.core.text

internal val InvisibleTextControls = Regex("[\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u206F\\uFEFF\\u00AD\\u061C\\u180E\\u034F]")
internal val VisibleBlankSubstitutes = Regex("[\\u00A0\\u2800\\u3000\\u3164]")
private val RepeatedHorizontalWhitespace = Regex("[\\p{Zs}\\t]{2,}")

/** Keeps one visual blank row between paragraphs while removing blank-glyph bypasses. */
internal fun normalizeParagraphSpacing(text: String): String = text
    .replace("\r\n", "\n")
    .replace('\r', '\n')
    .replace(VisibleBlankSubstitutes, " ")
    .replace(InvisibleTextControls, "")
    .replace(Regex("\\n(?:[ \\t]*\\n){2,}"), "\n\n")
    .trim()

/**
 * Preserves authored line breaks while preventing long runs of spaces from
 * manufacturing empty horizontal layout inside profile cards.
 */
internal fun normalizeBioWhitespace(text: String): String = text
    .replace("\r\n", "\n")
    .replace('\r', '\n')
    .replace(VisibleBlankSubstitutes, " ")
    .replace(InvisibleTextControls, "")
    .replace(RepeatedHorizontalWhitespace, " ")
    .trim()

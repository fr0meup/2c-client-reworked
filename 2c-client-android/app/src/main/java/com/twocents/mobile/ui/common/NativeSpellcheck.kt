package com.twocents.mobile.ui.common

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SpellCheckerSession
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import android.view.textservice.TextServicesManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextLayoutResult
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

/**
 * Uses Android's configured spell-check service instead of shipping a dictionary.
 * Compose text fields do not consume TextView's SuggestionSpans, so the returned
 * native typo ranges are drawn over the existing editor without replacing it.
 */
@Composable
internal fun rememberNativeMisspellings(text: String): List<IntRange> {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentText by rememberUpdatedState(text)
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var session by remember { mutableStateOf<SpellCheckerSession?>(null) }
    var requestSequence by remember { mutableIntStateOf(0) }
    var misspellings by remember { mutableStateOf(emptyList<IntRange>()) }

    DisposableEffect(context) {
        val manager = context.getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE) as? TextServicesManager
        val listener = object : SpellCheckerSession.SpellCheckerSessionListener {
            override fun onGetSuggestions(results: Array<out SuggestionsInfo>?) = Unit

            override fun onGetSentenceSuggestions(results: Array<out SentenceSuggestionsInfo>?) {
                val sentence = results?.firstOrNull()
                val responseSequence = sentence
                    ?.takeIf { it.suggestionsCount > 0 }
                    ?.getSuggestionsInfoAt(0)
                    ?.sequence
                val ranges = buildList {
                    if (sentence != null) {
                        repeat(sentence.suggestionsCount) { index ->
                            val info = sentence.getSuggestionsInfoAt(index)
                            val isTypo = info.suggestionsAttributes and SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO != 0
                            val start = sentence.getOffsetAt(index)
                            val length = sentence.getLengthAt(index)
                            if (isTypo && start >= 0 && length > 0) add(start until (start + length))
                        }
                    }
                }
                mainHandler.post {
                    // Suggestions carry the TextInfo sequence when at least one
                    // word is returned; reject stale responses after fast typing.
                    if (responseSequence == null || responseSequence == requestSequence) {
                        misspellings = ranges.filter { it.first < currentText.length }
                    }
                }
            }
        }
        val created = if (manager?.isSpellCheckerEnabled == true) {
            manager.newSpellCheckerSession(null, null, listener, true)
        } else null
        session = created
        onDispose {
            session = null
            created?.close()
        }
    }

    LaunchedEffect(text, session) {
        if (text.isBlank()) {
            misspellings = emptyList()
            return@LaunchedEffect
        }
        delay(320)
        val checker = session ?: return@LaunchedEffect
        requestSequence += 1
        checker.getSentenceSuggestions(arrayOf(TextInfo(text, 0, requestSequence)), 5)
    }
    return misspellings
}

/** Draws compact native-style red waves under the ranges reported by Android. */
internal fun Modifier.nativeMisspellingUnderlines(
    layout: TextLayoutResult?,
    ranges: List<IntRange>,
): Modifier = drawWithContent {
    drawContent()
    val result = layout ?: return@drawWithContent
    val text = result.layoutInput.text.text
    if (text.isEmpty()) return@drawWithContent
    val stroke = 1.15f * density
    val halfWave = 1.7f * density
    val amplitude = 1.05f * density

    ranges.forEach { sourceRange ->
        var start = sourceRange.first.coerceIn(0, text.length)
        val endExclusive = (sourceRange.last + 1).coerceIn(start, text.length)
        while (start < endExclusive) {
            val line = result.getLineForOffset(start.coerceAtMost(text.lastIndex))
            var segmentEnd = min(endExclusive, result.getLineEnd(line, visibleEnd = true))
            while (start < segmentEnd && text[start].isWhitespace()) start += 1
            while (segmentEnd > start && text[segmentEnd - 1].isWhitespace()) segmentEnd -= 1
            if (start < segmentEnd) {
                val first = result.getBoundingBox(start)
                val last = result.getBoundingBox(segmentEnd - 1)
                val left = min(first.left, last.left)
                val right = max(first.right, last.right)
                val baseline = max(first.bottom, last.bottom) - (0.7f * density)
                val wave = Path().apply {
                    moveTo(left, baseline)
                    var x = left
                    var downward = true
                    while (x < right) {
                        x = min(x + halfWave, right)
                        lineTo(x, baseline + if (downward) amplitude else -amplitude)
                        downward = !downward
                    }
                }
                drawPath(wave, color = Color(0xFFE45767), style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke))
            }
            start = max(segmentEnd, result.getLineEnd(line)).coerceAtMost(endExclusive)
            if (start < endExclusive && text[start] == '\n') start += 1
        }
    }
}

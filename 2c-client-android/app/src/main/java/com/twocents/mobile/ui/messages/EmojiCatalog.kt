package com.twocents.mobile.ui.messages

import android.content.Context
import com.twocents.mobile.kotlin.R

/** The full Unicode Emoji 17 keyboard set bundled with the app. */
internal object EmojiCatalog {
    @Volatile private var cached: List<String>? = null
    @Volatile private var cachedCategories: List<EmojiCategory>? = null

    fun all(context: Context): List<String> = cached ?: synchronized(this) {
        cached ?: context.resources.openRawResource(R.raw.emoji_test).bufferedReader().useLines { lines ->
            lines.mapNotNull { raw ->
                val data = raw.substringBefore('#').trim()
                if (!data.endsWith("; fully-qualified")) return@mapNotNull null
                val codePoints = data.substringBefore(';').trim().split(Regex("\\s+"))
                runCatching {
                    buildString { codePoints.forEach { appendCodePoint(it.toInt(16)) } }
                }.getOrNull()
            }.distinct().toList()
        }.also { cached = it }
    }

    fun categories(context: Context): List<EmojiCategory> = cachedCategories ?: synchronized(this) {
        cachedCategories ?: run {
            val grouped = linkedMapOf<String, MutableList<String>>()
            var group = "Smileys & Emotion"
            context.resources.openRawResource(R.raw.emoji_test).bufferedReader().useLines { lines ->
                lines.forEach { raw ->
                    if (raw.startsWith("# group:")) {
                        group = raw.substringAfter(':').trim()
                        return@forEach
                    }
                    val data = raw.substringBefore('#').trim()
                    if (!data.endsWith("; fully-qualified")) return@forEach
                    val emoji = runCatching {
                        buildString {
                            data.substringBefore(';').trim().split(Regex("\\s+")).forEach { appendCodePoint(it.toInt(16)) }
                        }
                    }.getOrNull() ?: return@forEach
                    grouped.getOrPut(group) { mutableListOf() }.add(emoji)
                }
            }
            grouped.map { (name, values) -> EmojiCategory(name, values.distinct()) }
        }.also { cachedCategories = it }
    }
}

internal data class EmojiCategory(val name: String, val emojis: List<String>) {
    val icon: String get() = when (name) {
        "Smileys & Emotion" -> "😀"
        "People & Body" -> "👋"
        "Animals & Nature" -> "🐻"
        "Food & Drink" -> "🍕"
        "Travel & Places" -> "✈️"
        "Activities" -> "⚽"
        "Objects" -> "💡"
        "Symbols" -> "❤️"
        "Flags" -> "🏁"
        else -> "•"
    }
}

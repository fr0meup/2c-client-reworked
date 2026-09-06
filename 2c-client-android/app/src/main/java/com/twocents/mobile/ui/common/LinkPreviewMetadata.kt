package com.twocents.mobile.ui.common

import android.text.Html
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

private val Tags = Regex("""<(meta|link|base)\b(?:[^>"']|"[^"]*"|'[^']*')*>""", RegexOption.IGNORE_CASE)
private val Attributes = Regex("""([\w:-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""")
private fun attributes(tag: String) = Attributes.findAll(tag).associate {
    it.groupValues[1].lowercase() to it.groupValues.drop(2).firstOrNull(String::isNotEmpty).orEmpty()
}
private fun plain(value: String) = Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString()
    .replace(Regex("\\s+"), " ").trim()
private fun first(vararg values: String?) = values.firstOrNull { !it.isNullOrBlank() }.orEmpty()

/** Read social tags, schema.org and older publishers without executing page scripts. */
internal fun parseLinkPreview(html: String, base: String): LinkPreview {
    val tags = mutableMapOf<String, String>()
    var imageLink: String? = null
    var documentBase = base
    Tags.findAll(html).forEach { match ->
        val attrs = attributes(match.value)
        when (match.groupValues[1].lowercase()) {
            "meta" -> {
                val key = attrs["property"] ?: attrs["name"] ?: attrs["itemprop"]
                val content = attrs["content"]?.takeIf(String::isNotBlank)
                if (key != null && content != null) tags.putIfAbsent(key.lowercase(), content)
            }
            "link" -> if (attrs["rel"]?.lowercase()?.split(' ')?.contains("image_src") == true) imageLink = attrs["href"]
            "base" -> base.toHttpUrlOrNull()?.resolve(attrs["href"].orEmpty())?.let { documentBase = it.toString() }
        }
    }
    val structured = mutableListOf<JSONObject>()
    fun collect(value: Any?, depth: Int = 0) {
        if (depth > 6 || structured.size >= 64) return
        when (value) {
            is JSONArray -> for (i in 0 until minOf(value.length(), 64)) collect(value.opt(i), depth + 1)
            is JSONObject -> {
                structured += value
                collect(value.opt("@graph"), depth + 1)
                collect(value.opt("mainEntity"), depth + 1)
            }
        }
    }
    Regex("""<script\b([^>]*)>([\s\S]*?)</script>""", RegexOption.IGNORE_CASE).findAll(html).forEach {
        if (attributes(it.groupValues[1])["type"]?.equals("application/ld+json", true) == true) {
            runCatching { collect(JSONTokener(it.groupValues[2]).nextValue()) }
        }
    }
    // Prefer content entities over site-wide Organization/Breadcrumb records.
    val entity = structured.firstOrNull {
        val type = it.optString("@type")
        listOf("Article", "Product", "VideoObject", "Recipe", "NewsArticle", "BlogPosting", "WebPage")
            .any { candidate -> type.contains(candidate, true) }
    } ?: structured.firstOrNull { it.has("headline") }

    fun imageValue(value: Any?): String? = when (value) {
        is String -> value.takeIf(String::isNotBlank)
        is JSONObject -> first(value.optString("contentUrl"), value.optString("url")).takeIf(String::isNotBlank)
        is JSONArray -> (0 until minOf(value.length(), 8)).firstNotNullOfOrNull { imageValue(value.opt(it)) }
        else -> null
    }
    val title = first(tags["og:title"], tags["twitter:title"], tags["headline"], tags["name"],
        entity?.optString("headline"), entity?.optString("name"),
        Regex("""<title[^>]*>([\s\S]*?)</title>""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1),
        Regex("""<h1[^>]*>([\s\S]*?)</h1>""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1))
    val description = first(tags["og:description"], tags["twitter:description"], tags["description"],
        entity?.optString("description"))
    val rawImage = first(tags["og:image:secure_url"], tags["og:image"], tags["og:image:url"],
        tags["twitter:image"], tags["twitter:image:src"], tags["image"], tags["thumbnailurl"],
        imageValue(entity?.opt("image")), imageValue(entity?.opt("thumbnailUrl")), imageLink)
    val image = documentBase.toHttpUrlOrNull()?.resolve(plain(rawImage))?.takeIf {
        rawImage.isNotBlank() && it.isHttps && it.username.isEmpty() && it.password.isEmpty()
    }?.toString()
    return LinkPreview(plain(title).take(300), plain(description).take(500), image)
}

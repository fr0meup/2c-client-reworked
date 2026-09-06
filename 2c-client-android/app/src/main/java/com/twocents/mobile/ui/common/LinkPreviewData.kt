package com.twocents.mobile.ui.common

import android.content.Context
import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

internal data class LinkPreview(val title: String, val description: String, val image: String?)

private val WebLinks = Regex("""(?:https?://|www\.)[^\s<>"\[\]()]+""", RegexOption.IGNORE_CASE)
private val MarkdownLinks = Regex("""\[[^\]]*]\(\s*((?:https?://|www\.)[^\s)]+)\s*\)""", RegexOption.IGNORE_CASE)

/** Strip the whole markdown token, rather than leaving its label and punctuation behind. */
internal fun withoutPreviewLinks(text: String): String {
    val markdownRemoved = MarkdownLinks.replace(text) { match ->
        if (previewLinks(match.groupValues[1]).isNotEmpty()) "" else match.value
    }
    return WebLinks.replace(markdownRemoved) { match ->
        if (previewLinks(match.value).isNotEmpty()) "" else match.value
    }.replace(Regex("\\n[ \\t]*\\n(?:[ \\t]*\\n)+"), "\n\n").trim()
}
private val MediaSuffix = Regex("""\.(?:png|jpe?g|gifv?|webp|avif|apng|bmp|svg|heic|heif|tiff?|ico|mp4|webm|mov|m4v|m3u8|mp3|wav|ogg)$""", RegexOption.IGNORE_CASE)

/** Retain ordinary destinations, deduplicate markdown URLs, and leave native embeds alone. */
internal fun previewLinks(text: String, attached: String? = null): List<String> =
    (listOfNotNull(attached).flatMap { WebLinks.findAll(it).map { match -> match.value }.toList() } +
        WebLinks.findAll(MarkdownLinks.replace(text) { it.groupValues[1] }).map { it.value }.toList())
        .map { it.trimEnd('.', ',', '!', '?', ')', ';').replace("&amp;", "&") }
        .map { if (it.startsWith("www.", true)) "https://$it" else it }
        .mapNotNull { it.toHttpUrlOrNull() }
        .filter { url ->
            url.username.isEmpty() && url.password.isEmpty() &&
                !MediaSuffix.containsMatchIn(url.encodedPath) &&
                !(url.host.removePrefix("www.") in setOf("x.com", "twitter.com", "mobile.twitter.com") && url.encodedPath.contains("/status/"))
        }.groupBy { url ->
            val host = url.host.removePrefix("www.")
            val twocents = host in setOf("twocents.com", "twocents.money")
            // Keep path case and meaningful query parameters for unrelated websites.
            val query = if (twocents) emptyList() else url.queryParameterNames
                .filterNot { it.startsWith("utm_", true) || it in setOf("fbclid", "gclid") }
                .sorted().map { it to url.queryParameterValues(it) }
            Triple(if (twocents) "twocents" else host, url.pathSegments.joinToString("/").trimEnd('/'), query)
        }.values.map { variants ->
            // Prefer the supplied .com destination, preserving first-card ordering.
            (variants.firstOrNull { it.host.removePrefix("www.") == "twocents.com" } ?: variants.first()).toString()
        }

/**
 * Fetch only bounded HTML metadata, never a WebView or a third-party preview service.
 * Disk caching also remembers failed previews to prevent repeated scrolling retries.
 */
internal object LinkPreviewRepository {
    private val slots = Semaphore(2)
    private val imageSlots = Semaphore(2)
    private val locks = Array(32) { Mutex() }
    private val client = OkHttpClient.Builder()
        .callTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .dns(object : Dns {
            override fun lookup(hostname: String): List<java.net.InetAddress> = Dns.SYSTEM.lookup(hostname).also { addresses ->
                require(addresses.isNotEmpty() && addresses.none {
                    it.isAnyLocalAddress || it.isLoopbackAddress || it.isLinkLocalAddress ||
                        it.isSiteLocalAddress || it.isMulticastAddress
                }) { "Preview requires a public website" }
            }
        }).build()

    suspend fun get(context: Context, url: String, onMetadata: suspend (LinkPreview) -> Unit = {}): LinkPreview = withContext(Dispatchers.IO) {
        locks[(url.hashCode() and Int.MAX_VALUE) % locks.size].withLock {
            val directory = File(context.cacheDir, "link-previews").apply { mkdirs() }
            val key = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
            val file = File(directory, "$key.json")
            val cached = runCatching { JSONObject(file.readText()) }.getOrNull()
            if (cached != null && cached.optInt("parser") == 2 && cached.optLong("expires") > System.currentTimeMillis()) {
                return@withLock LinkPreview(cached.optString("title"), cached.optString("description"),
                    cached.optString("image").takeIf(String::isNotBlank))
            }
            val result = slots.withPermit {
                runCatching {
                    client.newCall(Request.Builder().url(url)
                        .header("User-Agent", "2c-LinkPreview/1.0")
                        .header("Accept", "text/html,application/xhtml+xml;q=0.9")
                        .build())
                        .execute().use { response ->
                            require(response.isSuccessful)
                            val body = requireNotNull(response.body)
                            val type = body.contentType()
                            require(type == null || type.type == "text" || type.subtype.contains("html"))
                            // Read beyond the head only when metadata is incomplete:
                            // JSON-LD often lives in the body. Keep a hard transfer cap.
                            val source = body.source()
                            val buffer = okio.Buffer()
                            var inspectedHead = false
                            while (buffer.size < 256 * 1024) {
                                if (source.read(buffer, minOf(8192L, 256 * 1024L - buffer.size)) == -1L) break
                                if (!inspectedHead) {
                                    val head = buffer.clone().readUtf8()
                                    if (head.contains("</head>", true)) {
                                        inspectedHead = true
                                        val metadata = parseLinkPreview(head, response.request.url.toString())
                                        if (metadata.title.isNotBlank() && metadata.image != null) break
                                    }
                                }
                            }
                            parseLinkPreview(buffer.readString(body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8),
                                response.request.url.toString())
                        }
                }.getOrNull()
            }
            // Preview pictures are also bounded and cached locally. A massive social
            // image must not turn a small text card into a multi-megabyte download.
            // Text becomes visible as soon as HTML is parsed; a slow image host
            // no longer holds back the entire card or other sites' metadata.
            result?.let { onMetadata(it.copy(image = null)) }
            val imageFile = File(directory, "$key.image")
            val image = result?.image?.let { imageUrl ->
                imageSlots.withPermit {
                    runCatching {
                        client.newCall(Request.Builder().url(imageUrl).build()).execute().use { response ->
                            require(response.isSuccessful)
                            val body = requireNotNull(response.body)
                            require(body.contentType()?.type == "image")
                            val limit = 512 * 1024L
                            require(body.contentLength() <= limit)
                            val bytes = okio.Buffer()
                            val source = body.source()
                            while (bytes.size <= limit) {
                                if (source.read(bytes, minOf(8192L, limit + 1 - bytes.size)) == -1L) break
                            }
                            require(bytes.size <= limit)
                            imageFile.outputStream().use { bytes.copyTo(it) }
                            imageFile.absolutePath
                        }
                    }.getOrNull()
                }
            }
            val preview = result?.copy(image = image) ?: LinkPreview("", "", null)
            val ttl = if (result == null) TimeUnit.HOURS.toMillis(1) else TimeUnit.DAYS.toMillis(7)
            runCatching {
                file.writeText(JSONObject().put("parser", 2).put("title", preview.title).put("description", preview.description)
                    .put("image", preview.image.orEmpty()).put("expires", System.currentTimeMillis() + ttl).toString())
                var retained = 0L
                directory.listFiles()?.sortedByDescending { it.lastModified() }?.forEachIndexed { index, cachedFile ->
                    retained += cachedFile.length()
                    if (index >= 1024 || retained > 64 * 1024 * 1024L) cachedFile.delete()
                }
            }
            preview
        }
    }
}

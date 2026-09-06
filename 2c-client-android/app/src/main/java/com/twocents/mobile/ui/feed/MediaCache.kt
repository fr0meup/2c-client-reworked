package com.twocents.mobile.ui.feed

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaDataSource
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

internal val MediaRatios: MutableMap<String, Float> = object : LinkedHashMap<String, Float>(256, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Float>?) = size > 512
}

internal fun cachedMediaRatio(uri: String): Float? = synchronized(MediaRatios) { MediaRatios[uri] }

internal fun cacheMediaRatio(uri: String, ratio: Float) {
    synchronized(MediaRatios) { MediaRatios[uri] = ratio }
}

internal data class CachedVideoPreview(val file: File, val ratio: Float?)

/** Stores only a bounded set of first frames and dimensions; video streams are never persisted. */
internal object VideoPreviewRepository {
    private val memory = ConcurrentHashMap<String, CachedVideoPreview>()
    private val locks = ConcurrentHashMap<String, Mutex>()
    // A failed probe must not spend its transfer budget again on every row remount.
    private val retryAfter = ConcurrentHashMap<String, Long>()

    suspend fun prepare(context: Context, uri: String): CachedVideoPreview? = withContext(Dispatchers.IO) {
        memory[uri]?.let { return@withContext it }
        if ((retryAfter[uri] ?: 0L) > System.currentTimeMillis()) return@withContext null
        val lock = locks.getOrPut(uri) { Mutex() }
        try {
            lock.withLock {
                memory[uri]?.let { return@withLock it }
                if ((retryAfter[uri] ?: 0L) > System.currentTimeMillis()) return@withLock null
                val directory = File(context.cacheDir, "video-previews").apply { mkdirs() }
                val key = MessageDigest.getInstance("SHA-256").digest(uri.toByteArray()).joinToString("") { "%02x".format(it) }
                val image = File(directory, "$key.jpg")
                val ratioFile = File(directory, "$key.ratio")
                if (image.isFile) {
                    val cached = CachedVideoPreview(image, ratioFile.takeIf(File::isFile)?.readText()?.toFloatOrNull())
                    cached.ratio?.let { cacheMediaRatio(uri, it) }
                    memory[uri] = cached
                    return@withLock cached
                }
                runCatching {
                    val retriever = MediaMetadataRetriever()
                    try {
                        val parsed = Uri.parse(uri)
                        if (parsed.scheme in setOf("content", "file")) {
                            retriever.setDataSource(context, parsed)
                        } else {
                            // Android's URL retriever may transfer most or all of a
                            // remote video just to obtain frame zero. A bounded,
                            // range-backed source keeps preview generation cheap
                            // while returning the exact same full-quality frame.
                            retriever.setDataSource(HttpRangeMediaDataSource(uri))
                        }
                        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull()
                        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull()
                        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                        val rawRatio = if (width != null && height != null && height > 0f) width / height else null
                        val ratio = rawRatio?.let { if (rotation == 90 || rotation == 270) 1f / it else it }
                        val frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return@runCatching null
                        image.outputStream().buffered().use { frame.compress(Bitmap.CompressFormat.JPEG, 86, it) }
                        ratio?.let { ratioFile.writeText(it.toString()); cacheMediaRatio(uri, it) }
                        CachedVideoPreview(image, ratio)
                    } finally {
                        retriever.release()
                    }
                }.getOrNull().also { result ->
                    if (result == null) retryAfter[uri] = System.currentTimeMillis() + 15 * 60 * 1000L
                }?.also { preview ->
                    memory[uri] = preview
                    directory.listFiles()?.sortedByDescending(File::lastModified)?.drop(96)?.forEach { stale ->
                        if (stale.extension == "jpg") {
                            stale.delete()
                            File(directory, "${stale.nameWithoutExtension}.ratio").delete()
                        }
                    }
                }
            }
        } finally {
            locks.remove(uri, lock)
        }
    }
}

private class HttpRangeMediaDataSource(private val uri: String) : MediaDataSource() {
    companion object {
        // Metadata probes often request tiny ranges at opposite ends of an MP4.
        private const val ChunkBytes = 64 * 1024
        private const val TransferBudgetBytes = 4 * 1024 * 1024
        private val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val chunks = LinkedHashMap<Long, ByteArray>(8, .75f, true)
    private var knownSize = -1L
    private var transferred = 0
    private var closed = false

    override fun getSize(): Long {
        if (knownSize < 0L) loadChunk(0L)
        return knownSize
    }

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (closed) throw IOException("Video preview source is closed")
        if (position < 0L || size <= 0) return if (size <= 0) 0 else -1
        if (knownSize >= 0L && position >= knownSize) return -1
        var cursor = position
        var destination = offset
        var remaining = size
        var copied = 0
        while (remaining > 0) {
            val chunkStart = (cursor / ChunkBytes) * ChunkBytes
            val chunk = chunks[chunkStart] ?: loadChunk(chunkStart)
            val inChunk = (cursor - chunkStart).toInt()
            if (inChunk >= chunk.size) break
            val count = minOf(remaining, chunk.size - inChunk)
            chunk.copyInto(buffer, destination, inChunk, inChunk + count)
            cursor += count
            destination += count
            remaining -= count
            copied += count
            if (chunk.size < ChunkBytes) break
        }
        return if (copied == 0) -1 else copied
    }

    private fun loadChunk(start: Long): ByteArray {
        if (transferred >= TransferBudgetBytes) throw IOException("Video preview transfer budget reached")
        val requested = minOf(ChunkBytes, TransferBudgetBytes - transferred)
        val end = start + requested - 1L
        val request = Request.Builder().url(uri).header("Range", "bytes=$start-$end").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Video preview request failed (${response.code})")
            if (start > 0L && response.code != 206) throw IOException("Video server does not support byte ranges")
            response.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()?.let { knownSize = it }
            if (knownSize < 0L && response.code == 200) knownSize = response.body?.contentLength() ?: -1L
            val source = response.body?.source() ?: throw IOException("Empty video preview response")
            val bytes = ByteArray(requested)
            var count = 0
            while (count < requested) {
                val read = source.read(bytes, count, requested - count)
                if (read < 0) break
                count += read
            }
            val result = if (count == bytes.size) bytes else bytes.copyOf(count)
            transferred += result.size
            chunks[start] = result
            while (chunks.size > TransferBudgetBytes / ChunkBytes) chunks.remove(chunks.entries.first().key)
            return result
        }
    }

    override fun close() {
        closed = true
        chunks.clear()
    }
}

internal class SharedVideoPlayback {
    var owner by mutableStateOf<Any?>(null)
    var positionMs by mutableLongStateOf(0L)
    var resumePlaying by mutableStateOf(false)
}

internal object VideoPlaybackHandoff {
    private val entries = object : LinkedHashMap<String, SharedVideoPlayback>(24, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SharedVideoPlayback>?) = size > 48
    }
    var detailOverlayActive by mutableStateOf(false)

    fun state(uri: String): SharedVideoPlayback = synchronized(entries) {
        entries.getOrPut(uri) { SharedVideoPlayback() }
    }
}

internal fun setPostDetailVideoOverlayActive(active: Boolean) {
    VideoPlaybackHandoff.detailOverlayActive = active
}

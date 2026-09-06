package com.twocents.mobile.ui.compose

import android.content.Context
import coil3.imageLoader
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request

/** Durable originals live outside the evictable image cache; URLs remain the portable identity. */
internal object SavedGifFiles {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Mutex()
    private val client = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build()
    private fun directory(context: Context) = File(context.filesDir, "saved-gif-media").apply { mkdirs() }
    private fun file(context: Context, url: String): File {
        val key = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(directory(context), key)
    }
    fun model(context: Context, url: String): Any = file(context, url).takeIf { it.isFile && it.length() > 0 } ?: url

    fun sync(context: Context) {
        val app = context.applicationContext
        scope.launch {
            gate.withLock {
                val urls = (GifLibrary.saved(app) + GifLibrary.favorites(app)).distinct()
                val names = urls.map { file(app, it).name }.toSet()
                directory(app).listFiles()?.filter { it.name !in names }?.forEach { it.delete() }
                if (!com.twocents.mobile.ui.settings.InteractionPreferences.automaticMediaAllowed(app)) return@withLock
                for (url in urls) {
                    val target = file(app, url)
                    if (target.isFile) continue
                    val partial = File(target.path + ".part")
                    try {
                        val cached = app.imageLoader.diskCache?.openSnapshot(url)
                        if (cached != null) cached.use { it.data.toFile().copyTo(partial, overwrite = true) }
                        else client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                            check(response.isSuccessful)
                            val body = requireNotNull(response.body)
                            check(body.contentType()?.type == "image")
                            body.byteStream().use { input -> partial.outputStream().use { input.copyTo(it) } }
                        }
                        // Removal during a download must not resurrect a deleted GIF.
                        if (url in GifLibrary.saved(app) || url in GifLibrary.favorites(app)) {
                            check(partial.renameTo(target))
                        }
                    } catch (_: Exception) {
                        // Keep the URL so a future open can recover missing originals.
                    } finally { partial.delete() }
                }
            }
        }
    }
}

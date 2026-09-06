package com.twocents.mobile.updates

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

internal data class AvailableUpdate(val version: String, val download: String, val notes: String)

/** Public release metadata only: no account credentials or user identifiers are sent. */
internal object AppUpdates {
    private val gate = Mutex()
    private val mutableUpdate = MutableStateFlow<AvailableUpdate?>(null)
    val available = mutableUpdate.asStateFlow()
    private val client = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build()

    fun dismiss() { mutableUpdate.value = null }
    @Volatile private var returningFromDownload = false
    fun downloadOpened() { returningFromDownload = true; dismiss() }

    suspend fun check(context: Context, manual: Boolean = false): String? = withContext(Dispatchers.IO) {
        if (!gate.tryLock()) return@withContext if (manual) "An update check is already running" else null
        try {
            val suppressPrompt = !manual && returningFromDownload
            returningFromDownload = false
            // MainActivity requests fresh release metadata on every foreground entry.
            val response = client.newCall(Request.Builder()
                .url("https://api.github.com/repos/fr0meup/2c-client-reworked/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "2c-android-update-check")
                .build()).execute()
            response.use {
                check(it.isSuccessful) { "Couldn't check for updates. Try again later." }
                val source = requireNotNull(it.body).source()
                val bytes = okio.Buffer()
                while (bytes.size <= 256 * 1024L) {
                    if (source.read(bytes, minOf(8192L, 256 * 1024L + 1 - bytes.size)) == -1L) break
                }
                check(bytes.size <= 256 * 1024L)
                val release = JSONObject(bytes.readUtf8())
                check(!release.optBoolean("draft") && !release.optBoolean("prerelease"))
                val version = release.optString("tag_name").removePrefix("v")
                val installed = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
                check(versionParts(version) != null && versionParts(installed) != null)
                if (!isNewerRelease(version, installed)) {
                    mutableUpdate.value = null
                    return@withContext if (manual) "You're up to date ($installed)" else null
                }
                val assets = release.optJSONArray("assets")
                val asset = (0 until (assets?.length() ?: 0)).mapNotNull { assets?.optJSONObject(it) }
                    .firstOrNull { it.optString("name") == "2c-client-android.apk" && it.optString("state") == "uploaded" }
                // Don't prompt until the release APK has finished uploading.
                check(asset != null) { "The update is still being published. Try again later." }
                val download = asset.optString("browser_download_url")
                check(download.startsWith("https://github.com/fr0meup/2c-client-reworked/releases/download/"))
                if (!suppressPrompt) mutableUpdate.value = AvailableUpdate(version, download, updateHighlights(release.optString("body")))
                null
            }
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            if (manual) "Couldn't check for updates. Check your connection and try again." else null
        } finally { gate.unlock() }
    }
}

private fun versionParts(value: String): List<Long>? =
    if (value.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) value.split('.').mapNotNull(String::toLongOrNull).takeIf { it.size == 3 } else null

/** Compare numbers, so 0.1.10 correctly sorts after 0.1.9. */
internal fun isNewerRelease(candidate: String, installed: String): Boolean {
    val next = versionParts(candidate) ?: return false
    val current = versionParts(installed) ?: return false
    for (index in next.indices) {
        if (next[index] != current[index]) return next[index] > current[index]
    }
    return false
}

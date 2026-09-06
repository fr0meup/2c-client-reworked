package com.twocents.mobile.ui.settings

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.twocents.mobile.notifications.NotificationHistoryStore
import com.twocents.mobile.notifications.NotificationPreferences
import com.twocents.mobile.ui.compose.GifLibrary
import com.twocents.mobile.ui.feed.AdvancedSearchIndex
import com.twocents.mobile.ui.profile.FollowersScanStore
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject

internal data class LocalDataExportState(
    val offline: Boolean,
    val verifiedOnly: Boolean,
    val autoLikeOwnContent: Boolean,
    val autoPlayVideos: Boolean,
    val wifiOnlyMedia: Boolean,
    val haptics: Boolean,
    val muted: Set<String>,
)

internal data class ImportedLocalData(
    val offline: Boolean = false,
    val verifiedOnly: Boolean = false,
    val autoLikeOwnContent: Boolean = true,
    val autoPlayVideos: Boolean = false,
    val wifiOnlyMedia: Boolean = false,
    val haptics: Boolean = true,
    val muted: List<String> = emptyList(),
    val notificationPreferences: String? = null,
    val activityStats: String? = null,
    val followersScan: String? = null,
    val gifLibrary: JSONObject? = null,
    val drafts: String? = null,
)

private data class DraftMediaEntry(val archivePath: String, val source: Uri)

/**
 * Writes a portable archive rather than embedding binary media in JSON. Search rows
 * are streamed from SQLite and draft media is copied as raw bytes, keeping memory use
 * flat even when either part of the backup is large.
 */
internal fun exportLocalData(
    context: Context,
    destination: Uri,
    userUuid: String,
    state: LocalDataExportState,
) {
    val (drafts, draftMedia) = prepareDraftArchive(context)
    val descriptor = context.contentResolver.openFileDescriptor(destination, "rwt")
        ?: error("Couldn't open the selected file")
    descriptor.use { parcel ->
        ZipOutputStream(BufferedOutputStream(FileOutputStream(parcel.fileDescriptor), 64 * 1024)).use { zip ->
            // Media is already compressed by its codec; storing it first with no
            // deflate work keeps video-heavy backups quick and lets import stage all
            // attachments before it mutates the local data stores.
            zip.setLevel(java.util.zip.Deflater.NO_COMPRESSION)
            draftMedia.forEach { media ->
                zip.putNextEntry(ZipEntry(media.archivePath))
                context.contentResolver.openInputStream(media.source)?.use { it.copyTo(zip, 64 * 1024) }
                    ?: error("A draft attachment could not be read")
                zip.closeEntry()
            }

            zip.setLevel(java.util.zip.Deflater.BEST_SPEED)
            zip.putNextEntry(ZipEntry(BACKUP_MANIFEST))
            val writer = JsonWriter(OutputStreamWriter(zip, Charsets.UTF_8))
            writer.beginObject()
            writer.name("version").value(3L)
            writer.name("offline").value(state.offline)
            writer.name("verified_only").value(state.verifiedOnly)
            writer.name("auto_like_own_content").value(state.autoLikeOwnContent)
            writer.name("auto_play_videos").value(state.autoPlayVideos)
            writer.name("wifi_only_media").value(state.wifiOnlyMedia)
            writer.name("haptic_feedback").value(state.haptics)
            writer.name("notification_preferences").value(NotificationPreferences.exportJson(context).toString())
            writer.name("muted").beginArray(); state.muted.forEach(writer::value); writer.endArray()
            writer.name("search_posts"); AdvancedSearchIndex.writeExport(writer)
            writer.name("activity_stats").value(NotificationHistoryStore(context, userUuid).exportJson().toString())
            writer.name("followers_scan").value(FollowersScanStore(context, userUuid).exportJson().toString())
            writer.name("gif_library").beginObject()
            writer.name("saved").beginArray(); GifLibrary.saved(context).take(200).forEach(writer::value); writer.endArray()
            writer.name("favorites").beginArray(); GifLibrary.favorites(context).take(200).forEach(writer::value); writer.endArray()
            writer.endObject()
            writer.name("drafts"); if (drafts == null) writer.nullValue() else writer.value(drafts)
            writer.endObject()
            writer.flush()
            zip.closeEntry()
        }
    }
}

/** Accepts both the portable v3 archive and legacy JSON-only backups. */
internal fun importLocalData(context: Context, source: Uri): ImportedLocalData {
    val input = context.contentResolver.openInputStream(source) ?: error("Couldn't open the selected backup")
    BufferedInputStream(input, 64 * 1024).use { buffered ->
        buffered.mark(4)
        val signature = ByteArray(4)
        val read = buffered.read(signature)
        buffered.reset()
        return if (read >= 2 && signature[0] == 'P'.code.toByte() && signature[1] == 'K'.code.toByte()) {
            importArchive(context, buffered)
        } else {
            JsonReader(BufferedReader(InputStreamReader(buffered, Charsets.UTF_8), 64 * 1024)).use(::readManifest)
        }
    }
}

private fun importArchive(context: Context, input: InputStream): ImportedLocalData {
    val draftRoot = File(context.filesDir, "compose-drafts")
    val staging = File(draftRoot, "import-media-${UUID.randomUUID()}")
    var imported: ImportedLocalData? = null
    try {
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when {
                    entry.name == BACKUP_MANIFEST -> {
                        imported = readManifest(JsonReader(InputStreamReader(zip, Charsets.UTF_8)))
                    }
                    entry.name.startsWith("$DRAFT_MEDIA/") && !entry.isDirectory -> {
                        val name = entry.name.substringAfterLast('/').takeIf { it.isNotBlank() }
                            ?: error("Invalid draft attachment entry")
                        staging.mkdirs()
                        File(staging, name).outputStream().buffered(64 * 1024).use { zip.copyTo(it, 64 * 1024) }
                    }
                }
                zip.closeEntry()
            }
        }
        val result = imported ?: error("This is not a valid 2c backup")
        val restored = File(draftRoot, "restored-media")
        restored.deleteRecursively()
        if (staging.exists()) {
            restored.parentFile?.mkdirs()
            if (!staging.renameTo(restored)) {
                staging.copyRecursively(restored, overwrite = true)
                staging.deleteRecursively()
            }
        }
        return result
    } catch (error: Throwable) {
        staging.deleteRecursively()
        throw error
    }
}

private fun readManifest(reader: JsonReader): ImportedLocalData {
    var offline = false
    var verifiedOnly = false
    var autoLike = true
    var autoPlay = false
    var wifiOnly = false
    var haptics = true
    var muted = emptyList<String>()
    var notificationPreferences: String? = null
    var activityStats: String? = null
    var followersScan: String? = null
    var gifLibrary: JSONObject? = null
    var drafts: String? = null

    reader.beginObject()
    while (reader.hasNext()) {
        when (reader.nextName()) {
            "offline" -> offline = reader.nextBoolean()
            "verified_only" -> verifiedOnly = reader.nextBoolean()
            "auto_like_own_content" -> autoLike = reader.nextBoolean()
            "auto_play_videos" -> autoPlay = reader.nextBoolean()
            "wifi_only_media" -> wifiOnly = reader.nextBoolean()
            "haptic_feedback" -> haptics = reader.nextBoolean()
            "notification_preferences" -> notificationPreferences = reader.nextNullableString()
            "muted" -> muted = reader.readStringArray()
            "search_posts" -> {
                // A backup is a snapshot, not an additive feed page. Removing the
                // prior corpus prevents stale rows surviving an import.
                AdvancedSearchIndex.clear()
                AdvancedSearchIndex.importRows(reader)
            }
            "activity_stats" -> activityStats = reader.nextNullableString()
            "followers_scan" -> followersScan = reader.nextNullableString()
            "gif_library" -> gifLibrary = reader.readGifLibrary()
            "drafts" -> drafts = reader.nextNullableString()
            else -> reader.skipValue()
        }
    }
    reader.endObject()
    return ImportedLocalData(offline, verifiedOnly, autoLike, autoPlay, wifiOnly, haptics, muted,
        notificationPreferences, activityStats, followersScan, gifLibrary, drafts)
}

private fun prepareDraftArchive(context: Context): Pair<String?, List<DraftMediaEntry>> {
    val draftFile = File(context.filesDir, "compose-drafts/drafts.json")
    if (!draftFile.isFile) return null to emptyList()
    val drafts = JSONArray(draftFile.readText())
    val media = mutableListOf<DraftMediaEntry>()
    val mapped = mutableMapOf<String, String>()
    for (draftIndex in 0 until drafts.length()) {
        val draft = drafts.optJSONObject(draftIndex)?.optJSONObject("draft") ?: continue
        val uris = draft.optJSONArray("mediaUris") ?: continue
        for (mediaIndex in 0 until uris.length()) {
            val raw = uris.optString(mediaIndex)
            if (raw.isBlank() || raw.startsWith("http://") || raw.startsWith("https://")) continue
            val restoredUri = mapped.getOrPut(raw) {
                val source = Uri.parse(raw)
                val extension = mediaExtension(context, source)
                val name = "${UUID.randomUUID()}${extension.takeIf(String::isNotBlank)?.let { ".$it" }.orEmpty()}"
                val target = File(context.filesDir, "compose-drafts/restored-media/$name")
                val archivePath = "$DRAFT_MEDIA/$name"
                // Opening now makes a broken/expired picker grant fail the export instead
                // of producing a backup that only appears to contain the attachment.
                context.contentResolver.openInputStream(source)?.use { } ?: error("A draft attachment could not be read")
                media += DraftMediaEntry(archivePath, source)
                FileProvider.getUriForFile(context, "${context.packageName}.files", target).toString()
            }
            uris.put(mediaIndex, restoredUri)
        }
    }
    return drafts.toString() to media
}

private fun mediaExtension(context: Context, uri: Uri): String {
    val displayName = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()
    displayName?.substringAfterLast('.', "")?.takeIf { it.length in 1..8 }?.let { return it.lowercase() }
    return context.contentResolver.getType(uri)?.let(MimeTypeMap.getSingleton()::getExtensionFromMimeType).orEmpty()
}

private fun JsonReader.nextNullableString(): String? =
    if (peek() == JsonToken.NULL) { nextNull(); null } else nextString()

private fun JsonReader.readStringArray(): List<String> = buildList {
    beginArray()
    while (hasNext()) nextNullableString()?.takeIf(String::isNotBlank)?.let(::add)
    endArray()
}

private fun JsonReader.readGifLibrary(): JSONObject {
    val root = JSONObject()
    beginObject()
    while (hasNext()) {
        val name = nextName()
        if (name == "saved" || name == "favorites") root.put(name, JSONArray(readStringArray())) else skipValue()
    }
    endObject()
    return root
}

private const val BACKUP_MANIFEST = "backup.json"
private const val DRAFT_MEDIA = "draft-media"

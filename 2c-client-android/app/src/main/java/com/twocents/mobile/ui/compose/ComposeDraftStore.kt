package com.twocents.mobile.ui.compose

import android.content.Context
import com.twocents.mobile.ui.feed.FeedPost
import com.twocents.mobile.ui.feed.parseFeedPost
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal data class StoredComposeDraft(
    val id: String,
    val savedAt: Long,
    val draft: ComposePostDraft,
)

/** Draft metadata lives in filesDir, so clearing image/network caches never removes it. */
internal class ComposeDraftStore(context: Context) {
    private val directory = File(context.filesDir, "compose-drafts")
    private val file = File(directory, "drafts.json")

    suspend fun load(): List<StoredComposeDraft> = withContext(Dispatchers.IO) { readNow() }

    suspend fun save(draft: ComposePostDraft, existingId: String? = null): List<StoredComposeDraft> = withContext(Dispatchers.IO) {
        val id = existingId ?: UUID.randomUUID().toString()
        val updated = (readNow().filterNot { it.id == id } + StoredComposeDraft(id, System.currentTimeMillis(), draft))
            .sortedByDescending(StoredComposeDraft::savedAt)
        writeNow(updated)
        updated
    }

    suspend fun delete(id: String): List<StoredComposeDraft> = withContext(Dispatchers.IO) {
        val updated = readNow().filterNot { it.id == id }
        writeNow(updated)
        updated
    }

    private fun readNow(): List<StoredComposeDraft> {
        if (!file.isFile) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            buildList {
                for (index in 0 until array.length()) {
                    val root = array.optJSONObject(index) ?: continue
                    val id = root.optString("id")
                    if (id.isBlank()) continue
                    val draft = root.optJSONObject("draft")?.toDraft() ?: continue
                    add(StoredComposeDraft(id, root.optLong("savedAt"), draft))
                }
            }.sortedByDescending(StoredComposeDraft::savedAt)
        }.getOrDefault(emptyList())
    }

    private fun writeNow(drafts: List<StoredComposeDraft>) {
        directory.mkdirs()
        val array = JSONArray()
        drafts.forEach { stored ->
            array.put(
                JSONObject()
                    .put("id", stored.id)
                    .put("savedAt", stored.savedAt)
                    .put("draft", stored.draft.toJson()),
            )
        }
        val temporary = File(directory, "drafts.tmp")
        temporary.writeText(array.toString())
        if (!temporary.renameTo(file)) {
            file.writeText(temporary.readText())
            temporary.delete()
        }
    }
}

private fun ComposePostDraft.toJson() = JSONObject()
    .put("title", title)
    .put("body", body)
    .put("topic", topic)
    .put("option", option?.name)
    .put("pollOptions", JSONArray(pollOptions))
    .put("mediaUris", JSONArray(mediaUris))
    .put("pollLink", pollLink)
    .apply { quotedPost?.let { put("quotedPost", it.toDraftPostJson()) } }

private fun JSONObject.toDraft(): ComposePostDraft {
    fun strings(key: String): List<String> = optJSONArray(key)?.let { array ->
        (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
    }.orEmpty()
    return ComposePostDraft(
        title = optString("title"),
        body = optString("body"),
        topic = optString("topic").ifBlank { "Lounge" },
        option = optString("option").takeIf(String::isNotBlank)?.let { runCatching { ComposePostOption.valueOf(it) }.getOrNull() },
        pollOptions = strings("pollOptions").ifEmpty { listOf("", "") },
        mediaUris = strings("mediaUris"),
        pollLink = optString("pollLink").takeIf { it.isNotBlank() && it != "null" },
        quotedPost = optJSONObject("quotedPost")?.let(::parseFeedPost),
    )
}

private fun FeedPost.toDraftPostJson(): JSONObject {
    val postMeta = JSONObject()
        .put("platform", meta.platform)
        .put("poll", JSONArray(meta.poll))
        .put("image_urls", JSONArray(meta.images))
    meta.videoUrl?.let { postMeta.put("video_url", it) }
    meta.link?.let { postMeta.put("link", it) }
    meta.giphyUrl?.let { postMeta.put("giphy_url", it) }
    meta.tweetUrl?.let { postMeta.put("tweet_url", it) }
    return JSONObject()
        .put("uuid", uuid)
        .put("created_at", createdAt)
        .put("author_uuid", authorUuid)
        .put("upvote_count", upvoteCount)
        .put("comment_count", commentCount)
        .put("view_count", viewCount)
        .put("title", title)
        .put("text", text)
        .put("topic", topic)
        .put("post_type", postType)
        .put("post_meta", postMeta)
        .put(
            "author_meta",
            JSONObject()
                .put("balance", author.balance)
                .put("subscription_type", author.subscriptionType)
                .put("age", author.age)
                .put("gender", author.gender)
                .put("arena", author.arena)
                .put("role", author.role),
        )
}

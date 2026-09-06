package com.twocents.mobile.ui.compose

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.twocents.mobile.AuthState
import com.twocents.mobile.RpcApi
import com.twocents.mobile.ui.feed.FeedPost
import com.twocents.mobile.ui.feed.parseFeedPost
import com.twocents.mobile.ui.settings.InteractionPreferences
import com.twocents.mobile.data.VoteRepository
import org.json.JSONArray
import org.json.JSONObject

private data class UploadedComposeMedia(
    val publicUrl: String,
    val isVideo: Boolean,
)

/** Mirrors the RN create-post payload, including the direct-to-storage media path. */
suspend fun createComposePost(
    api: RpcApi,
    auth: AuthState,
    draft: ComposePostDraft,
    context: Context,
): FeedPost? {
    val uploadedMedia = uploadComposeMedia(api, auth, context, draft.mediaUris)

    val postType = when {
        draft.option == ComposePostOption.Poll -> 2
        uploadedMedia.any { it.isVideo } -> 10
        uploadedMedia.isNotEmpty() -> 4
        draft.quotedPost != null -> 3
        draft.option == ComposePostOption.Likert -> 5
        else -> 0
    }
    val meta = JSONObject()
        .put("version", 1)
        .put("platform", "android")

    if (draft.option == ComposePostOption.Poll) {
        meta.put("poll", JSONArray(draft.pollOptions.filter { it.isNotBlank() }))
        draft.pollLink?.trim()?.takeIf { it.startsWith("http") }?.let { meta.put("tweet_url", it) }
    }
    if (uploadedMedia.isNotEmpty()) {
        val video = uploadedMedia.singleOrNull { it.isVideo }
        if (video != null) {
            meta.put("media_type", "video")
            meta.put("videoUrl", video.publicUrl)
            // Keep legacy readers functional while videoUrl is the canonical API field.
            meta.put("video_url", video.publicUrl)
        } else {
            val urls = JSONArray(uploadedMedia.map { it.publicUrl })
            meta.put("src", uploadedMedia.first().publicUrl)
            meta.put("imageUrls", urls)
            meta.put("image_urls", urls)
            if (draft.mediaUris.singleOrNull()?.let { it.startsWith("http://") || it.startsWith("https://") } == true) {
                meta.put("giphy_url", uploadedMedia.first().publicUrl)
                meta.put("giphy_id", uploadedMedia.first().publicUrl)
            }
        }
    }
    draft.quotedPost?.let { meta.put("quote_post", it.toQuoteJson()) }

    val result = api.call(
        method = "/v1/posts/create",
        params = JSONObject()
            .put("title", draft.title)
            .put("topic", composeTopicSlug(draft.topic))
            .put("text", formatComposeTextForApi(draft.body))
            .put("post_type", postType)
            .put("post_meta", meta),
        auth = auth,
    )
    val root = result as? JSONObject ?: return null
    val post = root.optJSONObject("post")?.let(::parseFeedPost) ?: return null
    if (InteractionPreferences.autoLikeOwnContent(context)) {
        // Creation is authoritative; a failed convenience vote must never turn a
        // successfully published post into an apparent submission failure.
        VoteRepository(api, auth).post(post.uuid, 1)
    }
    return post
}

private fun FeedPost.toQuoteJson(): JSONObject {
    val postMeta = JSONObject()
        .put("platform", meta.platform)
        .put("poll", JSONArray(meta.poll))
    if (meta.images.isNotEmpty()) {
        postMeta.put("src", meta.images.first())
        postMeta.put("imageUrls", JSONArray(meta.images))
    }
    meta.videoUrl?.let { postMeta.put("media_type", "video").put("videoUrl", it) }
    meta.link?.let { postMeta.put("link", it) }
    meta.giphyUrl?.let { postMeta.put("giphy_url", it) }
    meta.tweetUrl?.let { postMeta.put("tweet_url", it) }
    return JSONObject()
        .put("uuid", uuid)
        .put("created_at", createdAt)
        .put("updated_at", createdAt)
        .put("author_uuid", authorUuid)
        .put("upvote_count", upvoteCount)
        .put("comment_count", commentCount)
        .put("view_count", viewCount)
        .put("report_count", 0)
        .put("bookmark_count", 0)
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
                .put("role", author.role)
                .put("elo_rating", author.eloRating),
        )
}

private suspend fun uploadComposeMedia(
    api: RpcApi,
    auth: AuthState,
    context: Context,
    mediaUris: List<String>,
): List<UploadedComposeMedia> {
    if (mediaUris.isEmpty()) return emptyList()
    val resolver = context.contentResolver
    val remote = mediaUris.filter { it.startsWith("http://") || it.startsWith("https://") }
    require(remote.size <= 1) { "Only one saved GIF can be attached" }
    if (remote.isNotEmpty()) {
        require(mediaUris.size == 1) { "A saved GIF cannot be combined with other media" }
        val (contentType, bytes) = api.downloadBinary(remote.single())
        val result = api.call(
            method = "/v1/media/uploadImage",
            params = JSONObject().put("contentType", contentType).put("size", bytes.size),
            auth = auth,
        ) as? JSONObject ?: throw IllegalStateException("Media upload response was invalid")
        api.putBytes(result.getString("presignedURL"), bytes, contentType)
        return listOf(UploadedComposeMedia(result.getString("publicURL"), isVideo = false))
    }
    val details = mediaUris.map { rawUri ->
        val uri = Uri.parse(rawUri)
        val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        val extension = displayName?.substringAfterLast('.', "")?.lowercase().orEmpty()
        val contentType = resolver.getType(uri)?.takeIf { it.isNotBlank() }
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
        val size = resolver.openAssetFileDescriptor(uri, "r")?.use { descriptor -> descriptor.length }
            ?.takeIf { it > 0 }
            ?: resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }?.takeIf { it > 0 }
            ?: throw IllegalArgumentException("Selected media size is unavailable")
        LocalComposeMedia(uri, contentType, size, contentType.startsWith("video/"))
    }

    val videos = details.filter { it.isVideo }
    val images = details.filterNot { it.isVideo }
    require(details.all { it.isVideo || it.contentType.startsWith("image/") }) { "Unsupported media type" }
    require(videos.size <= 1) { "Only one video can be attached" }
    require(images.size <= 4) { "Only four images can be attached" }
    require(videos.isEmpty() || images.isEmpty()) { "Video and images cannot be attached together" }

    if (videos.isNotEmpty()) {
        val video = videos.single()
        val result = api.call(
            method = "/v1/media/uploadVideo",
            params = JSONObject()
                .put("contentType", video.contentType)
                .put("size", video.size),
            auth = auth,
        ) as? JSONObject ?: throw IllegalStateException("Video upload response was invalid")
        val presignedUrl = result.getString("presignedURL")
        val publicUrl = result.getString("publicURL")
        api.putBinary(presignedUrl, resolver, video.uri, video.contentType, video.size)
        return listOf(UploadedComposeMedia(publicUrl, isVideo = true))
    }

    val imageParams = JSONArray().apply {
        images.forEach { image ->
            put(JSONObject().put("contentType", image.contentType).put("size", image.size))
        }
    }
    val result = api.call(
        method = "/v1/media/uploadImageBulk",
        params = JSONObject().put("images", imageParams),
        auth = auth,
    ) as? JSONObject ?: throw IllegalStateException("Media upload response was invalid")
    val uploads = result.optJSONArray("uploads")
        ?: throw IllegalStateException("Media upload response did not include uploads")
    if (uploads.length() != images.size) {
        throw IllegalStateException("Media upload response count did not match selection")
    }

    return images.mapIndexed { index, image ->
        val upload = uploads.getJSONObject(index)
        val presignedUrl = upload.getString("presignedURL")
        val publicUrl = upload.getString("publicURL")
        api.putBinary(presignedUrl, resolver, image.uri, image.contentType, image.size)
        UploadedComposeMedia(publicUrl, isVideo = false)
    }
}

private data class LocalComposeMedia(
    val uri: Uri,
    val contentType: String,
    val size: Long,
    val isVideo: Boolean,
)

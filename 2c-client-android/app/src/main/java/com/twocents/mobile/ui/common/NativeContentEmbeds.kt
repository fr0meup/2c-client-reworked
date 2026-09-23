package com.twocents.mobile.ui.common

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twocents.mobile.AuthState
import com.twocents.mobile.RpcApi
import com.twocents.mobile.ui.compose.ComposeAuthorProfile
import com.twocents.mobile.ui.compose.ComposeNetworthPill
import com.twocents.mobile.ui.feed.*
import com.twocents.mobile.ui.profile.ProfileNavigationBus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

internal data class NativeContentTarget(val postUuid: String, val commentUuid: String?)
internal fun nativeContentTarget(url: String): NativeContentTarget? {
    val uri = Uri.parse(url)
    if (uri.host?.lowercase()?.removePrefix("www.") !in setOf("twocents.com", "twocents.money")) return null
    val parts = uri.pathSegments
    if (parts.firstOrNull() != "post") return null
    val post = parts.getOrNull(1)?.takeIf(String::isNotBlank) ?: return null
    return NativeContentTarget(post, uri.getQueryParameter("comment")?.takeIf(String::isNotBlank))
}

internal val LocalContentEmbeds = staticCompositionLocalOf<NativeContentRepository?> { null }
private val LocalEmbedDepth = staticCompositionLocalOf { 0 }
internal data class NativeContent(
    val post: FeedPost?,
    val comment: PostComment?,
    val pollVote: Int? = null,
    val pollResults: Map<Int, FeedOptionResult>? = null,
)

/** Short-lived account-local cache; duplicate embeds share requests and never recurse. */
internal class NativeContentRepository(private val api: RpcApi, private val auth: AuthState) {
    internal val authUserUuid: String get() = auth.userUuid
    private val mutex = Mutex()
    private val cache = linkedMapOf<NativeContentTarget, Pair<Long, NativeContent>>()
    private var aliases = emptyMap<String, String>()
    private var aliasesCheckedAt = 0L
    suspend fun get(target: NativeContentTarget): NativeContent = mutex.withLock {
        cache[target]?.takeIf { System.currentTimeMillis() - it.first < 120_000 }?.let { return@withLock it.second }
        // Author metadata doesn't contain the viewer's nicknames. Share one alias
        // lookup across embeds instead of issuing a request for every card.
        if (System.currentTimeMillis() - aliasesCheckedAt > 120_000) {
            try {
                aliases = com.twocents.mobile.data.AliasRepository(api, auth).load()
                aliasesCheckedAt = System.currentTimeMillis()
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel } catch (_: Exception) { /* Alias failure must not hide otherwise available content. */ }
        }
        val content = if (target.commentUuid == null) {
            // The detail response contains the viewer's selected poll option.
            val root = api.call("/v1/posts/get", JSONObject().put("post_uuid", target.postUuid), auth) as? JSONObject
            val post = root?.optJSONObject("post")?.let(::parseFeedPost)
                ?.let { it.copy(author = it.author.copy(alias = aliases[it.authorUuid] ?: it.author.alias)) }
            val vote = root?.optJSONArray("polls")?.let { rows ->
                (0 until rows.length()).mapNotNull(rows::optJSONObject)
                    .firstOrNull { it.has("option") }?.optInt("option")
            }
            val results = if (post?.hasPoll == true) {
                val poll = api.call("/v1/polls/get", JSONObject().put("post_uuid", post.uuid), auth) as? JSONObject
                parseOptionResults(poll?.optJSONObject("results"), averageKeys = listOf("average_balance"))
            } else null
            NativeContent(post, null, vote, results)
        } else {
            val root = api.call("/v1/comments/get", JSONObject().put("post_uuid", target.postUuid), auth) as? JSONObject
                ?: error("Comment unavailable")
            val comments = root.optJSONArray("comments")
            val comment = (0 until (comments?.length() ?: 0)).mapNotNull { comments?.optJSONObject(it) }
                .firstOrNull { it.optString("uuid") == target.commentUuid }?.let(::parseComment)
            NativeContent(null, comment?.copy(author = comment.author.copy(alias = aliases[comment.authorUuid] ?: comment.author.alias)))
        }
        cache[target] = System.currentTimeMillis() to content
        while (cache.size > 64) cache.remove(cache.keys.first())
        content
    }
}

@Composable
internal fun NativeContentEmbed(url: String, fallback: @Composable () -> Unit) {
    val target = remember(url) { nativeContentTarget(url) }
    val repository = LocalContentEmbeds.current
    if (target == null || repository == null || LocalEmbedDepth.current > 0) { fallback(); return }
    var content by remember(repository, target) { mutableStateOf<NativeContent?>(null) }
    var failed by remember(repository, target) { mutableStateOf(false) }
    LaunchedEffect(repository, target) {
        try { content = repository.get(target) } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel } catch (_: Exception) { failed = true }
    }
    if (failed || content?.let { it.post == null && (it.comment == null || it.comment.deleted) } == true) {
        fallback(); return
    }
    CompositionLocalProvider(LocalEmbedDepth provides 1) {
        val post = content?.post
        val comment = content?.comment
        if (post != null) FeedQuoteCard(
            post, onClick = { AppLinkRouter.open(url) }, showUserMeta = true, authUuid = repository.authUserUuid,
            embeddedPollVote = content?.pollVote, embeddedPollResults = content?.pollResults,
            opaqueSurface = LocalChatEmbedSurface.current,
        )
        else Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = if (LocalBalancedEmbedSpacing.current) 6.dp else 0.dp).clip(RoundedCornerShape(14.dp))
            .background(if (LocalChatEmbedSurface.current) Color(0xFF171713) else Color.White.copy(alpha = .02f))
            .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(14.dp))
            .clickable { AppLinkRouter.open(url) }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            if (comment == null) Text("Loading twocents ${if (target.commentUuid == null) "post" else "comment"}…", color = Color.Gray, fontSize = 12.sp)
            else {
                val profile = ComposeAuthorProfile(comment.authorUuid, comment.author.balance, comment.author.subscriptionType)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.clickable { ProfileNavigationBus.open(profile) }) { ComposeNetworthPill(profile, comment.authorUuid, compact = true) }
                    CommentTimestamp(comment.createdAt, Modifier.weight(1f, fill = false))
                    Text("· Comment", color = Color(0xFFC8A44D), fontSize = 11.sp, maxLines = 1)
                }
                ExpandableCommentBody(comment.visibleText(), "embed-${comment.uuid}")
                if (comment.mediaUrls.isNotEmpty()) FeedPostMedia(comment.mediaUrls, compact = true)
                commentTweetUrl(comment.text)?.let { TweetEmbedCard(it) }
                UserMetaPill(comment.author.toUserDisplay(comment.authorUuid, comment.author.alias),
                    comment.author.alias, Modifier.fillMaxWidth(), compact = true)
            }
        }
    }
}

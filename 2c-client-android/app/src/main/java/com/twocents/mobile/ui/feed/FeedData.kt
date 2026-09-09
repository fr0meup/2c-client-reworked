package com.twocents.mobile.ui.feed

import android.content.Context
import android.util.Base64
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.twocents.mobile.AuthState
import com.twocents.mobile.RpcApi
import com.twocents.mobile.data.AliasRepository
import com.twocents.mobile.data.VoteRepository
import com.twocents.mobile.core.json.double
import com.twocents.mobile.core.json.int
import com.twocents.mobile.core.json.nullableDouble
import com.twocents.mobile.core.json.nullableInt
import com.twocents.mobile.core.json.number
import com.twocents.mobile.core.json.objectValue
import com.twocents.mobile.core.json.objects
import com.twocents.mobile.core.json.string
import com.twocents.mobile.core.json.strings
import com.twocents.mobile.core.media.looksLikeGifUrl
import com.twocents.mobile.core.media.looksLikeVideoUrl
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.withLock

/**
 * Owns one feed source, its bounded page cache, and optimistic interaction state.
 * Presentation and advanced-scan orchestration live in separate files.
 */
@Stable
class FeedController(
    private val api: RpcApi,
    private val auth: AuthState,
    internal val source: FeedSource = FeedSource.Arena,
    context: Context,
) {
    var state by mutableStateOf(FeedUiState())
        internal set

    private val feedCache = object : LinkedHashMap<String, FeedCacheEntry>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FeedCacheEntry>?) = size > 8
    }
    private val pollResultsCache = mutableMapOf<String, Map<Int, FeedOptionResult>>()
    private val likertResultsCache = mutableMapOf<String, Map<Int, FeedOptionResult>>()
    private val picksResultsCache = mutableMapOf<String, FeedPicksResult>()
    private val pendingResults = mutableSetOf<String>()
    internal val pendingMutations = mutableSetOf<String>()
    private var aliasesLoaded = false
    internal var aliases: Map<String, String> = emptyMap()
    private val aliasRepository = AliasRepository(api, auth)
    private val voteRepository = VoteRepository(api, auth)
    internal var activeKey = ""
    internal var requestGeneration = 0
    internal val mutedUsers = MutedUsersStore(context, auth.userUuid)
    internal var advancedDisplayFilters = AdvancedSearchFilters()
    internal var advancedScanKey: String? = null
    internal var advancedCorpus: List<FeedPost> = emptyList()
    internal var advancedCorpusReady = false
    internal var advancedSessionHasScan = false

    init {
        AdvancedSearchIndex.initialize(context)
    }

    suspend fun load(topic: String, query: String, force: Boolean = false): Boolean {
        val key = cacheKey(topic, query)
        val keyChanged = key != activeKey
        if (!force && !keyChanged && state.posts.isNotEmpty()) return false
        activeKey = key
        // Generations make late responses harmless when topic/query changes race.
        val generation = ++requestGeneration
        val cached = feedCache[key]
        if (!force && cached != null && System.currentTimeMillis() - cached.storedAt < CACHE_TTL_MS) {
            state = cached.state.copy(
                posts = cached.state.posts.filterNot { mutedUsers.isMuted(it.authorUuid) },
                aliases = aliases,
                pollResults = pollResultsCache.toMap(),
                likertResults = likertResultsCache.toMap(),
                picksResults = picksResultsCache.toMap(),
                isInitialLoading = false,
            )
            return true
        }

        state = if (keyChanged) {
            FeedUiState(isInitialLoading = true)
        } else {
            state.copy(isInitialLoading = state.posts.isEmpty(), error = null)
        }
        runCatching { requestPage(topic, query, cursor = null) }
            .onSuccess { page ->
                if (generation != requestGeneration || key != activeKey) return@onSuccess
                state = page.withoutMuted().toState().copy(
                    aliases = aliases,
                    pollResults = pollResultsCache.toMap(),
                    likertResults = likertResultsCache.toMap(),
                    picksResults = picksResultsCache.toMap(),
                )
                feedCache[key] = FeedCacheEntry(System.currentTimeMillis(), state)
            }
            .onFailure { error ->
                if (error is CancellationException) throw error
                if (generation != requestGeneration || key != activeKey) return@onFailure
                state = state.copy(isInitialLoading = false, error = error.message ?: "Unable to load feed")
            }
        return keyChanged
    }

    suspend fun refresh(topic: String, query: String): Boolean {
        load(topic, query, force = true)
        return state.error == null
    }

    /**
     * Reconciles server-fresh post metadata returned by a nested screen into every
     * in-memory copy owned by this controller. Interaction maps stay untouched. 
     * EASTEREGG: if you see this you're gay
     */
    internal fun reconcilePostSnapshot(post: FeedPost) {
        fun List<FeedPost>.reconciled(): List<FeedPost> =
            map { current -> if (current.uuid == post.uuid) post else current }

        if (state.posts.any { it.uuid == post.uuid }) {
            state = state.copy(posts = state.posts.reconciled())
        }
        feedCache.replaceAll { _, entry ->
            if (entry.state.posts.none { it.uuid == post.uuid }) entry
            else entry.copy(state = entry.state.copy(posts = entry.state.posts.reconciled()))
        }
        if (advancedCorpus.any { it.uuid == post.uuid }) {
            advancedCorpus = advancedCorpus.reconciled()
        }
    }

    suspend fun loadMore(topic: String, query: String) {
        val snapshot = state
        if (snapshot.isLoadingMore || !snapshot.hasMore || snapshot.nextCursor.isNullOrBlank()) return
        state = snapshot.copy(isLoadingMore = true)
        runCatching { requestPage(topic, query, snapshot.nextCursor) }
            .onSuccess { page ->
                if (cacheKey(topic, query) != activeKey) return@onSuccess
                val existing = state.posts.associateByTo(LinkedHashMap()) { it.uuid }
                page.posts.filterNot { mutedUsers.isMuted(it.authorUuid) }.forEach { existing[it.uuid] = it }
                state = state.copy(
                    posts = existing.values.toList(),
                    postVotes = state.postVotes + page.postVotes,
                    pollVotes = state.pollVotes + page.pollVotes,
                    likertVotes = state.likertVotes + page.likertVotes,
                    pickVotes = state.pickVotes + page.pickVotes,
                    nextCursor = page.nextCursor,
                    hasMore = page.hasMore,
                    isLoadingMore = false,
                )
                feedCache[activeKey] = FeedCacheEntry(System.currentTimeMillis(), state)
            }
            .onFailure { state = state.copy(isLoadingMore = false) }
    }

    suspend fun ensureAliases() {
        if (aliasesLoaded) return
        aliasesLoaded = true
        runCatching { aliasRepository.load() }.onSuccess { loaded ->
            aliases = loaded.orEmpty()
            state = state.copy(aliases = aliases)
        }.onFailure {
            aliasesLoaded = false
        }
    }

    suspend fun togglePostVote(postUuid: String, direction: Int) {
        val pendingKey = "post:$postUuid"
        if (!pendingMutations.add(pendingKey)) return
        val oldVote = state.postVotes[postUuid] ?: 0
        val nextVote = if (oldVote == direction) 0 else direction
        val oldPosts = state.posts
        state = state.copy(
            postVotes = state.postVotes + (postUuid to nextVote),
            posts = state.posts.map { post ->
                if (post.uuid == postUuid) post.copy(upvoteCount = post.upvoteCount + nextVote - oldVote) else post
            },
        )
        if (!voteRepository.post(postUuid, nextVote)) {
            state = state.copy(postVotes = state.postVotes + (postUuid to oldVote), posts = oldPosts)
        }
        pendingMutations.remove(pendingKey)
    }

    fun isFollowing(authorUuid: String): Boolean = aliases.containsKey(authorUuid)
    fun isBookmarked(postUuid: String): Boolean = postUuid in state.bookmarkedPosts || source == FeedSource.Bookmarks

    suspend fun toggleFollowing(authorUuid: String, alias: String? = null): Boolean {
        val following = aliases.containsKey(authorUuid)
        val (succeeded, updated) = aliasRepository.toggle(aliases, authorUuid, alias)
        if (succeeded) {
            aliases = updated
            state = state.copy(aliases = aliases)
        }
        return succeeded
    }

    suspend fun toggleBookmark(postUuid: String): Boolean? {
        val result = runCatching {
            api.call(
                "/v1/bookmarks/bookmark",
                JSONObject().put("postUUID", postUuid),
                auth,
            ) as? JSONObject
        }.getOrNull() ?: return null
        val bookmarked = result.optBoolean("bookmarked", true)
        state = state.copy(bookmarkedPosts = if (bookmarked) state.bookmarkedPosts + postUuid else state.bookmarkedPosts - postUuid)
        if (source == FeedSource.Bookmarks && !bookmarked) {
            state = state.copy(posts = state.posts.filterNot { it.uuid == postUuid })
        }
        return bookmarked
    }

    suspend fun loadQuotes(postUuid: String): List<FeedPost> = runCatching {
        val root = api.call("/v2/posts/quotes", JSONObject().put("post_uuid", postUuid), auth) as? JSONObject
        root?.optJSONArray("posts").objects().mapNotNull(::parseFeedPost).orEmpty()
    }.getOrDefault(emptyList())

    suspend fun blockAuthor(authorUuid: String): Boolean {
        val succeeded = runCatching {
            api.call("/v1/users/block", JSONObject().put("blocked_uuid", authorUuid), auth)
        }.isSuccess
        if (succeeded) state = state.copy(posts = state.posts.filterNot { it.authorUuid == authorUuid })
        return succeeded
    }

    suspend fun unblockAuthor(authorUuid: String): Boolean = runCatching {
        api.call("/v1/users/unblock", JSONObject().put("blocked_uuid", authorUuid), auth)
        true
    }.getOrDefault(false)

    fun isMuted(authorUuid: String): Boolean = mutedUsers.isMuted(authorUuid)

    fun toggleMuted(authorUuid: String): Boolean? {
        val muted = !mutedUsers.isMuted(authorUuid)
        if (!mutedUsers.setMuted(authorUuid, muted)) return null
        if (muted) {
            state = state.copy(posts = state.posts.filterNot { it.authorUuid == authorUuid })
            feedCache.replaceAll { _, entry ->
                entry.copy(state = entry.state.copy(posts = entry.state.posts.filterNot { it.authorUuid == authorUuid }))
            }
        }
        return muted
    }

    suspend fun deletePost(postUuid: String): Boolean {
        val succeeded = runCatching {
            api.call("/v1/posts/delete", JSONObject().put("post_uuid", postUuid), auth)
        }.isSuccess
        if (succeeded) state = state.copy(posts = state.posts.filterNot { it.uuid == postUuid })
        return succeeded
    }

    suspend fun startDirectMessage(authorUuid: String): String? = runCatching {
        val result = api.call(
            "/v1/rooms/startDM",
            JSONObject().put("recipientUuid", authorUuid),
            auth,
        ) as? JSONObject
        result?.optJSONObject("room")?.let { room -> room.string("uuid") ?: room.string("room_uuid") }
            ?: result?.let { root -> root.string("roomUuid") ?: root.string("room_uuid") ?: root.string("uuid") }
    }.getOrNull()

    suspend fun votePoll(postUuid: String, option: Int) {
        val pendingKey = "poll:$postUuid"
        if (state.pollVotes.containsKey(postUuid) || !pendingMutations.add(pendingKey)) return
        state = state.copy(pollVotes = state.pollVotes + (postUuid to option))
        val succeeded = voteRepository.poll(postUuid, option)
        if (!succeeded) state = state.copy(pollVotes = state.pollVotes - postUuid)
        pendingMutations.remove(pendingKey)
        if (succeeded) ensurePollResults(postUuid, force = true)
    }

    suspend fun voteLikert(postUuid: String, option: Int) {
        val pendingKey = "likert:$postUuid"
        if (state.likertVotes.containsKey(postUuid) || !pendingMutations.add(pendingKey)) return
        state = state.copy(likertVotes = state.likertVotes + (postUuid to option))
        val succeeded = voteRepository.likert(postUuid, option)
        if (!succeeded) state = state.copy(likertVotes = state.likertVotes - postUuid)
        pendingMutations.remove(pendingKey)
        if (succeeded) ensureLikertResults(postUuid, force = true)
    }

    suspend fun votePick(postUuid: String, vote: String) {
        val pendingKey = "pick:$postUuid"
        if (state.pickVotes.containsKey(postUuid) || !pendingMutations.add(pendingKey)) return
        state = state.copy(pickVotes = state.pickVotes + (postUuid to vote))
        val succeeded = voteRepository.pick(postUuid, vote)
        if (!succeeded) state = state.copy(pickVotes = state.pickVotes - postUuid)
        pendingMutations.remove(pendingKey)
        if (succeeded) ensurePicksResults(postUuid, force = true)
    }

    suspend fun ensurePollResults(postUuid: String, force: Boolean = false) {
        val key = "poll-result:$postUuid"
        if ((!force && pollResultsCache.containsKey(postUuid)) || !pendingResults.add(key)) return
        runCatching {
            val result = api.call("/v1/polls/get", JSONObject().put("post_uuid", postUuid), auth) as? JSONObject
            parseOptionResults(result?.optJSONObject("results"), averageKeys = listOf("average_balance"))
        }.getOrNull()?.let { result ->
            pollResultsCache[postUuid] = result
            state = state.copy(pollResults = state.pollResults + (postUuid to result))
        }
        pendingResults.remove(key)
    }

    suspend fun ensureLikertResults(postUuid: String, force: Boolean = false) {
        val key = "likert-result:$postUuid"
        if ((!force && likertResultsCache.containsKey(postUuid)) || !pendingResults.add(key)) return
        runCatching {
            val result = api.call(
                "/v1/likert/get",
                JSONObject().put("postUuid", postUuid).put("post_uuid", postUuid),
                auth,
            ) as? JSONObject
            parseOptionResults(result?.optJSONObject("results"), averageKeys = listOf("averageBalance", "average_balance"))
        }.getOrNull()?.let { result ->
            likertResultsCache[postUuid] = result
            state = state.copy(likertResults = state.likertResults + (postUuid to result))
        }
        pendingResults.remove(key)
    }

    suspend fun ensurePicksResults(postUuid: String, force: Boolean = false) {
        val key = "picks-result:$postUuid"
        if ((!force && picksResultsCache.containsKey(postUuid)) || !pendingResults.add(key)) return
        runCatching {
            // Same endpoint/schema as the web client; never cache an empty success.
            val root = api.call("/v1/picks/results", JSONObject().put("post_uuid", postUuid), auth) as? JSONObject
                ?: error("Invalid pick results")
            val results = root.optJSONObject("results") ?: error("Missing pick results")
            val yes = results.number("yes_percent")?.toDouble()?.toInt()?.coerceIn(0, 100) ?: 0
            FeedPicksResult(
                yesPercent = yes,
                noPercent = results.number("no_percent")?.toDouble()?.toInt()?.coerceIn(0, 100) ?: (100 - yes),
                resolved = root?.string("resolution_status") == "resolved",
                correctAnswer = root?.string("correct_answer"),
                yesAverageBalance = results?.optJSONObject("yes")?.number("average_balance")?.toDouble()?.takeIf(Double::isFinite),
                noAverageBalance = results?.optJSONObject("no")?.number("average_balance")?.toDouble()?.takeIf(Double::isFinite),
            )
        }.getOrNull()?.let { result ->
            picksResultsCache[postUuid] = result
            state = state.copy(picksResults = state.picksResults + (postUuid to result))
        }
        pendingResults.remove(key)
    }

    internal suspend fun requestPage(topic: String, query: String, cursor: String?): FeedPage {
        if (source == FeedSource.Bookmarks) {
            val root = api.call("/v1/bookmarks/all", JSONObject(), auth) as? JSONObject
                ?: error("Bookmarks response was invalid")
            return parseFeedPage(root).withoutMuted()
        }
        val params = JSONObject().put("sort_dir", "desc").put("filter", "chronological")
        topicToApi(topic)?.let { params.put("topic", it) }
        query.trim().takeIf { it.isNotEmpty() }?.let { params.put("q", it) }
        cursor?.let { params.put("cursor", it) }
        val root = api.call("/v2/posts/arena", params, auth) as? JSONObject
            ?: error("Feed response was invalid")
        return parseFeedPage(root).withoutMuted()
    }

    private fun FeedPage.withoutMuted(): FeedPage {
        val muted = mutedUsers.all()
        if (muted.isEmpty()) return this
        val visible = posts.filterNot { it.authorUuid in muted }
        val visibleIds = visible.mapTo(HashSet()) { it.uuid }
        return copy(
            posts = visible,
            postVotes = postVotes.filterKeys(visibleIds::contains),
            pollVotes = pollVotes.filterKeys(visibleIds::contains),
            likertVotes = likertVotes.filterKeys(visibleIds::contains),
            pickVotes = pickVotes.filterKeys(visibleIds::contains),
        )
    }

    private fun FeedPage.toState() = FeedUiState(
        posts = posts,
        postVotes = postVotes,
        pollVotes = pollVotes,
        likertVotes = likertVotes,
        pickVotes = pickVotes,
        aliases = aliases,
        bookmarkedPosts = if (source == FeedSource.Bookmarks) posts.mapTo(mutableSetOf()) { it.uuid } else emptySet(),
        isInitialLoading = false,
        hasMore = hasMore,
        nextCursor = nextCursor,
    )

    companion object {
        private const val CACHE_TTL_MS = 3 * 60 * 1000L
    }
}

private fun cacheKey(topic: String, query: String) = "$topic\u0000${query.trim()}"

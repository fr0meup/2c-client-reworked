package com.twocents.mobile.ui.feed

import android.util.Base64
import com.twocents.mobile.core.media.looksLikeGifUrl
import com.twocents.mobile.core.media.looksLikeVideoUrl
import java.time.Instant
import java.util.LinkedHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * Advanced-search orchestration is kept outside the everyday feed controller path.
 * It deliberately mutates the controller's existing state so scan generations,
 * optimistic status maps, and the 20-worker publication cadence stay unchanged.
 */
internal fun FeedController.beginAdvancedSearchSession() {
    advancedSessionHasScan = false
}

/** Clears stale result cards synchronously with the Search tap. */
internal fun FeedController.prepareAdvancedSearch(filters: AdvancedSearchFilters) {
    advancedDisplayFilters = filters
    state = state.copy(
        posts = emptyList(),
        advancedSearching = true,
        advancedScanned = 0,
        advancedMatches = 0,
        error = null,
        isInitialLoading = false,
    )
}

internal suspend fun FeedController.loadAdvanced(filters: AdvancedSearchFilters, force: Boolean = false): Boolean {
    val from = filters.dateFrom.takeIf(String::isNotBlank)?.let { runCatching { Instant.parse("${it}T00:00:00Z") }.getOrNull() }
        ?: Instant.parse("2024-12-06T00:00:00Z")
    val to = filters.dateTo.takeIf(String::isNotBlank)?.let { runCatching { Instant.parse("${it}T23:59:59.999Z") }.getOrNull() }
        ?: Instant.now()
    val scanKey = "${from}|${to}"
    advancedDisplayFilters = filters
    if (!force && advancedSessionHasScan) {
        val visible = filters.sort(advancedCorpus.filter(filters::matches).filterNot { mutedUsers.isMuted(it.authorUuid) })
        state = state.copy(posts = visible, advancedMatches = visible.size, error = null)
        return false
    }
    val key = "advanced:$scanKey"
    val changed = activeKey != key
    activeKey = key
    advancedScanKey = scanKey
    advancedCorpusReady = false
    advancedSessionHasScan = true
    val generation = ++requestGeneration
    state = state.copy(advancedSearching = true, advancedScanned = 0, error = null, isInitialLoading = false)
    val cached = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { AdvancedSearchIndex.snapshot(from, to) }
    val cachedStatuses = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { AdvancedSearchIndex.statuses(from, to) }
    advancedCorpus = cached
    val cachedVisible = filters.sort(cached.filter(filters::matches).filterNot { mutedUsers.isMuted(it.authorUuid) })
    // Do not publish stale cached rows as results. The UI keeps its searching
    // state until every worker has reconciled the corpus and vote metadata.
    state = FeedUiState(
        posts = emptyList(),
        postVotes = cachedStatuses.postVotes,
        pollVotes = cachedStatuses.pollVotes,
        likertVotes = cachedStatuses.likertVotes,
        pickVotes = cachedStatuses.pickVotes,
        isInitialLoading = false,
        advancedSearching = true,
        advancedMatches = cachedVisible.size,
        aliases = aliases,
    )
    runCatching {
        val indexedNewest = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { AdvancedSearchIndex.newest() }
        val indexedOldest = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { AdvancedSearchIndex.oldest() }
        val missingRanges = buildList {
            if (indexedOldest == null || indexedNewest == null) add(from to to)
            else {
                if (from.isBefore(indexedOldest)) add(from to indexedOldest.plusSeconds(1))
                val freshnessStart = maxOf(from, indexedNewest.minusSeconds(60))
                if (freshnessStart.isBefore(to)) add(freshnessStart to to)
            }
        }
        // Divide only missing time ranges among exactly twenty workers. Splitting
        // by time avoids workers racing over the same cursor chain.
        val ranges = missingRanges.flatMapIndexed { rangeIndex, (start, end) ->
            val seconds = java.time.Duration.between(start, end).seconds.coerceAtLeast(1L)
            val workers = 20 / missingRanges.size + if (rangeIndex < 20 % missingRanges.size) 1 else 0
            (0 until workers).map { index ->
                start.plusSeconds(seconds * index / workers) to start.plusSeconds(seconds * (index + 1) / workers)
            }
        }
        val live = cached.associateByTo(LinkedHashMap(), FeedPost::uuid)
        val votes = LinkedHashMap(cachedStatuses.postVotes); val pollVotes = LinkedHashMap(cachedStatuses.pollVotes)
        val likertVotes = LinkedHashMap(cachedStatuses.likertVotes); val pickVotes = LinkedHashMap(cachedStatuses.pickVotes)
        val pendingRawPosts = ArrayList<String>()
        val lock = kotlinx.coroutines.sync.Mutex()
        var scanned = 0
        var lastPublishedAt = 0L
        kotlinx.coroutines.coroutineScope {
            ranges.map { (rangeStart, rangeEnd) ->
                async(kotlinx.coroutines.Dispatchers.IO) {
                    var cursor: String? = Base64.encodeToString(JSONObject().put("created_at", rangeEnd.toString()).toString().toByteArray(), Base64.NO_WRAP)
                    var pages = 0
                    do {
                        val page = requestPage("All", "", cursor)
                        val reachedStart = page.posts.lastOrNull()?.createdAt?.let { runCatching { Instant.parse(it) }.getOrNull() }?.isBefore(rangeStart) == true
                        var publishPosts: List<FeedPost>? = null
                        var publishScanned = 0
                        lock.withLock {
                            pendingRawPosts.addAll(page.rawPosts)
                            page.posts.filter { post -> post.createdAt.let { runCatching { Instant.parse(it) }.getOrNull() }?.let { !it.isBefore(rangeStart) && !it.isAfter(rangeEnd) } == true }.forEach { live[it.uuid] = it }
                            votes.putAll(page.postVotes); pollVotes.putAll(page.pollVotes); likertVotes.putAll(page.likertVotes); pickVotes.putAll(page.pickVotes)
                            scanned += page.posts.size
                            val nowMs = System.currentTimeMillis()
                            // Throttle progress publication; parsing remains parallel on IO,
                            // while Compose receives at most one progress mutation per window.
                            if (nowMs - lastPublishedAt >= 420L) {
                                lastPublishedAt = nowMs
                                publishPosts = live.values.toList()
                                publishScanned = scanned
                            }
                        }
                        publishPosts?.let { snapshot ->
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                                if (generation == requestGeneration && activeKey == key) {
                                    advancedCorpus = snapshot
                                    state = state.copy(advancedSearching = true, advancedScanned = publishScanned)
                                }
                            }
                        }
                        cursor = page.nextCursor
                        pages++
                        if (reachedStart) break
                    } while (!cursor.isNullOrBlank() && pages < 160)
                }
            }.forEach { it.await() }
        }
        // Persist raw payloads and interaction status together so a process restart
        // cannot resurrect posts with apparently missing votes.
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { AdvancedSearchIndex.merge(pendingRawPosts, votes, pollVotes, likertVotes, pickVotes) }
        advancedCorpus = live.values.toList()
        advancedCorpusReady = true
        val current = advancedDisplayFilters
        val visible = current.sort(advancedCorpus.filter(current::matches).filterNot { mutedUsers.isMuted(it.authorUuid) })
        FeedUiState(posts = visible, postVotes = votes, pollVotes = pollVotes, likertVotes = likertVotes, pickVotes = pickVotes, aliases = aliases, isInitialLoading = false, hasMore = false, advancedSearching = false, advancedScanned = scanned, advancedMatches = visible.size)
    }.onSuccess { result -> if (generation == requestGeneration && activeKey == key) {
        state = result
        restoreResultSnapshots()
    } }
        .onFailure { error ->
            if (error is CancellationException) throw error
            if (generation == requestGeneration) state = state.copy(isInitialLoading = false, advancedSearching = false, error = error.message ?: "Advanced search failed")
        }
    return changed
}

internal fun FeedController.sortAdvanced(sort: SearchResultSort) {
    state = state.copy(posts = AdvancedSearchFilters(sort = sort).sort(state.posts))
}

package com.twocents.mobile.ui.profile

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.twocents.mobile.AuthState
import com.twocents.mobile.RpcApi
import com.twocents.mobile.ui.common.AppToast
import com.twocents.mobile.ui.common.friendlyError
import com.twocents.mobile.ui.compose.ComposeAuthorProfile
import com.twocents.mobile.ui.feed.AdvancedSearchFilters
import com.twocents.mobile.ui.feed.AdvancedSearchIndex
import com.twocents.mobile.ui.feed.FeedController
import com.twocents.mobile.ui.feed.FeedSource
import com.twocents.mobile.ui.feed.loadAdvanced
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

internal data class FollowersScanState(
    val running: Boolean = false,
    val phase: String = "",
    val completed: Int = 0,
    val total: Int = 0,
    val snapshot: FollowersScanSnapshot = FollowersScanSnapshot(),
    val error: String? = null,
    val pauseReason: String? = null,
) {
    val displayPhase: String get() = pauseReason ?: phase
}

/**
 * Process-lived follower discovery. Collection and hasMe verification are kept
 * outside composables so closing the sheet never interrupts a scan.
 */
internal object FollowersScanner {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val states = ConcurrentHashMap<String, MutableState<FollowersScanState>>()
    private val visibleSheets = ConcurrentHashMap.newKeySet<String>()
    private val toastIds = ConcurrentHashMap<String, Long>()
    private val lastToastAt = ConcurrentHashMap<String, Long>()

    fun state(context: Context, userUuid: String): FollowersScanState = states.getOrPut(userUuid) {
        mutableStateOf(FollowersScanState(snapshot = FollowersScanStore(context, userUuid).read()))
    }.value

    fun reloadStored(context: Context, userUuid: String) {
        if (states[userUuid]?.value?.running == true) return
        states.getOrPut(userUuid) { mutableStateOf(FollowersScanState()) }.value = FollowersScanState(snapshot = FollowersScanStore(context, userUuid).read())
    }

    /** Self-follow is known directly after saving; no discovery scan is necessary.
     * Uses the normal snapshot so backups and app restarts retain the entry. */
    suspend fun recordSelfFollow(context: Context, profile: ComposeAuthorProfile, alias: String?) {
        val snapshot = withContext(Dispatchers.IO) {
            FollowersScanStore(context, profile.uuid).modify { current ->
                val others = current.followers.filterNot { it.profile.uuid == profile.uuid }
                current.copy(followers = if (alias == null) others else others + FollowerEntry(alias, profile))
            }
        }
        update(profile.uuid) { it.copy(snapshot = snapshot) }
    }

    /** Shared by push and notification refresh; disk work never blocks notification UI. */
    fun recordNotifications(context: Context, userUuid: String, notifications: List<com.twocents.mobile.notifications.AppNotification>) {
        if (notifications.none { it.type == "followed" || it.type == "followed_by" }) return
        val appContext = context.applicationContext
        scope.launch {
            val snapshot = FollowersScanStore(appContext, userUuid).modify {
                it.withFollowNotifications(userUuid, notifications)
            }
            update(userUuid) { it.copy(snapshot = snapshot) }
        }
    }

    fun clearStored(context: Context, userUuid: String) {
        if (states[userUuid]?.value?.running == true) return
        FollowersScanStore(context, userUuid).clear()
        states.getOrPut(userUuid) { mutableStateOf(FollowersScanState()) }.value = FollowersScanState()
    }

    fun setSheetVisible(userUuid: String, visible: Boolean) {
        if (visible) {
            visibleSheets += userUuid
            toastIds.remove(userUuid)?.let(AppToast::dismiss)
        } else {
            visibleSheets -= userUuid
            states[userUuid]?.value?.takeIf { it.running }?.let { publishToast(userUuid, it) }
        }
    }

    /** Refreshes user-assigned aliases without rerunning the expensive discovery scan. */
    fun refreshAliases(context: Context, api: RpcApi, auth: AuthState) {
        scope.launch {
            val aliases = runCatching { api.call("/v1/aliases/get", JSONObject(), auth) as? JSONObject }
                .getOrNull()?.aliasMap().orEmpty()
            if (aliases.isEmpty()) return@launch
            val refreshed = FollowersScanStore(context, auth.userUuid).modify { current ->
                current.copy(followers = current.followers.map { entry ->
                    entry.copy(alias = aliases[entry.profile.uuid] ?: entry.alias)
                })
            }
            update(auth.userUuid) { it.copy(snapshot = refreshed) }
        }
    }

    fun start(context: Context, api: RpcApi, auth: AuthState) {
        if (state(context, auth.userUuid).running) return
        update(auth.userUuid) { it.copy(running = true, phase = "Finding users…", completed = 0, total = 0, error = null) }
        scope.launch {
            runCatching {
                withContext(ScanRequestPacer(onPause = { reason ->
                    update(auth.userUuid) { it.copy(pauseReason = reason) }
                })) { scan(context.applicationContext, api, auth) }
            }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    update(auth.userUuid) { it.copy(running = false, pauseReason = null, error = friendlyError(error, "Follower scan failed")) }
                    if (auth.userUuid !in visibleSheets) {
                        toastIds.remove(auth.userUuid)?.let { AppToast.error("Follower scan failed", it) }
                            ?: AppToast.error("Follower scan failed")
                    } else toastIds.remove(auth.userUuid)?.let(AppToast::dismiss)
                }
        }
    }

    private suspend fun scan(context: Context, api: RpcApi, auth: AuthState) {
        val scanStartedAt = System.currentTimeMillis()
        val candidates = LinkedHashSet<String>() // Deliberately case-sensitive.
        candidates += FollowersScanStore(context, auth.userUuid).read().candidates
        candidates += auth.userUuid
        val (rooms, dms, aliases, boards) = coroutineScope {
            listOf(
                async { api.call("/v2/rooms/listUserRooms", JSONObject(), auth) as? JSONObject },
                async { api.call("/v2/rooms/listUserDMs", JSONObject(), auth) as? JSONObject },
                async { api.call("/v1/aliases/get", JSONObject(), auth) as? JSONObject },
                async { api.call("/v1/leaderboard/all", JSONObject(), auth) as? JSONObject },
            ).awaitAll()
        }
        collectValues(aliases, setOf("for_uuid", "user_uuid"), candidates)
        val aliasByUuid = aliases.aliasMap()
        val roomUuids = LinkedHashSet<String>()
        listOf(rooms, dms).forEach { root ->
            root?.optJSONArray("rooms")?.let { rows -> for (i in 0 until rows.length()) rows.optJSONObject(i)?.let { room ->
                room.optString("uuid").takeIf(String::isNotBlank)?.let(roomUuids::add)
                collectValues(room.optJSONArray("members"), setOf("user_uuid"), candidates)
            } }
        }
        update(auth.userUuid) { it.copy(phase = "Checking room members…", completed = 0, total = roomUuids.size) }
        roomUuids.toList().chunked(6).forEachIndexed { batch, ids ->
            coroutineScope { ids.map { room -> async { retryRpc { api.call("/v1/rooms/getMembers", JSONObject().put("roomUuid", room), auth) } } }.awaitAll() }
                .forEach { collectValues(it, setOf("user_uuid"), candidates) }
            update(auth.userUuid) { it.copy(completed = ((batch + 1) * 6).coerceAtMost(roomUuids.size)) }
            delay(120)
        }
        val boardNames = boards?.optJSONArray("leaderboards")?.stringObjects("name") ?: emptyList()
        boards?.optJSONArray("leaderboards")?.let { rows ->
            for (index in 0 until rows.length()) {
                collectValues(rows.optJSONObject(index)?.optJSONArray("leaderboard"), setOf("uuid", "user_uuid"), candidates)
            }
        }
        update(auth.userUuid) { it.copy(phase = "Checking leaderboards…", completed = 0, total = boardNames.size) }
        boardNames.chunked(6).forEachIndexed { batch, names ->
            coroutineScope { names.map { name -> async { retryRpc { api.call("/v1/leaderboard/get", JSONObject().put("name", name), auth) } } }.awaitAll() }
                .forEach { root -> collectValues((root as? JSONObject)?.optJSONArray("leaderboard"), setOf("uuid", "user_uuid"), candidates) }
            update(auth.userUuid) { it.copy(completed = ((batch + 1) * 6).coerceAtMost(boardNames.size)) }
        }

        update(auth.userUuid) { it.copy(phase = "Updating the post index…", completed = 0, total = 20) }
        AdvancedSearchIndex.initialize(context)
        val search = withContext(Dispatchers.Main.immediate) { FeedController(api, auth, FeedSource.Arena, context) }
        withContext(Dispatchers.Main.immediate) {
            search.loadAdvanced(AdvancedSearchFilters(dateFrom = "2024-11-01"), force = true)
        }
        search.state.error?.let { error(it) } // Never publish a partial discovery as a successful full scan.
        withContext(Dispatchers.IO) { AdvancedSearchIndex.snapshot() }.forEach { candidates += it.authorUuid }
        candidates.remove(auth.userUuid)

        val store = FollowersScanStore(context, auth.userUuid)
        store.modify { previous ->
            candidates += previous.candidates
            candidates.remove(auth.userUuid)
            previous.copy(candidates = candidates.toList())
        }
        update(auth.userUuid) { it.copy(phase = "Checking who follows you…", completed = 0, total = candidates.size) }
        val followerUuids = ArrayList<String>()
        candidates.toList().chunked(12).forEachIndexed { batch, ids ->
            val found = coroutineScope {
                ids.map { uuid -> async {
                    val root = retryRpc { api.call("/v1/aliases/hasMe", JSONObject().put("authorUUID", uuid), auth) } as? JSONObject
                    // A malformed response is not evidence of an unfollow.
                    if (root?.opt("hasAlias") !is Boolean) error("Invalid follower lookup response. Your saved followers have been kept.")
                    uuid.takeIf { root.optBoolean("hasAlias") }
                } }.awaitAll().filterNotNull()
            }
            followerUuids += found
            val done = ((batch + 1) * 12).coerceAtMost(candidates.size)
            update(auth.userUuid) { it.copy(completed = done) }
            if (batch % 4 == 3) delay(240) else delay(80)
        }
        update(auth.userUuid) { it.copy(phase = "Loading follower details…", completed = 0, total = followerUuids.size) }
        val followers = ArrayList<FollowerEntry>()
        followerUuids.chunked(8).forEachIndexed { batch, ids ->
            followers += coroutineScope { ids.map { uuid -> async { loadFollower(api, auth, uuid, aliasByUuid[uuid]) } }.awaitAll().filterNotNull() }
            update(auth.userUuid) { it.copy(completed = ((batch + 1) * 8).coerceAtMost(followerUuids.size)) }
            delay(100)
        }
        val snapshot = store.modify { latest ->
            // A follow arriving during the scan must survive its final write. Older
            // followers absent from hasMe results are removed, but candidates are retained.
            val merged = followers.associateByTo(linkedMapOf()) { it.profile.uuid }
            // Discovery excludes the account itself; keep the latest explicit self-follow.
            latest.followers.firstOrNull { it.profile.uuid == auth.userUuid }?.let { merged[auth.userUuid] = it }
            latest.followers.filter { (latest.followEvents[it.profile.uuid] ?: 0L) > scanStartedAt }
                .forEach { merged[it.profile.uuid] = it }
            latest.copy(candidates = (candidates + latest.candidates).toList(),
                followers = merged.values.toList(), completedAt = scanStartedAt)
        }
        val foundCount = snapshot.followers.size
        update(auth.userUuid) { it.copy(running = false, phase = "Complete", snapshot = snapshot, completed = foundCount, total = foundCount) }
        if (auth.userUuid !in visibleSheets) {
            toastIds.remove(auth.userUuid)?.let { AppToast.success("Found $foundCount followers", it) }
                ?: AppToast.success("Found $foundCount followers")
        } else toastIds.remove(auth.userUuid)?.let(AppToast::dismiss)
    }

    private suspend fun loadFollower(api: RpcApi, auth: AuthState, uuid: String, assignedAlias: String?): FollowerEntry? {
        val root = retryRpc {
            api.call("/v2/users/get", JSONObject().put("user_uuid", uuid).put("posts_limit", 0).put("comments_limit", 0).put("voted_posts_limit", 0), auth)
        } as? JSONObject
        val user = root?.optJSONObject("user") ?: return null
        return FollowerEntry(
            assignedAlias ?: user.optString("systemAlias").takeUnless { it.isBlank() || it == "null" },
            ComposeAuthorProfile(uuid, user.optDouble("balance"), user.optInt("subscription_type"), user.optString("role").takeIf(String::isNotBlank), user.optString("gender").takeIf(String::isNotBlank), user.optInt("age").takeIf { user.has("age") && !user.isNull("age") }, user.optString("arena").takeIf(String::isNotBlank)),
        )
    }

    // Retries and pauses are coordinated by the inherited request policy.
    private suspend fun retryRpc(block: suspend () -> Any?): Any? = block()

    private fun update(uuid: String, transform: (FollowersScanState) -> FollowersScanState) {
        val holder = states.getOrPut(uuid) { mutableStateOf(FollowersScanState()) }
        val next = transform(holder.value)
        holder.value = next
        val now = System.currentTimeMillis()
        // Progress can advance hundreds of times during hasMe batching. Coalesce
        // off-sheet toast updates so the global UI host is never flooded.
        if (next.running && uuid !in visibleSheets && now - (lastToastAt[uuid] ?: 0L) >= 750L) {
            lastToastAt[uuid] = now
            publishToast(uuid, next)
        }
    }

    private fun publishToast(uuid: String, state: FollowersScanState) {
        val detail = if (state.total > 0) " ${state.completed}/${state.total}" else ""
        toastIds[uuid] = AppToast.progress(state.displayPhase + detail, toastIds[uuid])
    }
}

private fun JSONObject?.aliasMap(): Map<String, String> = buildMap {
    val rows = this@aliasMap?.optJSONArray("aliases") ?: return@buildMap
    for (index in 0 until rows.length()) rows.optJSONObject(index)?.let { row ->
        val uuid = row.optString("for_uuid").ifBlank { row.optJSONObject("user")?.optString("uuid").orEmpty() }
        val alias = row.optString("alias")
        if (uuid.isNotBlank() && alias.isNotBlank() && !alias.equals("null", true)) put(uuid, alias)
    }
}

private fun collectValues(value: Any?, keys: Set<String>, output: MutableSet<String>) {
    when (value) {
        is JSONObject -> value.keys().forEach { key ->
            val child = value.opt(key)
            if (key in keys && child is String && child.isNotBlank()) output += child
            collectValues(child, keys, output)
        }
        is JSONArray -> for (index in 0 until value.length()) collectValues(value.opt(index), keys, output)
    }
}

private fun JSONArray.stringObjects(key: String): List<String> = buildList {
    for (index in 0 until length()) optJSONObject(index)?.optString(key)?.takeIf(String::isNotBlank)?.let(::add)
}

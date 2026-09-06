package com.twocents.mobile.ui.leaderboard

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.twocents.mobile.AuthState
import com.twocents.mobile.RpcApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject

@Immutable
internal data class LeaderboardBoard(
    val uuid: String,
    val apiName: String,
    val label: String,
    val description: String,
    val pointsFormat: String,
    val embedded: List<LeaderboardEntry> = emptyList(),
) {
    val hasExtra: Boolean get() = apiName != "top100" && apiName != "league"
    val extraLabel: String get() = when (apiName) {
        "highestDebt" -> "Debt"
        "Biggest Gains" -> "Gain"
        "Biggest Losses" -> "Loss"
        else -> "Points"
    }
}

@Immutable
internal data class LeaderboardEntry(
    val uuid: String,
    val balance: Double,
    val subscriptionType: Int,
    val role: String?,
    val elo: Int,
    val gender: String?,
    val age: Int?,
    val arena: String?,
    val bio: String?,
    val points: Double?,
    val apiRank: Int?,
)

@Stable
internal class LeaderboardController(private val api: RpcApi, private val auth: AuthState) {
    var boards by mutableStateOf<List<LeaderboardBoard>>(emptyList())
        private set
    var selected by mutableStateOf<LeaderboardBoard?>(null)
        private set
    var entries by mutableStateOf<List<LeaderboardEntry>>(emptyList())
        private set
    var myPosition by mutableIntStateOf(0)
        private set
    var totalPositions by mutableIntStateOf(0)
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private val cache = mutableMapOf<String, List<LeaderboardEntry>>()

    suspend fun load(force: Boolean = false): Boolean {
        if (!force && boards.isNotEmpty()) return true
        loading = true
        error = null
        return runCatching {
            coroutineScope {
                val allRequest = async { api.call("/v1/leaderboard/all", JSONObject(), auth) as? JSONObject }
                val loginRequest = async {
                    api.call(
                        "/v2/auth/login",
                        JSONObject().put("version", "web-v0.1.3").put("secret_key", auth.secretKey),
                        auth,
                    ) as? JSONObject
                }
                val catalog = parseBoards(allRequest.await())
                val login = loginRequest.await()?.optJSONObject("leaderboard")
                myPosition = login?.optInt("myPosition") ?: 0
                totalPositions = login?.optInt("totalPositions") ?: 0
                login?.optJSONArray("top100")?.let { rows ->
                    if (rows.length() > 0) cache["top100"] = parseEntries(rows)
                }
                boards = catalog
                selected = catalog.firstOrNull { it.apiName == selected?.apiName }
                    ?: catalog.firstOrNull { it.apiName == "top100" }
                    ?: catalog.firstOrNull()
                selected?.let { loadBoard(it, force) } ?: run { entries = emptyList() }
            }
            loading = false
            true
        }.getOrElse { failure ->
            if (failure is CancellationException) throw failure
            loading = false
            error = failure.message ?: "Couldn't load leaderboards"
            false
        }
    }

    suspend fun select(board: LeaderboardBoard) {
        if (selected?.apiName == board.apiName) return
        selected = board
        error = null
        val cached = cache[board.apiName]
        if (cached != null) {
            entries = cached
            return
        }
        entries = emptyList()
        loading = true
        runCatching { loadBoard(board, false) }
            .onFailure { failure ->
                if (failure is CancellationException) throw failure
                error = failure.message ?: "Couldn't load leaderboard"
            }
        loading = false
    }

    suspend fun refresh(): Boolean = load(true)

    private suspend fun loadBoard(board: LeaderboardBoard, force: Boolean) {
        if (!force) cache[board.apiName]?.let { entries = it; return }
        val loaded = if (board.embedded.isNotEmpty()) board.embedded else {
            val root = api.call("/v1/leaderboard/get", JSONObject().put("name", board.apiName), auth) as? JSONObject
                ?: error("Invalid leaderboard response")
            parseEntries(root.optJSONArray("leaderboard"))
        }
        cache[board.apiName] = loaded
        entries = loaded
    }
}

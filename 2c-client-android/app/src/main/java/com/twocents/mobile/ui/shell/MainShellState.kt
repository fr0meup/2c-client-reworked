package com.twocents.mobile.ui.shell

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.twocents.mobile.notifications.NotificationFilter
import com.twocents.mobile.ui.common.PullToRefreshState
import com.twocents.mobile.ui.common.rememberPullToRefreshState
import com.twocents.mobile.ui.compose.ComposeAuthorProfile
import com.twocents.mobile.ui.feed.AdvancedSearchFilters
import com.twocents.mobile.ui.feed.FeedPost
import com.twocents.mobile.ui.messages.RoomSummary
import com.twocents.mobile.ui.navigation.AppTab

/**
 * Mutable state owned by the app shell. This is deliberately UI-only: network
 * and feature state stays in its existing controllers, preserving their scopes
 * and avoiding a second source of truth during navigation.
 */
@Stable
internal class MainShellState(
    authUuid: String,
    val refreshState: PullToRefreshState,
    val notificationRefreshState: PullToRefreshState,
    val bookmarkRefreshState: PullToRefreshState,
    val profileRefreshState: PullToRefreshState,
    val messagesRefreshState: PullToRefreshState,
    val feedListState: LazyListState,
    val notificationListState: LazyListState,
    val bookmarkListState: LazyListState,
    val messagesListState: LazyListState,
) {
    var selectedTab by mutableStateOf(AppTab.Feed)
    var tabBackStack by mutableStateOf(listOf(AppTab.Feed))
    var suppressNextTabHistory by mutableStateOf(false)
    var activeTopic by mutableStateOf("New")
    var topicDropdownOpen by mutableStateOf(false)
    var searchQuery by mutableStateOf("")
    var advancedSearchOpen by mutableStateOf(false)
    var advancedSearchFilters by mutableStateOf<AdvancedSearchFilters?>(null)
    var advancedSearchDraft by mutableStateOf(AdvancedSearchFilters())
    var advancedSearchSessionActive by mutableStateOf(false)
    var profileRequested by mutableStateOf(false)
    var accountSidebarOpen by mutableStateOf(false)
    var profileTargetUuid by mutableStateOf(authUuid)
    var viewedProfile by mutableStateOf<ComposeAuthorProfile?>(null)
    var profileScrollToTopRequest by mutableStateOf(0)
    var composeModalOpen by mutableStateOf(false)
    var composeInitialTopic by mutableStateOf("Lounge")
    var composeQuotedPost by mutableStateOf<FeedPost?>(null)
    var notificationFilter by mutableStateOf(NotificationFilter.All)
    var composeAuthorProfile by mutableStateOf<ComposeAuthorProfile?>(null)
    var editProfileOpen by mutableStateOf(false)
    var openedPost by mutableStateOf<OpenedPost?>(null)
    var overlayStack by mutableStateOf<List<ShellOverlayEntry>>(emptyList())
    var exitingOverlayId by mutableStateOf<Long?>(null)
    var nextOverlayId by mutableStateOf(1L)
    var profileOverPost by mutableStateOf(false)
    var profileWasUnderPost by mutableStateOf(false)
    var profileUnderPostUuid by mutableStateOf<String?>(null)
    var profileUnderPostSeed by mutableStateOf<ComposeAuthorProfile?>(null)
    var openedRoom by mutableStateOf<RoomSummary?>(null)
    var openedRoomTargetMessageUuid by mutableStateOf<String?>(null)
    var accountSidebarDragFraction by mutableFloatStateOf(0f)
    var accountSidebarEdgeProgress by mutableFloatStateOf(0f)
    var accountSidebarEdgeHolding by mutableStateOf(false)
    var feedChromeProgress by mutableFloatStateOf(0f)
    var secondaryNavProgress by mutableFloatStateOf(0f)
    var suppressFeedActionUntilMs by mutableStateOf(0L)
    var exploreRoomsOpen by mutableStateOf(false)
    var exitHintVisible by mutableStateOf(false)
    var lastExitBackAt by mutableStateOf(0L)
    var roomActionBusy by mutableStateOf(false)
    var profileReturnAnchor by mutableStateOf<ProfileReturnAnchor?>(null)
    var preserveNotificationPositionOnProfileReturn by mutableStateOf(false)
}

@Composable
internal fun rememberMainShellState(authUuid: String): MainShellState {
    val refreshState = rememberPullToRefreshState()
    val notificationRefreshState = rememberPullToRefreshState()
    val bookmarkRefreshState = rememberPullToRefreshState()
    val profileRefreshState = rememberPullToRefreshState()
    val messagesRefreshState = rememberPullToRefreshState()
    val feedListState = rememberLazyListState()
    val notificationListState = rememberLazyListState()
    val bookmarkListState = rememberLazyListState()
    val messagesListState = rememberLazyListState()
    val state = remember {
        MainShellState(
            authUuid,
            refreshState,
            notificationRefreshState,
            bookmarkRefreshState,
            profileRefreshState,
            messagesRefreshState,
            feedListState,
            notificationListState,
            bookmarkListState,
            messagesListState,
        )
    }
    // The original shell keyed only identity-dependent profile state to the
    // account UUID. Other navigation state intentionally survives recomposition.
    LaunchedEffect(authUuid) {
        state.profileTargetUuid = authUuid
        state.composeAuthorProfile = null
    }
    return state
}

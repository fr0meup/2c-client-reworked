package com.twocents.mobile.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.twocents.mobile.AuthState
import com.twocents.mobile.RpcApi
import com.twocents.mobile.notifications.*
import com.twocents.mobile.ui.common.PullToRefreshContainer
import com.twocents.mobile.ui.common.PullToRefreshState
import com.twocents.mobile.ui.compose.ComposeAuthorProfile
import com.twocents.mobile.ui.feed.*
import com.twocents.mobile.ui.messages.*
import com.twocents.mobile.ui.profile.UserProfileContent
import com.twocents.mobile.ui.theme.Background

/** Common frame for non-feed scenes; the profile blocker prevents click-through during transitions. */
@Composable
private fun SecondarySceneFrame(
    blocksUnderlyingTouches: Boolean,
    header: @Composable () -> Unit,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(Background).clickable(
            enabled = blocksUnderlyingTouches,
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {},
        ),
    ) {
        header()
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = contentAlignment, content = content)
    }
}

@Composable
internal fun ProfileShellScene(
    auth: AuthState,
    api: RpcApi,
    targetUuid: String,
    viewedProfile: ComposeAuthorProfile?,
    ownProfile: ComposeAuthorProfile?,
    scrollToTopRequest: Int,
    refreshState: PullToRefreshState,
    feedController: FeedController,
    isFollowing: Boolean,
    onBack: () -> Unit,
    onNetworthClick: () -> Unit,
    onEditProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleFollow: (String?) -> Unit,
    onMessage: () -> Unit,
    onBlock: () -> Unit,
    onProfileLoaded: (ComposeAuthorProfile) -> Unit,
    onOpenPost: (FeedPost) -> Unit,
    onOpenCommentPost: (String, String) -> Unit,
    onOpenProfile: (String, ComposeAuthorProfile) -> Unit,
    onQuotePost: (FeedPost) -> Unit,
    onOpenMessages: () -> Unit,
    onNotificationsFallback: suspend () -> Unit,
    navigationActive: Boolean,
) = SecondarySceneFrame(
    blocksUnderlyingTouches = true,
    header = {
        ProfilePageHeader(
            profile = viewedProfile ?: ownProfile.takeIf { targetUuid == auth.userUuid },
            authUuid = targetUuid,
            isOwn = targetUuid == auth.userUuid,
            onBack = onBack,
            onNetworthClick = onNetworthClick,
            onEditProfile = onEditProfile,
            onOpenSettings = onOpenSettings,
            isFollowing = isFollowing,
            onToggleFollow = onToggleFollow,
            onMessage = onMessage,
            onBlock = onBlock,
            refreshing = refreshState.isRefreshing,
            modifier = Modifier.statusBarsPadding(),
        )
    },
) {
    UserProfileContent(
        auth = auth, api = api, targetUuid = targetUuid, scrollToTopRequest = scrollToTopRequest,
        refreshState = refreshState, feedController = feedController, onProfileLoaded = onProfileLoaded,
        onOpenPost = onOpenPost, onOpenCommentPost = onOpenCommentPost, onOpenProfile = onOpenProfile,
        onQuotePost = onQuotePost, onOpenMessages = onOpenMessages,
        onNotificationsFallback = onNotificationsFallback,
        navigationActive = navigationActive,
    )
}

@Composable
internal fun MessagesShellScene(
    authUuid: String,
    controller: MessagesController,
    aliases: Map<String, String>,
    refreshState: PullToRefreshState,
    listState: LazyListState,
    bottomNavigationHeight: Dp,
    profileSelected: Boolean,
    roomActionBusy: Boolean,
    onPressLogo: () -> Unit,
    onNavigateProfile: () -> Unit,
    onExplore: () -> Unit,
    onJoinCode: (String) -> Unit,
    onCreateRoom: () -> Unit,
    onRefresh: suspend () -> Boolean,
    onOpenRoom: (RoomSummary) -> Unit,
) = SecondarySceneFrame(false, header = {
    MessagesPageHeader(
        onPressLogo = onPressLogo, onNavigateProfile = onNavigateProfile,
        profileSelected = profileSelected, onExplore = onExplore, onJoinCode = onJoinCode,
        onCreateRoom = onCreateRoom, busy = roomActionBusy, refreshing = refreshState.isRefreshing,
        modifier = Modifier.statusBarsPadding(),
    )
}) {
    PullToRefreshContainer(
        state = refreshState,
        enabled = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0,
        onRefresh = onRefresh,
    ) {
        MessagesContent(
            controller = controller, authUuid = authUuid, aliases = aliases,
            bottomContentPadding = bottomNavigationHeight + 18.dp, listState = listState,
            onOpenRoom = onOpenRoom,
        )
    }
}

@Composable
internal fun NotificationsShellScene(
    controller: NotificationController,
    filter: NotificationFilter,
    refreshState: PullToRefreshState,
    listState: LazyListState,
    bottomNavigationHeight: Dp,
    profileSelected: Boolean,
    scrollConnection: NestedScrollConnection,
    onPressLogo: () -> Unit,
    onNavigateProfile: () -> Unit,
    onSelectFilter: (NotificationFilter) -> Unit,
    onMarkAllRead: () -> Unit,
    onRefresh: suspend () -> Boolean,
    onOpenNotification: (AppNotification) -> Unit,
) = SecondarySceneFrame(false, header = {
    NotificationsPageHeader(
        onPressLogo = onPressLogo, onNavigateProfile = onNavigateProfile,
        profileSelected = profileSelected, unreadCount = controller.state.unreadCount,
        replyCount = controller.state.replyCount, filter = filter, onSelectFilter = onSelectFilter,
        onMarkAllRead = onMarkAllRead, refreshing = refreshState.isRefreshing,
        modifier = Modifier.statusBarsPadding(),
    )
}) {
    PullToRefreshContainer(
        state = refreshState,
        enabled = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0,
        onRefresh = onRefresh,
    ) {
        NotificationsContent(
            controller = controller, filter = filter, listState = listState,
            onOpenNotification = onOpenNotification,
            modifier = Modifier.nestedScroll(scrollConnection),
            bottomContentPadding = bottomNavigationHeight + 18.dp,
        )
    }
}

@Composable
internal fun BookmarksShellScene(
    authUuid: String,
    controller: FeedController,
    refreshState: PullToRefreshState,
    listState: LazyListState,
    bottomNavigationHeight: Dp,
    profileSelected: Boolean,
    scrollConnection: NestedScrollConnection,
    onPressLogo: () -> Unit,
    onNavigateProfile: () -> Unit,
    onRefresh: suspend () -> Boolean,
    onOpenPost: (FeedPost) -> Unit,
    onQuotePost: (FeedPost) -> Unit,
    onOpenMessages: () -> Unit,
) = SecondarySceneFrame(false, header = {
    BookmarksPageHeader(
        onPressLogo = onPressLogo, onNavigateProfile = onNavigateProfile,
        profileSelected = profileSelected, refreshing = refreshState.isRefreshing,
        modifier = Modifier.statusBarsPadding(),
    )
}) {
    PullToRefreshContainer(
        state = refreshState,
        enabled = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0,
        onRefresh = onRefresh,
    ) {
        FeedContent(
            controller = controller, topic = "Bookmarks", searchQuery = "", authUuid = authUuid,
            listState = listState, bottomContentPadding = bottomNavigationHeight + 18.dp,
            chromeScrollConnection = scrollConnection, emptyTitle = "No bookmarks yet",
            emptyMessage = "Saved posts will appear here.", onOpenPost = onOpenPost,
            onQuotePost = onQuotePost, onOpenMessages = onOpenMessages,
        )
    }
}

@Composable
internal fun TransactionsShellScene(
    profileSelected: Boolean,
    onPressLogo: () -> Unit,
    onNavigateProfile: () -> Unit,
) = SecondarySceneFrame(false, header = {
    TransactionsPageHeader(
        onPressLogo = onPressLogo,
        onNavigateProfile = onNavigateProfile,
        profileSelected = profileSelected,
        modifier = Modifier.statusBarsPadding(),
    )
}, contentAlignment = Alignment.Center) { TransactionsPlaceholder() }

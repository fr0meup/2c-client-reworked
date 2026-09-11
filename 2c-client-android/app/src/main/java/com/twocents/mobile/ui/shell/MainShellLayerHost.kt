package com.twocents.mobile.ui.shell

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import com.twocents.mobile.AuthState
import com.twocents.mobile.RpcApi
import com.twocents.mobile.notifications.NotificationController
import com.twocents.mobile.ui.compose.createComposePost
import com.twocents.mobile.ui.compose.launchPostPublishFollowUp
import com.twocents.mobile.ui.common.notifyMentions
import com.twocents.mobile.ui.common.AppToast
import com.twocents.mobile.ui.feed.FeedController
import com.twocents.mobile.ui.feed.loadFeedPost
import com.twocents.mobile.ui.messages.MessagesController
import com.twocents.mobile.ui.messages.RoomSummary
import com.twocents.mobile.ui.navigation.AppTab
import com.twocents.mobile.ui.profile.EditProfileSheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Composes shell chrome and overlays above the active scene. Their ordering is
 * intentional: primary modals, sidebar, retained destinations, then edit profile.
 */
@Composable
internal fun BoxScope.MainShellLayerHost(
    context: Context,
    state: MainShellState,
    auth: AuthState,
    api: RpcApi,
    feedController: FeedController,
    bookmarkController: FeedController,
    notificationController: NotificationController,
    messagesController: MessagesController,
    backdropLayer: GraphicsLayer,
    accountSidebarWidthPx: Float,
    accountSidebarVisualProgress: Float,
    bottomNavigationHeightPx: Float,
    feedHeaderHeight: Dp,
    bottomNavigationHeight: Dp,
    nearTopPx: Int,
    scope: CoroutineScope,
    refreshOwnProfile: suspend () -> Unit,
    selectFeedTopic: (String) -> Unit,
    scrollFeedToTop: (Boolean) -> Unit,
    returnToFeedWithoutAction: () -> Unit,
    navigateProfile: () -> Unit,
    popOverlay: () -> Unit,
    pushPost: (OpenedPost) -> Unit,
    pushProfile: (String, com.twocents.mobile.ui.compose.ComposeAuthorProfile?) -> Unit,
    openQuotePost: (com.twocents.mobile.ui.feed.FeedPost) -> Unit,
    openMessagesFromPostMenu: () -> Unit,
    onLogout: () -> Unit,
) = with(state) {
    val feedVisible = selectedTab == AppTab.Feed && !profileRequested
    ShellChromeLayer(
        selectedTab = selectedTab,
        showNavigation = !profileRequested && openedRoom == null && overlayStack.isEmpty(),
        backdropLayer = backdropLayer,
        unreadNotifications = notificationController.state.unreadCount,
        unreadMessages = messagesController.state.unreadCount,
        accountSidebarWidthPx = accountSidebarWidthPx,
        accountSidebarProgress = accountSidebarVisualProgress,
        bottomNavigationHeightPx = bottomNavigationHeightPx,
        navigationHideProgress = when {
            feedVisible -> feedChromeProgress
            selectedTab == AppTab.Notifications || selectedTab == AppTab.Bookmarks -> secondaryNavProgress
            else -> 0f
        },
        onSelectTab = { tab ->
            val feedRetapped = tab == AppTab.Feed && selectedTab == AppTab.Feed && !profileRequested
            val notificationsRetapped = tab == AppTab.Notifications && selectedTab == AppTab.Notifications && !profileRequested
            val bookmarksRetapped = tab == AppTab.Bookmarks && selectedTab == AppTab.Bookmarks && !profileRequested
            topicDropdownOpen = false
            when {
                feedRetapped -> if (SystemClock.elapsedRealtime() >= suppressFeedActionUntilMs) scrollFeedToTop(true)
                notificationsRetapped -> scope.launch {
                    notificationListState.animateScrollToItem(0)
                    notificationRefreshState.requestRefresh()
                }
                bookmarksRetapped -> scope.launch {
                    bookmarkListState.animateScrollToItem(0)
                    bookmarkRefreshState.requestRefresh()
                }
                tab == AppTab.Feed -> returnToFeedWithoutAction()
                else -> {
                    feedChromeProgress = 0f
                    if (tab == AppTab.Notifications) notificationListState.requestScrollToItem(0)
                    selectedTab = tab
                    profileRequested = false
                    if (tab == AppTab.Bookmarks) scope.launch { bookmarkController.refresh("Bookmarks", "") }
                }
            }
        },
        topicDropdownOpen = topicDropdownOpen,
        activeTopic = activeTopic,
        onDismissTopicDropdown = { topicDropdownOpen = false },
        onSelectTopic = { topicDropdownOpen = false; selectFeedTopic(it) },
        exitHintVisible = exitHintVisible,
        showComposeFab = selectedTab == AppTab.Feed && !profileRequested && !topicDropdownOpen &&
            openedRoom == null && overlayStack.isEmpty(),
        composeFabHideProgress = maxOf(feedChromeProgress, accountSidebarVisualProgress),
        onComposePost = {
            if (feedChromeProgress < .82f) {
                composeQuotedPost = null
                composeInitialTopic = "Lounge"
                composeModalOpen = true
            }
        },
    )

    PrimaryOverlayLayer(
        composeVisible = composeModalOpen,
        composeProfile = composeAuthorProfile,
        composeQuotedPost = composeQuotedPost,
        composeInitialTopic = composeInitialTopic,
        mentionAliases = feedController.state.aliases,
        auth = auth,
        api = api,
        onDismissCompose = { composeModalOpen = false; composeQuotedPost = null },
        onSubmitPost = { draft ->
            val wasAtTopOfNew = selectedTab == AppTab.Feed && activeTopic == "New" &&
                feedListState.firstVisibleItemIndex == 0 && feedListState.firstVisibleItemScrollOffset <= nearTopPx
            val postedPost = createComposePost(api, auth, draft, context)
            if (postedPost != null) {
                // The create response is the publication boundary. Notifications,
                // refreshes and UI frame work cannot change that success to false.
                val background = com.twocents.mobile.ui.common.AppBackgroundTasks.mutations
                background.launchPostPublishFollowUp("Post published, but mentions couldn't be sent") {
                    notifyMentions(api, auth, draft.body, postedPost.uuid, contentType = "post")
                }
                background.launchPostPublishFollowUp("Post published, but your profile couldn't refresh") {
                    refreshOwnProfile()
                }
                composeQuotedPost = null
                val submittedTopic = activeTopic
                val submittedQuery = searchQuery
                scope.launchPostPublishFollowUp("Post published, but the feed couldn't refresh") {
                    val refreshed = feedController.refresh(submittedTopic, submittedQuery)
                    if (!refreshed) error("Feed refresh failed")
                    if (wasAtTopOfNew && selectedTab == AppTab.Feed &&
                        activeTopic == submittedTopic && searchQuery == submittedQuery) {
                        withFrameNanos { }
                        feedListState.scrollToItem(0)
                    }
                }
                scope.launchPostPublishFollowUp("Post published, but couldn't open its page") {
                    if (!wasAtTopOfNew) pushPost(OpenedPost(postedPost, feedController))
                }
            }
            postedPost != null
        },
        openedPost = openedPost,
        profileOverPost = profileOverPost,
        onClosePost = {
            openedPost = null
            profileOverPost = false
            profileUnderPostUuid?.let { previousUuid ->
                profileTargetUuid = previousUuid
                viewedProfile = profileUnderPostSeed
                profileUnderPostUuid = null
                profileUnderPostSeed = null
            }
            if (!profileWasUnderPost && profileRequested) {
                profileRequested = false
                viewedProfile = null
            }
            profileWasUnderPost = false
        },
        onOpenPost = { openedPost = it },
        onQuotePost = openQuotePost,
        onOpenMessages = openMessagesFromPostMenu,
        onNotificationsFallback = { notificationController.load(force = true) },
        openedRoom = openedRoom,
        roomTargetMessageUuid = openedRoomTargetMessageUuid,
        roomAliases = feedController.state.aliases,
        roomNavigationEnabled = !profileRequested && overlayStack.isEmpty(),
        roomHiddenByProfile = profileRequested,
        onCloseRoom = {
            openedRoom = null
            openedRoomTargetMessageUuid = null
            scope.launch { messagesController.load(force = true) }
        },
        exploreRoomsOpen = exploreRoomsOpen,
        messagesController = messagesController,
        onDismissExploreRooms = { exploreRoomsOpen = false },
        onOpenExploredRoom = { room ->
            exploreRoomsOpen = false
            messagesController.markOpened(room.uuid)
            openedRoom = room
        },
    )

    MainShellSidebarLayer(
        accountSidebarOpen = accountSidebarOpen,
        navigationActive = overlayStack.isEmpty() && !profileRequested && openedRoom == null,
        feedHeaderHeight = feedHeaderHeight,
        bottomNavigationHeight = bottomNavigationHeight,
        accountSidebarWidthPx = accountSidebarWidthPx,
        accountSidebarVisualProgress = accountSidebarVisualProgress,
        accountSidebarEdgeProgress = accountSidebarEdgeProgress,
        onEdgeProgressChanged = { accountSidebarEdgeProgress = it },
        onEdgeHoldingChanged = { accountSidebarEdgeHolding = it },
        onOpenChanged = { accountSidebarOpen = it },
        onDragFractionChange = { accountSidebarDragFraction = it },
        profile = composeAuthorProfile,
        auth = auth,
        api = api,
        alias = feedController.state.aliases[auth.userUuid],
        onOpenProfile = { accountSidebarOpen = false; navigateProfile() },
        notificationRevision = notificationController.state.notifications.hashCode(),
        recentRooms = messagesController.state.dms.distinctBy { it.uuid }.sortedByDescending { it.lastMessageAt },
        onOpenActivity = { postUuid, commentUuid ->
            scope.launch {
                loadFeedPost(api, auth, postUuid)?.let { post ->
                    accountSidebarOpen = false
                    pushPost(OpenedPost(post, feedController, commentUuid))
                }
            }
        },
        onOpenRoom = { room ->
            accountSidebarOpen = false
            overlayStack = emptyList()
            exitingOverlayId = null
            messagesController.markOpened(room.uuid)
            openedPost = null
            profileRequested = false
            selectedTab = AppTab.Messages
            openedRoom = room
        },
        onOpenRoomsOverview = {
            accountSidebarOpen = false
            overlayStack = emptyList()
            exitingOverlayId = null
            openedPost = null
            openedRoom = null
            profileRequested = false
            selectedTab = AppTab.Messages
        },
        onOpenLeaderboard = {
            accountSidebarOpen = false
            overlayStack = overlayStack + ShellOverlayEntry.Leaderboard(nextOverlayId++)
        },
        onOpenSettings = {
            accountSidebarOpen = false
            overlayStack = overlayStack + ShellOverlayEntry.Settings(nextOverlayId++)
        },
        onOfflineModeChanged = notificationController::setAppearOffline,
        onLogout = onLogout,
    )

    RetainedOverlayHost(
        stack = overlayStack,
        exitingId = exitingOverlayId,
        auth = auth,
        api = api,
        feedController = feedController,
        onPop = popOverlay,
        onPushPost = pushPost,
        onPushProfile = pushProfile,
        onQuotePost = openQuotePost,
        onOpenMessages = openMessagesFromPostMenu,
        onMessageUser = { userUuid ->
            scope.launch {
                val roomUuid = feedController.startDirectMessage(userUuid)
                if (roomUuid == null) {
                    AppToast.error("Couldn't start this conversation")
                    return@launch
                }
                val room = messagesController.resolveRoom(roomUuid) ?: RoomSummary(
                    uuid = roomUuid, name = "DM", description = "", roomType = "dm",
                    roomCode = null, isPrivate = true, gradients = emptyList(), unread = 0,
                    memberCount = 2, lastMessage = "", lastMessageAt = "", members = emptyList(),
                )
                messagesController.markOpened(room.uuid)
                openedPost = null
                profileRequested = false
                selectedTab = AppTab.Messages
                openedRoomTargetMessageUuid = null
                openedRoom = room
                overlayStack = emptyList()
                exitingOverlayId = null
            }
        },
        onOpenSidebar = { accountSidebarOpen = true },
        profileSelected = accountSidebarVisualProgress > .001f,
        onPushSettings = { overlayStack = overlayStack + ShellOverlayEntry.Settings(nextOverlayId++) },
        onOfflineChanged = notificationController::setAppearOffline,
        onOpenFeedback = {
            popOverlay()
            scope.launch {
                delay(185)
                composeQuotedPost = null
                composeInitialTopic = "Bugs and feedback"
                composeModalOpen = true
            }
        },
        onLogout = onLogout,
        onNotificationsFallback = { notificationController.load(force = true) },
        modifier = Modifier.fillMaxSize().zIndex(200f),
    )
    if (editProfileOpen) {
        EditProfileSheet(
            auth = auth,
            api = api,
            seed = viewedProfile ?: composeAuthorProfile,
            onDismiss = { editProfileOpen = false },
            onSaved = { updated -> viewedProfile = updated; composeAuthorProfile = updated },
        )
    }
}

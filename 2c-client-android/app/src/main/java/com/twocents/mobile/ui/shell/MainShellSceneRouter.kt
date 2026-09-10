package com.twocents.mobile.ui.shell

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.unit.Dp
import com.twocents.mobile.AuthState
import com.twocents.mobile.RpcApi
import com.twocents.mobile.notifications.NotificationController
import com.twocents.mobile.ui.common.AppToast
import com.twocents.mobile.ui.feed.AdvancedSearchFilters
import com.twocents.mobile.ui.feed.FeedController
import com.twocents.mobile.ui.feed.beginAdvancedSearchSession
import com.twocents.mobile.ui.feed.loadAdvanced
import com.twocents.mobile.ui.feed.loadFeedPost
import com.twocents.mobile.ui.feed.sortAdvanced
import com.twocents.mobile.ui.feed.prepareAdvancedSearch
import com.twocents.mobile.ui.messages.MessagesController
import com.twocents.mobile.ui.messages.RoomNavigationBus
import com.twocents.mobile.ui.messages.RoomSummary
import com.twocents.mobile.ui.navigation.AppTab
import com.twocents.mobile.ui.profile.ProfileAvailability
import com.twocents.mobile.ui.profile.ProfileNavigationBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Selects and wires the one primary tab scene. This contains scene-specific
 * actions only; chrome, retained overlays and sidebar layers are composed by
 * their own hosts so their animation boundaries remain unchanged.
 */
@Composable
internal fun MainShellSceneRouter(
    state: MainShellState,
    scene: ShellScene,
    auth: AuthState,
    api: RpcApi,
    feedController: FeedController,
    bookmarkController: FeedController,
    notificationController: NotificationController,
    messagesController: MessagesController,
    feedHeaderHeight: Dp,
    bottomNavigationHeight: Dp,
    feedHeaderHeightPx: Float,
    accountSidebarVisualProgress: Float,
    feedScrollConnection: NestedScrollConnection,
    secondaryScrollConnection: NestedScrollConnection,
    backdropLayer: GraphicsLayer,
    scope: CoroutineScope,
    refreshOwnProfile: suspend () -> Unit,
    selectFeedTopic: (String) -> Unit,
    navigateFeed: () -> Unit,
    openAccountSidebar: () -> Unit,
    closeProfile: () -> Unit,
    pushPost: (OpenedPost) -> Unit,
    pushProfile: (String, com.twocents.mobile.ui.compose.ComposeAuthorProfile?) -> Unit,
    openQuotePost: (com.twocents.mobile.ui.feed.FeedPost) -> Unit,
    openMessagesFromPostMenu: () -> Unit,
) = with(state) {
    SingleSceneSlideHost(
        targetState = scene,
        modifier = Modifier.fillMaxSize().drawWithContent {
            backdropLayer.record { this@drawWithContent.drawContent() }
            drawLayer(backdropLayer)
        },
    ) { visibleScene ->
        val sceneIsFeed = visibleScene.tab == AppTab.Feed && !visibleScene.profileRequested
        if (sceneIsFeed) {
            FeedShellScene(
                refreshState = refreshState,
                listState = feedListState,
                headerHeight = feedHeaderHeight,
                bottomNavigationHeight = bottomNavigationHeight,
                headerHeightPx = feedHeaderHeightPx,
                chromeProgress = feedChromeProgress,
                chromeScrollConnection = feedScrollConnection,
                controller = feedController,
                activeTopic = activeTopic,
                searchQuery = searchQuery,
                advancedFilters = advancedSearchFilters,
                advancedSearchOpen = advancedSearchOpen,
                advancedSearchDraft = advancedSearchDraft,
                advancedSessionActive = advancedSearchSessionActive,
                topicDropdownOpen = topicDropdownOpen,
                profileSelected = accountSidebarVisualProgress > .001f,
                authUuid = auth.userUuid,
                onRefresh = {
                    val startedAt = System.nanoTime()
                    val succeeded = advancedSearchFilters?.let {
                        feedController.loadAdvanced(it, force = true)
                        // Refresh the first results page, not thousands of indexed
                        // posts. Remaining cards revalidate when they enter view.
                        feedController.state.error == null && feedController.refreshResults(feedController.state.posts.take(20))
                    } ?: feedController.refresh(activeTopic, searchQuery)
                    notificationController.load(force = true)
                    if (succeeded) feedListState.requestScrollToItem(0)
                    refreshOwnProfile()
                    val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L
                    delay((360L - elapsedMs).coerceAtLeast(0L))
                    succeeded
                },
                onAdvancedDraftChange = {
                    advancedSearchDraft = it
                    if (advancedSearchSessionActive) advancedSearchFilters = it
                },
                onApplyAdvanced = {
                    advancedSearchFilters = advancedSearchDraft
                    advancedSearchSessionActive = true
                    searchQuery = advancedSearchDraft.query
                    feedController.prepareAdvancedSearch(advancedSearchDraft)
                    scope.launch { feedController.loadAdvanced(advancedSearchDraft) }
                },
                onResetAdvanced = { advancedSearchDraft = AdvancedSearchFilters() },
                onCloseAdvanced = { advancedSearchOpen = false },
                onAdvancedSortChange = { sort ->
                    feedController.sortAdvanced(sort)
                    advancedSearchFilters = advancedSearchFilters?.copy(sort = sort)
                },
                onSearchChange = {
                    advancedSearchFilters = null
                    searchQuery = it
                    if (it.isBlank()) {
                        advancedSearchOpen = false
                        advancedSearchSessionActive = false
                    }
                },
                onToggleTopicDropdown = { topicDropdownOpen = !topicDropdownOpen },
                onSelectTopic = selectFeedTopic,
                onPressLogo = navigateFeed,
                onNavigateProfile = openAccountSidebar,
                onAdvancedSearch = {
                    advancedSearchDraft = advancedSearchFilters ?: AdvancedSearchFilters(query = searchQuery)
                    advancedSearchOpen = !advancedSearchOpen
                    if (advancedSearchOpen) {
                        advancedSearchSessionActive = false
                        feedController.beginAdvancedSearchSession()
                        scope.launch { feedListState.animateScrollToItem(0) }
                    }
                },
                onOpenPost = { pushPost(OpenedPost(it, feedController)) },
                onQuotePost = openQuotePost,
                onOpenMessages = openMessagesFromPostMenu,
            )
        } else when {
            visibleScene.profileRequested -> ProfileShellScene(
                auth = auth,
                api = api,
                targetUuid = profileTargetUuid,
                viewedProfile = viewedProfile,
                ownProfile = composeAuthorProfile,
                scrollToTopRequest = profileScrollToTopRequest,
                refreshState = profileRefreshState,
                feedController = feedController,
                isFollowing = feedController.isFollowing(profileTargetUuid),
                onBack = closeProfile,
                onNetworthClick = { profileScrollToTopRequest++ },
                onEditProfile = { editProfileOpen = true },
                onOpenSettings = { overlayStack = overlayStack + ShellOverlayEntry.Settings(nextOverlayId++) },
                onToggleFollow = { alias ->
                    scope.launch {
                        val wasFollowing = feedController.isFollowing(profileTargetUuid)
                        if (feedController.toggleFollowing(profileTargetUuid, alias)) {
                            AppToast.success(if (wasFollowing) "User unfollowed" else "User followed")
                        } else AppToast.error("Couldn't update follow")
                    }
                },
                onMessage = {
                    scope.launch {
                        val roomUuid = feedController.startDirectMessage(profileTargetUuid)
                        if (roomUuid == null) {
                            AppToast.error("Couldn't start this conversation")
                            return@launch
                        }
                        topicDropdownOpen = false
                        profileRequested = false
                        openedPost = null
                        selectedTab = AppTab.Messages
                        val room = messagesController.resolveRoom(roomUuid) ?: RoomSummary(
                            uuid = roomUuid, name = "DM", description = "", roomType = "dm",
                            roomCode = null, isPrivate = true, gradients = emptyList(), unread = 0,
                            memberCount = 2, lastMessage = "", lastMessageAt = "", members = emptyList(),
                        )
                        messagesController.markOpened(room.uuid)
                        openedRoomTargetMessageUuid = null
                        openedRoom = room
                    }
                },
                onBlock = {
                    scope.launch {
                        val blocked = ProfileAvailability.blockedByMe[profileTargetUuid] == true
                        val ok = if (blocked) feedController.unblockAuthor(profileTargetUuid) else feedController.blockAuthor(profileTargetUuid)
                        if (ok) ProfileAvailability.blockedByMe[profileTargetUuid] = !blocked
                        if (ok) AppToast.success(if (blocked) "User unblocked" else "User blocked")
                        else AppToast.error(if (blocked) "Couldn't unblock user" else "Couldn't block user")
                    }
                },
                onProfileLoaded = { viewedProfile = it },
                onOpenPost = {
                    profileOverPost = false
                    profileWasUnderPost = true
                    pushPost(OpenedPost(it, feedController))
                },
                onOpenCommentPost = { postUuid, commentUuid ->
                    scope.launch {
                        loadFeedPost(api, auth, postUuid)?.let {
                            profileOverPost = false
                            profileWasUnderPost = true
                            pushPost(OpenedPost(it, feedController, commentUuid))
                        }
                    }
                },
                onOpenProfile = pushProfile,
                onQuotePost = openQuotePost,
                onOpenMessages = openMessagesFromPostMenu,
                onNotificationsFallback = { notificationController.load(force = true) },
                navigationActive = overlayStack.isEmpty(),
            )
            visibleScene.tab == AppTab.Messages -> MessagesShellScene(
                authUuid = auth.userUuid,
                controller = messagesController,
                aliases = feedController.state.aliases,
                refreshState = messagesRefreshState,
                listState = messagesListState,
                bottomNavigationHeight = bottomNavigationHeight,
                profileSelected = accountSidebarVisualProgress > .001f,
                roomActionBusy = roomActionBusy,
                onPressLogo = navigateFeed,
                onNavigateProfile = openAccountSidebar,
                onExplore = { exploreRoomsOpen = true },
                onJoinCode = { code ->
                    if (!roomActionBusy) scope.launch {
                        roomActionBusy = true
                        val joined = messagesController.joinByCode(code)
                        joined?.let { room -> messagesController.markOpened(room.uuid); openedRoom = room }
                        if (joined == null) AppToast.error("Couldn't join that room. Check the invite link and try again.")
                        roomActionBusy = false
                    }
                },
                onCreateRoom = {
                    if (!roomActionBusy) scope.launch {
                        roomActionBusy = true
                        messagesController.createGroup()?.let { openedRoom = it }
                        roomActionBusy = false
                    }
                },
                onRefresh = {
                    val succeeded = messagesController.load(force = true)
                    notificationController.load(force = true)
                    succeeded
                },
                onOpenRoom = { room ->
                    messagesController.markOpened(room.uuid)
                    openedPost = null
                    openedRoom = room
                },
            )
            visibleScene.tab == AppTab.Notifications -> NotificationsShellScene(
                controller = notificationController,
                filter = notificationFilter,
                refreshState = notificationRefreshState,
                listState = notificationListState,
                bottomNavigationHeight = bottomNavigationHeight,
                profileSelected = accountSidebarVisualProgress > .001f,
                scrollConnection = secondaryScrollConnection,
                onPressLogo = navigateFeed,
                onNavigateProfile = openAccountSidebar,
                onSelectFilter = { notificationFilter = it },
                onMarkAllRead = { scope.launch { notificationController.markAllRead(notificationFilter) } },
                onRefresh = {
                    val succeeded = notificationController.load(force = true)
                    if (succeeded) { withFrameNanos { }; notificationListState.animateScrollToItem(0) }
                    succeeded
                },
                onOpenNotification = { notification ->
                    val actorUuid = notification.actorUuid
                    val postUuid = notification.postUuid
                    val roomUuid = notification.roomUuid
                    when {
                        notification.type == "room_reply" && roomUuid != null -> RoomNavigationBus.open(roomUuid, notification.messageUuid)
                        notification.type == "followed" && actorUuid != null -> ProfileNavigationBus.open(actorUuid)
                        postUuid != null -> scope.launch {
                            val post = feedController.state.posts.firstOrNull { it.uuid == postUuid }
                                ?: bookmarkController.state.posts.firstOrNull { it.uuid == postUuid }
                                ?: runCatching { loadFeedPost(api, auth, postUuid) }.getOrNull()
                            if (post != null) pushPost(OpenedPost(post, feedController, notification.commentUuid))
                        }
                    }
                },
            )
            visibleScene.tab == AppTab.Bookmarks -> BookmarksShellScene(
                authUuid = auth.userUuid,
                controller = bookmarkController,
                refreshState = bookmarkRefreshState,
                listState = bookmarkListState,
                bottomNavigationHeight = bottomNavigationHeight,
                profileSelected = accountSidebarVisualProgress > .001f,
                scrollConnection = secondaryScrollConnection,
                onPressLogo = navigateFeed,
                onNavigateProfile = openAccountSidebar,
                onRefresh = {
                    val succeeded = bookmarkController.refresh("Bookmarks", "")
                    notificationController.load(force = true)
                    if (succeeded) { withFrameNanos { }; bookmarkListState.animateScrollToItem(0) }
                    succeeded
                },
                onOpenPost = { pushPost(OpenedPost(it, bookmarkController)) },
                onQuotePost = openQuotePost,
                onOpenMessages = openMessagesFromPostMenu,
            )
            else -> TransactionsShellScene(
                profileSelected = accountSidebarVisualProgress > .001f,
                onPressLogo = navigateFeed,
                onNavigateProfile = openAccountSidebar,
            )
        }
    }
}

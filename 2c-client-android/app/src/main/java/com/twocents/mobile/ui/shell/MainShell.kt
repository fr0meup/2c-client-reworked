package com.twocents.mobile.ui.shell

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.twocents.mobile.AuthState
import com.twocents.mobile.RpcApi
import com.twocents.mobile.notifications.NotificationController
import com.twocents.mobile.ui.compose.ComposeAuthorProfile
import com.twocents.mobile.ui.compose.ComposeAuthorProfileCache
import com.twocents.mobile.ui.compose.loadComposeAuthorProfile
import com.twocents.mobile.ui.feed.FeedController
import com.twocents.mobile.ui.feed.FeedSource
import com.twocents.mobile.ui.messages.MessagesController
import com.twocents.mobile.ui.navigation.AppTab
import com.twocents.mobile.ui.theme.Background
import com.twocents.mobile.ui.common.AppToast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MainShell(
    auth: AuthState,
    rpcApi: RpcApi,
    onLogout: () -> Unit = {},
) {
    val context = LocalContext.current
    val shellState = rememberMainShellState(auth.userUuid)
    with(shellState) {
    val feedController = remember(rpcApi, auth) { FeedController(rpcApi, auth, FeedSource.Arena, context) }
    val bookmarkController = remember(rpcApi, auth) { FeedController(rpcApi, auth, FeedSource.Bookmarks, context) }
    val notificationController = remember(rpcApi, auth, context) { NotificationController(rpcApi, auth, context) }
    val messagesController = remember(rpcApi, auth) { MessagesController(rpcApi, auth, context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val profileCache = remember(context) { ComposeAuthorProfileCache(context) }
    val profileRefreshScope = rememberCoroutineScope()
    LaunchedEffect(auth.userUuid) {
        com.twocents.mobile.ui.feed.QuotesNavigationBus.requests.collect { request ->
            overlayStack = overlayStack + ShellOverlayEntry.Quotes(nextOverlayId++, request.post, request.controller)
        }
    }
    fun pushProfile(userUuid: String, seed: ComposeAuthorProfile? = null) {
        // Overlay entries retain their own identity so nested profile/post chains
        // unwind one screen at a time instead of reconstructing a destination.
        overlayStack = overlayStack + ShellOverlayEntry.Profile(nextOverlayId++, userUuid, seed)
    }
    fun pushPost(opened: OpenedPost) {
        overlayStack = overlayStack + ShellOverlayEntry.Post(nextOverlayId++, opened)
    }
    fun popOverlay() {
        val top = overlayStack.lastOrNull() ?: return
        if (exitingOverlayId != null) return
        exitingOverlayId = top.id
        profileRefreshScope.launch {
            delay(175)
            if (overlayStack.lastOrNull()?.id == top.id) overlayStack = overlayStack.dropLast(1)
            exitingOverlayId = null
        }
    }
    val density = LocalDensity.current
    val graphicsContext = LocalGraphicsContext.current
    val sceneBackdropLayer = remember(graphicsContext) { graphicsContext.createGraphicsLayer() }
    DisposableEffect(graphicsContext, sceneBackdropLayer) {
        onDispose { graphicsContext.releaseGraphicsLayer(sceneBackdropLayer) }
    }
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val rawNavigationBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navigationBarInset = when {
        rawNavigationBarInset >= 40.dp -> (rawNavigationBarInset - 6.dp).coerceAtLeast(2.dp)
        rawNavigationBarInset > 0.dp -> rawNavigationBarInset + 8.dp
        else -> 8.dp
    }
    val feedHeaderHeight = statusBarInset + 57.dp
    val bottomNavigationHeight = navigationBarInset + 51.dp
    val feedHeaderHeightPx = with(density) { feedHeaderHeight.toPx() }
    val bottomNavigationHeightPx = with(density) { bottomNavigationHeight.toPx() }
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val accountSidebarWidthPx = with(density) { (screenWidth - AccountSidebarRevealWidth).coerceAtLeast(260.dp).toPx() }
    val accountSidebarProgress by animateFloatAsState(
        targetValue = if (accountSidebarOpen) 1f else 0f,
        animationSpec = tween(260, easing = ShellOutCubic),
        label = "account-sidebar",
    )
    val animatedSidebarProgress = accountSidebarProgress * (1f - accountSidebarDragFraction)
    val accountSidebarVisualProgress = if (accountSidebarEdgeHolding) {
        maxOf(accountSidebarEdgeProgress, animatedSidebarProgress)
    } else {
        animatedSidebarProgress
    }
    LaunchedEffect(accountSidebarProgress) {
        if (accountSidebarProgress <= .001f) accountSidebarDragFraction = 0f
        if (accountSidebarOpen && accountSidebarEdgeHolding && accountSidebarProgress >= accountSidebarEdgeProgress - .01f) {
            accountSidebarEdgeHolding = false
            accountSidebarEdgeProgress = 0f
        }
    }
    val hideThresholdPx = with(density) { 140.dp.toPx() }
    val nearTopPx = with(density) { 15.dp.roundToPx() }

    // Toasts follow the visible shell chrome instead of remaining stranded at
    // the old header position after the feed header scrolls away.
    SideEffect {
        val headerOffset = when {
            selectedTab == AppTab.Feed && !profileRequested && openedRoom == null && overlayStack.isEmpty() ->
                6f + 52f * (1f - feedChromeProgress)
            else -> 58f
        }
        AppToast.placeBelowVisibleHeader(headerOffset)
    }

    fun closeProfile() {
        if (profileOverPost) {
            profileOverPost = false
            return
        }
        val anchor = profileReturnAnchor
        preserveNotificationPositionOnProfileReturn = anchor?.tab == AppTab.Notifications
        profileRequested = false
        viewedProfile = null
        profileReturnAnchor = null
        if (anchor != null) {
            selectedTab = anchor.tab
            profileRefreshScope.launch {
                withFrameNanos { }
                when (anchor.tab) {
                    AppTab.Feed -> feedListState.scrollToItem(anchor.feedIndex, anchor.feedOffset)
                    AppTab.Notifications -> notificationListState.scrollToItem(anchor.notificationIndex, anchor.notificationOffset)
                    AppTab.Bookmarks -> bookmarkListState.scrollToItem(anchor.bookmarkIndex, anchor.bookmarkOffset)
                    AppTab.Messages -> messagesListState.scrollToItem(anchor.messagesIndex, anchor.messagesOffset)
                    AppTab.Transactions -> Unit
                }
            }
        }
    }

    LaunchedEffect(selectedTab) {
        if (suppressNextTabHistory) {
            suppressNextTabHistory = false
        } else if (tabBackStack.lastOrNull() != selectedTab) {
            tabBackStack = (tabBackStack + selectedTab).takeLast(12)
        }
    }

    NotificationTopAnchorEffect(notificationController, selectedTab, profileRequested, notificationListState, nearTopPx)
    PostOverlayVideoEffects(openedPost, overlayStack)
    ShellRealtimeEffects(context, auth, rpcApi, lifecycleOwner, notificationController, messagesController, profileRefreshScope)
    ShellExternalNavigationEffects(
        auth = auth,
        api = rpcApi,
        feedController = feedController,
        bookmarkController = bookmarkController,
        messagesController = messagesController,
        profileCache = profileCache,
        ownProfile = composeAuthorProfile,
        onPrepareRoomNavigation = {
            topicDropdownOpen = false
            profileRequested = false
            openedPost = null
        },
        onPrepareNotificationNavigation = {
            topicDropdownOpen = false
            profileRequested = false
        },
        onPrepareProfileNavigation = { topicDropdownOpen = false },
        onSelectTab = { selectedTab = it },
        onOpenRoom = { room, targetUuid ->
            openedRoomTargetMessageUuid = targetUuid
            openedRoom = room
        },
        onPushPost = ::pushPost,
        onPushProfile = ::pushProfile,
    )
    LaunchedEffect(selectedTab, profileRequested) {
        refreshState.cancelRefresh()
        notificationRefreshState.cancelRefresh()
        bookmarkRefreshState.cancelRefresh()
        profileRefreshState.cancelRefresh()
        messagesRefreshState.cancelRefresh()
        secondaryNavProgress = 0f
        if (selectedTab == AppTab.Notifications && !profileRequested) {
            if (!preserveNotificationPositionOnProfileReturn) {
                notificationListState.requestScrollToItem(0)
            }
            notificationController.load(force = true)
            if (preserveNotificationPositionOnProfileReturn) {
                preserveNotificationPositionOnProfileReturn = false
            } else {
                notificationListState.requestScrollToItem(0)
                withFrameNanos { }
                notificationListState.scrollToItem(0)
                withFrameNanos { }
                notificationListState.scrollToItem(0)
            }
        }
        if (selectedTab == AppTab.Messages && !profileRequested) messagesController.load(force = true)
    }
    LaunchedEffect(notificationFilter) {
        if (selectedTab == AppTab.Notifications && !profileRequested && !preserveNotificationPositionOnProfileReturn) {
            notificationListState.scrollToItem(0)
            withFrameNanos { }
            notificationListState.scrollToItem(0)
        }
    }

    val feedChromeScrollConnection = rememberFeedChromeScrollConnection(
        listState = feedListState,
        headerHeightPx = feedHeaderHeightPx,
        hideThresholdPx = hideThresholdPx,
        nearTopPx = nearTopPx,
        progress = feedChromeProgress,
        onProgress = { feedChromeProgress = it },
    )
    val secondaryNavScrollConnection = rememberSecondaryNavScrollConnection(
        selectedTab = selectedTab,
        notificationState = notificationListState,
        bookmarkState = bookmarkListState,
        bottomNavigationHeightPx = bottomNavigationHeightPx,
        nearTopPx = nearTopPx,
        progress = secondaryNavProgress,
        onProgress = { secondaryNavProgress = it },
    )
    ShellChromeSettleEffects(
        selectedTab = selectedTab,
        feedState = feedListState,
        notificationState = notificationListState,
        bookmarkState = bookmarkListState,
        nearTopPx = nearTopPx,
        feedProgress = feedChromeProgress,
        secondaryProgress = secondaryNavProgress,
        onFeedProgress = { feedChromeProgress = it },
        onSecondaryProgress = { secondaryNavProgress = it },
    )

    suspend fun refreshOwnProfile() {
        runCatching { loadComposeAuthorProfile(rpcApi, auth) }
            .getOrNull()
            ?.let { freshProfile ->
                profileCache.save(freshProfile)
                composeAuthorProfile = freshProfile
            }
    }

    fun scrollFeedToTop(refreshAfterScroll: Boolean) {
        profileRefreshScope.launch {
            feedListState.animateScrollToItem(0)
            if (refreshAfterScroll) refreshState.requestRefresh()
        }
    }

    LaunchedEffect(auth.userUuid) {
        composeAuthorProfile = profileCache.load(auth.userUuid)
        refreshOwnProfile()
    }

    val scene = ShellScene(selectedTab, profileRequested)
    fun selectFeedTopic(topic: String) {
        if (topic == activeTopic) return
        feedChromeProgress = 0f
        profileRefreshScope.launch { feedListState.scrollToItem(0) }
        activeTopic = topic
    }
    fun returnToFeedWithoutAction() {
        // The originating tap may outlive the scene transition. Suppress feed
        // retap behavior until the complete gesture has safely finished.
        suppressFeedActionUntilMs = SystemClock.elapsedRealtime() + 900L
        selectedTab = AppTab.Feed
        profileRequested = false
        feedChromeProgress = 0f
    }
    val navigateFeed = {
        val alreadyInFeed = selectedTab == AppTab.Feed && !profileRequested
        topicDropdownOpen = false
        if (alreadyInFeed) {
            if (SystemClock.elapsedRealtime() >= suppressFeedActionUntilMs) {
                val refreshAfterScroll = activeTopic == "New"
                if (activeTopic != "New") selectFeedTopic("New")
                scrollFeedToTop(refreshAfterScroll)
            }
        } else {
            returnToFeedWithoutAction()
        }
    }
    val navigateProfile = {
        topicDropdownOpen = false
        pushProfile(auth.userUuid, composeAuthorProfile)
    }
    val openAccountSidebar = {
        accountSidebarOpen = true
        profileRefreshScope.launch {
            notificationController.load(force = true)
            messagesController.load(force = true)
        }
        Unit
    }
    val openQuotePost: (com.twocents.mobile.ui.feed.FeedPost) -> Unit = { post ->
        composeQuotedPost = post
        composeInitialTopic = "Lounge"
        composeModalOpen = true
    }
    val openMessagesFromPostMenu: () -> Unit = {
        openedPost = null
        selectedTab = AppTab.Messages
        profileRequested = false
    }

    MainShellBackNavigation(
        context = context,
        selectedTab = selectedTab,
        profileRequested = profileRequested,
        hasOpenedPost = openedPost != null,
        hasOpenedRoom = openedRoom != null,
        hasRetainedOverlay = overlayStack.isNotEmpty(),
        accountSidebarOpen = accountSidebarOpen,
        topicDropdownOpen = topicDropdownOpen,
        advancedSearchOpen = advancedSearchOpen,
        profileOverPost = profileOverPost,
        exitHintVisible = exitHintVisible,
        lastExitBackAt = lastExitBackAt,
        onDismissTopicDropdown = { topicDropdownOpen = false },
        onDismissAdvancedSearch = { advancedSearchOpen = false },
        onPopOverlay = ::popOverlay,
        onDismissSidebar = { accountSidebarOpen = false },
        onCloseProfile = ::closeProfile,
        onNavigatePreviousTab = {
            val target = tabBackStack.getOrNull(tabBackStack.lastIndex - 1) ?: AppTab.Feed
            tabBackStack = if (tabBackStack.size > 1) tabBackStack.dropLast(1) else listOf(AppTab.Feed)
            suppressNextTabHistory = true
            selectedTab = target
            feedChromeProgress = 0f
        },
        onExitHintChanged = { exitHintVisible = it },
        onLastExitBackChanged = { lastExitBackAt = it },
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .graphicsLayer { translationX = -accountSidebarWidthPx * accountSidebarVisualProgress },
    ) {
        MainShellSceneRouter(
            state = shellState,
            scene = scene,
            auth = auth,
            api = rpcApi,
            feedController = feedController,
            bookmarkController = bookmarkController,
            notificationController = notificationController,
            messagesController = messagesController,
            feedHeaderHeight = feedHeaderHeight,
            bottomNavigationHeight = bottomNavigationHeight,
            feedHeaderHeightPx = feedHeaderHeightPx,
            accountSidebarVisualProgress = accountSidebarVisualProgress,
            feedScrollConnection = feedChromeScrollConnection,
            secondaryScrollConnection = secondaryNavScrollConnection,
            backdropLayer = sceneBackdropLayer,
            scope = profileRefreshScope,
            refreshOwnProfile = ::refreshOwnProfile,
            selectFeedTopic = ::selectFeedTopic,
            navigateFeed = navigateFeed,
            openAccountSidebar = openAccountSidebar,
            closeProfile = ::closeProfile,
            pushPost = ::pushPost,
            pushProfile = ::pushProfile,
            openQuotePost = openQuotePost,
            openMessagesFromPostMenu = openMessagesFromPostMenu,
        )

        MainShellLayerHost(
            context = context,
            state = shellState,
            auth = auth,
            api = rpcApi,
            feedController = feedController,
            bookmarkController = bookmarkController,
            notificationController = notificationController,
            messagesController = messagesController,
            backdropLayer = sceneBackdropLayer,
            accountSidebarWidthPx = accountSidebarWidthPx,
            accountSidebarVisualProgress = accountSidebarVisualProgress,
            bottomNavigationHeightPx = bottomNavigationHeightPx,
            feedHeaderHeight = feedHeaderHeight,
            bottomNavigationHeight = bottomNavigationHeight,
            nearTopPx = nearTopPx,
            scope = profileRefreshScope,
            refreshOwnProfile = ::refreshOwnProfile,
            selectFeedTopic = ::selectFeedTopic,
            scrollFeedToTop = ::scrollFeedToTop,
            returnToFeedWithoutAction = ::returnToFeedWithoutAction,
            navigateProfile = navigateProfile,
            popOverlay = ::popOverlay,
            pushPost = ::pushPost,
            pushProfile = ::pushProfile,
            openQuotePost = openQuotePost,
            openMessagesFromPostMenu = openMessagesFromPostMenu,
            onLogout = onLogout,
        )
    }
    }
}

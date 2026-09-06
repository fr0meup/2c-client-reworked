package com.twocents.mobile.ui.shell

import android.content.Context
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.twocents.mobile.AuthState
import com.twocents.mobile.RpcApi
import com.twocents.mobile.notifications.NotificationController
import com.twocents.mobile.notifications.NotificationEventBus
import com.twocents.mobile.notifications.NotificationNavigationBus
import com.twocents.mobile.notifications.PushNotificationManager
import com.twocents.mobile.ui.compose.ComposeAuthorProfile
import com.twocents.mobile.ui.compose.ComposeAuthorProfileCache
import com.twocents.mobile.ui.feed.FeedController
import com.twocents.mobile.ui.feed.loadFeedPost
import com.twocents.mobile.ui.messages.MessagesController
import com.twocents.mobile.ui.messages.RoomNavigationBus
import com.twocents.mobile.ui.messages.RoomSummary
import com.twocents.mobile.ui.navigation.AppTab
import com.twocents.mobile.ui.profile.ProfileNavigationBus
import com.twocents.mobile.ui.feed.setPostDetailVideoOverlayActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Keeps a live notification inserted at index zero visible only when the user
 * was already reading the top. Stable-key anchoring otherwise preserves the old
 * first row and makes the new notification appear above the viewport.
 */
@Composable
internal fun NotificationTopAnchorEffect(
    controller: NotificationController,
    selectedTab: AppTab,
    profileRequested: Boolean,
    listState: LazyListState,
    nearTopPx: Int,
) {
    var previousFirstUuid by remember { mutableStateOf<String?>(null) }
    var userPinnedToTop by remember { mutableStateOf(true) }

    // Update this only after an actual scroll gesture settles. A stable-key
    // insertion can move the old first row to index one without user input;
    // treating that layout adjustment as scrolling was the autoscroll race.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.distinctUntilChanged().collect { scrolling ->
            if (!scrolling) {
                userPinnedToTop = listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset <= nearTopPx
            }
        }
    }
    LaunchedEffect(controller.state.notifications.firstOrNull()?.uuid) {
        val newest = controller.state.notifications.firstOrNull()?.uuid
        val previous = previousFirstUuid
        if (newest != null && previous != null && newest != previous &&
            selectedTab == AppTab.Notifications && !profileRequested
        ) {
            if (userPinnedToTop) {
                listState.requestScrollToItem(0)
                withFrameNanos { }
                listState.scrollToItem(0)
                withFrameNanos { }
                listState.scrollToItem(0)
                userPinnedToTop = true
            }
        }
        previousFirstUuid = newest
    }
}

/** Coordinates media playback with both legacy and retained post overlays. */
@Composable
internal fun PostOverlayVideoEffects(openedPost: OpenedPost?, overlayStack: List<ShellOverlayEntry>) {
    LaunchedEffect(openedPost != null) {
        setPostDetailVideoOverlayActive(openedPost != null)
    }
    LaunchedEffect(overlayStack.lastOrNull()) {
        setPostDetailVideoOverlayActive(overlayStack.lastOrNull() is ShellOverlayEntry.Post)
    }
    DisposableEffect(Unit) {
        onDispose { setPostDetailVideoOverlayActive(false) }
    }
}

/**
 * Owns process-facing notification and room work. Keeping this outside the
 * visual scene router prevents lifecycle/socket concerns from being mixed with
 * layout and transition code.
 */
@Composable
internal fun ShellRealtimeEffects(
    context: Context,
    auth: AuthState,
    api: RpcApi,
    lifecycleOwner: LifecycleOwner,
    notificationController: NotificationController,
    messagesController: MessagesController,
    scope: CoroutineScope,
) {
    LaunchedEffect(notificationController, auth.userUuid) {
        notificationController.load(force = true)
        messagesController.load()
        delay(750)
        PushNotificationManager.initialize(context, api, auth)
    }
    LaunchedEffect(messagesController) {
        NotificationEventBus.events.collect {
            // FCM is an invalidation signal; allow the backend commit to land
            // before refreshing room cards and their navigation badge.
            delay(120)
            messagesController.load(force = true)
        }
    }
    DisposableEffect(lifecycleOwner, notificationController) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    notificationController.startRealtime()
                    scope.launch {
                        notificationController.load(force = true)
                        messagesController.load(force = true)
                    }
                }
                Lifecycle.Event.ON_STOP -> notificationController.stopRealtime()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            notificationController.startRealtime()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            notificationController.dispose()
        }
    }
}

/** Routes global deep-link/push/navigation buses into MainShell-owned state. */
@Composable
internal fun ShellExternalNavigationEffects(
    auth: AuthState,
    api: RpcApi,
    feedController: FeedController,
    bookmarkController: FeedController,
    messagesController: MessagesController,
    profileCache: ComposeAuthorProfileCache,
    ownProfile: ComposeAuthorProfile?,
    onPrepareRoomNavigation: () -> Unit,
    onPrepareNotificationNavigation: () -> Unit,
    onPrepareProfileNavigation: () -> Unit,
    onSelectTab: (AppTab) -> Unit,
    onOpenRoom: (RoomSummary, String?) -> Unit,
    onPushPost: (OpenedPost) -> Unit,
    onPushProfile: (String, ComposeAuthorProfile?) -> Unit,
) {
    LaunchedEffect(Unit) {
        RoomNavigationBus.requests.collect { request ->
            onPrepareRoomNavigation()
            onSelectTab(AppTab.Messages)
            messagesController.load(force = true)
            messagesController.resolveRoom(request.roomUuid)?.let { room ->
                messagesController.markOpened(room.uuid)
                onOpenRoom(room, request.targetMessageUuid)
            }
        }
    }
    LaunchedEffect(Unit) {
        NotificationNavigationBus.requests.collect { request ->
            onPrepareNotificationNavigation()
            when {
                request.roomUuid != null -> RoomNavigationBus.open(request.roomUuid, request.messageUuid)
                request.postUuid == null -> onSelectTab(AppTab.Notifications)
                else -> {
                    val post = feedController.state.posts.firstOrNull { it.uuid == request.postUuid }
                        ?: bookmarkController.state.posts.firstOrNull { it.uuid == request.postUuid }
                        ?: runCatching { loadFeedPost(api, auth, request.postUuid) }.getOrNull()
                    if (post != null) onPushPost(OpenedPost(post, feedController, request.commentUuid))
                    else onSelectTab(AppTab.Notifications)
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        ProfileNavigationBus.requests.collect { request ->
            onPrepareProfileNavigation()
            val seed = request.seedProfile
                ?: if (request.userUuid == auth.userUuid) ownProfile else profileCache.load(request.userUuid)
            if (request.seedProfile != null) profileCache.save(request.seedProfile)
            onPushProfile(request.userUuid, seed)
        }
    }
}

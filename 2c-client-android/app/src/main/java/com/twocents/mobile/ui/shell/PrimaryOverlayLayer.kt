package com.twocents.mobile.ui.shell

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import com.twocents.mobile.AuthState
import com.twocents.mobile.RpcApi
import com.twocents.mobile.ui.compose.ComposeAuthorProfile
import com.twocents.mobile.ui.compose.ComposePostDraft
import com.twocents.mobile.ui.compose.ComposePostModal
import com.twocents.mobile.ui.feed.FeedPost
import com.twocents.mobile.ui.messages.ExploreRoomsSheet
import com.twocents.mobile.ui.messages.MessagesController
import com.twocents.mobile.ui.messages.RoomSummary

/** Hosts transient content surfaces while [MainShell] retains all navigation decisions. */
@Composable
internal fun BoxScope.PrimaryOverlayLayer(
    composeVisible: Boolean,
    composeProfile: ComposeAuthorProfile?,
    composeQuotedPost: FeedPost?,
    composeInitialTopic: String,
    mentionAliases: Map<String, String>,
    auth: AuthState,
    api: RpcApi,
    onDismissCompose: () -> Unit,
    onSubmitPost: suspend (ComposePostDraft) -> Boolean,
    openedPost: OpenedPost?,
    profileOverPost: Boolean,
    onClosePost: () -> Unit,
    onOpenPost: (OpenedPost) -> Unit,
    onQuotePost: (FeedPost) -> Unit,
    onOpenMessages: () -> Unit,
    onNotificationsFallback: suspend () -> Unit,
    openedRoom: RoomSummary?,
    roomTargetMessageUuid: String?,
    roomAliases: Map<String, String>,
    roomNavigationEnabled: Boolean,
    roomHiddenByProfile: Boolean,
    onCloseRoom: () -> Unit,
    exploreRoomsOpen: Boolean,
    messagesController: MessagesController,
    onDismissExploreRooms: () -> Unit,
    onOpenExploredRoom: (RoomSummary) -> Unit,
) {
    ComposePostModal(
        visible = composeVisible,
        onDismiss = onDismissCompose,
        profile = composeProfile,
        quotedPost = composeQuotedPost,
        initialTopic = composeInitialTopic,
        mentionAliases = mentionAliases,
        mentionApi = api,
        mentionAuth = auth,
        onPost = onSubmitPost,
    )

    PostDetailSlideHost(
        openedPost = openedPost,
        auth = auth,
        api = api,
        onClose = onClosePost,
        onOpenPost = onOpenPost,
        onQuotePost = onQuotePost,
        onOpenMessages = onOpenMessages,
        onNotificationsFallback = onNotificationsFallback,
        navigationEnabled = !profileOverPost,
        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (profileOverPost) 0f else 1f }
            .zIndex(if (profileOverPost) -1f else 20f),
    )

    RoomChatSlideHost(
        openedRoom = openedRoom,
        targetMessageUuid = roomTargetMessageUuid,
        auth = auth,
        api = api,
        aliases = roomAliases,
        navigationEnabled = roomNavigationEnabled,
        onClose = onCloseRoom,
        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (roomHiddenByProfile) 0f else 1f }
            .zIndex(if (roomHiddenByProfile) -1f else 30f),
    )

    if (exploreRoomsOpen) {
        ExploreRoomsSheet(
            controller = messagesController,
            onDismiss = onDismissExploreRooms,
            onOpenRoom = onOpenExploredRoom,
        )
    }
}

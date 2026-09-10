package com.twocents.mobile.ui.shell

import androidx.compose.animation.core.CubicBezierEasing
import com.twocents.mobile.ui.compose.ComposeAuthorProfile
import com.twocents.mobile.ui.feed.FeedController
import com.twocents.mobile.ui.feed.FeedPost
import com.twocents.mobile.ui.navigation.AppTab

internal data class ShellScene(
    val tab: AppTab,
    val profileRequested: Boolean,
)

internal data class OpenedPost(
    val post: FeedPost,
    val sourceController: FeedController,
    val targetCommentUuid: String? = null,
)

/** Exact list positions restored after returning from an overlaid profile. */
internal data class ProfileReturnAnchor(
    val tab: AppTab,
    val feedIndex: Int,
    val feedOffset: Int,
    val notificationIndex: Int,
    val notificationOffset: Int,
    val bookmarkIndex: Int,
    val bookmarkOffset: Int,
    val messagesIndex: Int,
    val messagesOffset: Int,
)

internal sealed interface ShellOverlayEntry {
    val id: Long

    data class Profile(
        override val id: Long,
        val userUuid: String,
        val seed: ComposeAuthorProfile?,
    ) : ShellOverlayEntry

    data class Post(override val id: Long, val opened: OpenedPost) : ShellOverlayEntry
    data class Quotes(override val id: Long, val post: FeedPost, val controller: FeedController) : ShellOverlayEntry
    data class Leaderboard(override val id: Long) : ShellOverlayEntry
    data class Settings(override val id: Long) : ShellOverlayEntry
}

internal val ShellOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
internal val ShellInCubic = CubicBezierEasing(0.32f, 0f, 0.67f, 0f)

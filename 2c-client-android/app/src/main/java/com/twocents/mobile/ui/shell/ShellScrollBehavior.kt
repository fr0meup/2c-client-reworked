package com.twocents.mobile.ui.shell

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import com.twocents.mobile.ui.navigation.AppTab
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/** Feed chrome follows consumed scroll, never unconsumed pull-to-refresh distance. */
@Composable
internal fun rememberFeedChromeScrollConnection(
    listState: LazyListState,
    headerHeightPx: Float,
    hideThresholdPx: Float,
    nearTopPx: Int,
    progress: Float,
    onProgress: (Float) -> Unit,
): NestedScrollConnection {
    val currentProgress by rememberUpdatedState(progress)
    val updateProgress by rememberUpdatedState(onProgress)
    return remember(listState, headerHeightPx, hideThresholdPx, nearTopPx) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset <= nearTopPx
                updateProgress(
                    when {
                        atTop -> 0f
                        consumed.y < 0f && (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > hideThresholdPx) ->
                            (currentProgress + (-consumed.y * .42f / headerHeightPx)).coerceIn(0f, 1f)
                        consumed.y > 0f -> (currentProgress - (consumed.y * .85f / headerHeightPx)).coerceIn(0f, 1f)
                        else -> currentProgress
                    },
                )
                return Offset.Zero
            }
        }
    }
}

@Composable
internal fun rememberSecondaryNavScrollConnection(
    selectedTab: AppTab,
    notificationState: LazyListState,
    bookmarkState: LazyListState,
    bottomNavigationHeightPx: Float,
    nearTopPx: Int,
    progress: Float,
    onProgress: (Float) -> Unit,
): NestedScrollConnection {
    val currentTab by rememberUpdatedState(selectedTab)
    val currentProgress by rememberUpdatedState(progress)
    val updateProgress by rememberUpdatedState(onProgress)
    return remember(notificationState, bookmarkState, bottomNavigationHeightPx, nearTopPx) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val state = if (currentTab == AppTab.Notifications) notificationState else bookmarkState
                val atTop = state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset <= nearTopPx
                updateProgress(
                    when {
                        atTop -> 0f
                        consumed.y < 0f -> (currentProgress + (-consumed.y * .58f / bottomNavigationHeightPx)).coerceIn(0f, 1f)
                        consumed.y > 0f -> (currentProgress - (consumed.y * .9f / bottomNavigationHeightPx)).coerceIn(0f, 1f)
                        else -> currentProgress
                    },
                )
                return Offset.Zero
            }
        }
    }
}

/** Settles partially hidden chrome only after a gesture ends, matching the original thresholds. */
@Composable
internal fun ShellChromeSettleEffects(
    selectedTab: AppTab,
    feedState: LazyListState,
    notificationState: LazyListState,
    bookmarkState: LazyListState,
    nearTopPx: Int,
    feedProgress: Float,
    secondaryProgress: Float,
    onFeedProgress: (Float) -> Unit,
    onSecondaryProgress: (Float) -> Unit,
) {
    val currentFeedProgress by rememberUpdatedState(feedProgress)
    val currentSecondaryProgress by rememberUpdatedState(secondaryProgress)
    LaunchedEffect(selectedTab, notificationState, bookmarkState) {
        val state = if (selectedTab == AppTab.Notifications) notificationState else bookmarkState
        snapshotFlow { state.isScrollInProgress }.distinctUntilChanged().collectLatest { scrolling ->
            if (scrolling || selectedTab !in setOf(AppTab.Notifications, AppTab.Bookmarks)) return@collectLatest
            val atTop = state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset <= nearTopPx
            val start = currentSecondaryProgress
            val target = if (atTop || start < .55f) 0f else 1f
            animate(start, target, animationSpec = tween(220, easing = ShellOutCubic)) { value, _ -> onSecondaryProgress(value) }
        }
    }
    LaunchedEffect(feedState) {
        snapshotFlow { feedState.isScrollInProgress }.distinctUntilChanged().collectLatest { scrolling ->
            if (scrolling) return@collectLatest
            val atTop = feedState.firstVisibleItemIndex == 0 && feedState.firstVisibleItemScrollOffset <= nearTopPx
            val start = currentFeedProgress
            val target = if (atTop || start < .25f) 0f else 1f
            animate(start, target, animationSpec = tween(240, easing = ShellOutCubic)) { value, _ -> onFeedProgress(value) }
        }
    }
}

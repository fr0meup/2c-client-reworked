package com.twocents.mobile.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.twocents.mobile.ui.common.PullToRefreshContainer
import com.twocents.mobile.ui.common.PullToRefreshState
import com.twocents.mobile.ui.common.RefreshProgressBar
import com.twocents.mobile.ui.feed.*
import com.twocents.mobile.ui.theme.Background

/** Feed-scene presentation only; MainShell retains navigation and refresh policy. */
@Composable
internal fun FeedShellScene(
    refreshState: PullToRefreshState,
    listState: LazyListState,
    headerHeight: Dp,
    bottomNavigationHeight: Dp,
    headerHeightPx: Float,
    chromeProgress: Float,
    chromeScrollConnection: NestedScrollConnection,
    controller: FeedController,
    activeTopic: String,
    searchQuery: String,
    advancedFilters: AdvancedSearchFilters?,
    advancedSearchOpen: Boolean,
    advancedSearchDraft: AdvancedSearchFilters,
    advancedSessionActive: Boolean,
    topicDropdownOpen: Boolean,
    profileSelected: Boolean,
    authUuid: String,
    onRefresh: suspend () -> Boolean,
    onAdvancedDraftChange: (AdvancedSearchFilters) -> Unit,
    onApplyAdvanced: () -> Unit,
    onResetAdvanced: () -> Unit,
    onCloseAdvanced: () -> Unit,
    onAdvancedSortChange: (SearchResultSort) -> Unit,
    onSearchChange: (String) -> Unit,
    onToggleTopicDropdown: () -> Unit,
    onSelectTopic: (String) -> Unit,
    onPressLogo: () -> Unit,
    onNavigateProfile: () -> Unit,
    onAdvancedSearch: () -> Unit,
    onOpenPost: (FeedPost) -> Unit,
    onQuotePost: (FeedPost) -> Unit,
    onOpenMessages: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshContainer(
            state = refreshState,
            enabled = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
            indicatorTopOffset = headerHeight,
        ) {
            FeedContent(
                controller = controller,
                topic = activeTopic,
                searchQuery = searchQuery,
                advancedFilters = advancedFilters,
                advancedHeaderContent = if (advancedSearchOpen) ({
                    AdvancedSearchHeaderPanel(
                        value = advancedSearchDraft,
                        onValueChange = onAdvancedDraftChange,
                        onApply = onApplyAdvanced,
                        onReset = onResetAdvanced,
                        onClose = onCloseAdvanced,
                        searching = controller.state.advancedSearching,
                        scanned = controller.state.advancedScanned,
                        matches = controller.state.advancedMatches,
                    )
                }) else null,
                onAdvancedSortChange = onAdvancedSortChange,
                authUuid = authUuid,
                listState = listState,
                topContentPadding = headerHeight,
                bottomContentPadding = bottomNavigationHeight + 18.dp,
                chromeScrollConnection = chromeScrollConnection,
                onOpenPost = onOpenPost,
                onQuotePost = onQuotePost,
                onOpenMessages = onOpenMessages,
            )
        }
        Box(
            modifier = Modifier.fillMaxWidth()
                .graphicsLayer { translationY = -headerHeightPx * chromeProgress }
                .background(Background),
        ) {
            FeedHeader(
                activeTopic = activeTopic,
                onSelectTopic = onSelectTopic,
                searchQuery = searchQuery,
                onSearchChange = onSearchChange,
                topicDropdownOpen = topicDropdownOpen,
                onToggleTopicDropdown = onToggleTopicDropdown,
                onPressLogo = onPressLogo,
                onNavigateProfile = onNavigateProfile,
                onAdvancedSearch = onAdvancedSearch,
                profileSelected = profileSelected,
                modifier = Modifier.statusBarsPadding(),
            )
            RefreshProgressBar(active = refreshState.isRefreshing, modifier = Modifier.align(Alignment.BottomCenter))
        }
        StatusBarScrim(alpha = chromeProgress, modifier = Modifier.align(Alignment.TopCenter))
    }
}

package com.twocents.mobile.ui.shell

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.twocents.mobile.ui.navigation.AppTab
import kotlinx.coroutines.delay

/**
 * Shell-level back policy. Destination-specific back handling remains inside
 * each overlay/screen; this layer only unwinds shell-owned UI in priority order.
 */
@Composable
internal fun MainShellBackNavigation(
    context: Context,
    selectedTab: AppTab,
    profileRequested: Boolean,
    hasOpenedPost: Boolean,
    hasOpenedRoom: Boolean,
    hasRetainedOverlay: Boolean,
    accountSidebarOpen: Boolean,
    topicDropdownOpen: Boolean,
    advancedSearchOpen: Boolean,
    profileOverPost: Boolean,
    exitHintVisible: Boolean,
    lastExitBackAt: Long,
    onDismissTopicDropdown: () -> Unit,
    onDismissAdvancedSearch: () -> Unit,
    onPopOverlay: () -> Unit,
    onDismissSidebar: () -> Unit,
    onCloseProfile: () -> Unit,
    onNavigatePreviousTab: () -> Unit,
    onExitHintChanged: (Boolean) -> Unit,
    onLastExitBackChanged: (Long) -> Unit,
) {
    BackHandler(
        enabled = topicDropdownOpen || advancedSearchOpen || hasRetainedOverlay || accountSidebarOpen ||
            (profileRequested && (!hasOpenedPost || profileOverPost)) ||
            (!hasOpenedPost && !hasOpenedRoom && selectedTab != AppTab.Feed),
    ) {
        when {
            topicDropdownOpen -> onDismissTopicDropdown()
            advancedSearchOpen -> onDismissAdvancedSearch()
            hasRetainedOverlay -> onPopOverlay()
            accountSidebarOpen -> onDismissSidebar()
            profileRequested -> onCloseProfile()
            else -> onNavigatePreviousTab()
        }
    }

    BackHandler(
        enabled = selectedTab == AppTab.Feed && !profileRequested && !hasOpenedPost && !hasOpenedRoom &&
            !hasRetainedOverlay && !accountSidebarOpen && !topicDropdownOpen && !advancedSearchOpen,
    ) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastExitBackAt <= 2_000L) {
            (context as? Activity)?.finish()
        } else {
            onLastExitBackChanged(now)
            onExitHintChanged(true)
        }
    }

    LaunchedEffect(exitHintVisible, lastExitBackAt) {
        if (exitHintVisible) {
            delay(2_050L)
            if (SystemClock.elapsedRealtime() - lastExitBackAt >= 2_000L) onExitHintChanged(false)
        }
    }
}

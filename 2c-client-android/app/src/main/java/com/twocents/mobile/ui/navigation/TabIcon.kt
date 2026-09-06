package com.twocents.mobile.ui.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.twocents.mobile.kotlin.R

private data class TabIconAsset(
    @param:DrawableRes val unselected: Int,
    @param:DrawableRes val selected: Int,
    val scale: Float = 1f,
)

private val TAB_ICON_ASSETS = mapOf(
    AppTab.Feed to TabIconAsset(
        R.drawable.nav_feed_unselected,
        R.drawable.nav_feed_selected,
    ),
    AppTab.Messages to TabIconAsset(
        R.drawable.nav_messages_unselected,
        R.drawable.nav_messages_selected,
        scale = 1.08f,
    ),
    AppTab.Notifications to TabIconAsset(
        R.drawable.nav_notifications_unselected,
        R.drawable.nav_notifications_selected,
        scale = 1.1f,
    ),
    AppTab.Bookmarks to TabIconAsset(
        R.drawable.nav_bookmarks_unselected,
        R.drawable.nav_bookmarks_selected,
        scale = 0.86f,
    ),
    AppTab.Transactions to TabIconAsset(
        R.drawable.nav_transactions_unselected,
        R.drawable.nav_transactions_selected,
        scale = 0.91f,
    ),
)

@Composable
fun TabIcon(tab: AppTab, selected: Boolean, modifier: Modifier = Modifier) {
    val asset = TAB_ICON_ASSETS.getValue(tab)
    Image(
        painter = painterResource(if (selected) asset.selected else asset.unselected),
        contentDescription = tab.label,
        contentScale = ContentScale.Fit,
        modifier = modifier.graphicsLayer {
            scaleX = asset.scale
            scaleY = asset.scale
        },
    )
}

package com.twocents.mobile.ui.feed

import com.twocents.mobile.kotlin.R

internal val SEARCH_ICON_RES = R.drawable.header_search
internal val PROFILE_ICON_RES = R.drawable.header_profile
internal val PROFILE_ICON_SELECTED_RES = R.drawable.header_profile_selected

internal data class TopicGroup(
    val category: String,
    val items: List<String>,
)

internal val TOPIC_GROUPS = listOf(
    TopicGroup("Feeds", listOf("New", "Hot", "Following", "Polls", "Picks")),
    TopicGroup("Lifestyle", listOf("Lounge", "Dating", "Ask a millionaire")),
    TopicGroup("Platform", listOf("Announcements", "Bugs and feedback")),
    TopicGroup("Tech & Finance", listOf("Stocks", "Cryptocurrency", "AI and tech", "Situation monitoring")),
)

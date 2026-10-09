package com.twocents.mobile.ui.compose

import com.twocents.mobile.ui.feed.FeedPost

enum class ComposePostOption {
    Image,
    Poll,
    Likert,
    Gif,
    EquityChart,
    EquityPrediction,
}

data class ComposeTopic(
    val name: String,
    val subtitle: String,
    val icon: ComposeTopicIcon,
)

enum class ComposeTopicIcon {
    Chat,
    Dollar,
    Bug,
}

data class ComposePostDraft(
    val title: String,
    val body: String,
    val topic: String,
    val option: ComposePostOption?,
    val pollOptions: List<String>,
    val mediaUris: List<String>,
    val pollLink: String? = null,
    val quotedPost: FeedPost? = null,
    val equityChart: com.twocents.mobile.ui.common.EquityChartAttachment? = null,
    val equityPrediction: com.twocents.mobile.ui.common.EquityPrediction? = null,
)

val ComposeTopics = listOf(
    ComposeTopic("Lounge", "Casual chats & general discussion", ComposeTopicIcon.Chat),
    ComposeTopic("Serious", "Thoughtful conversations & serious discussion", ComposeTopicIcon.Chat),
    ComposeTopic("Ask a millionaire", "Wealth questions & financial advice", ComposeTopicIcon.Dollar),
    ComposeTopic("Bugs and feedback", "Report issues & platform suggestions", ComposeTopicIcon.Bug),
)

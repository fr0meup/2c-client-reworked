package com.twocents.mobile.ui.common

import com.twocents.mobile.ui.compose.ComposeAuthorProfile
import com.twocents.mobile.ui.feed.FeedAuthor
import com.twocents.mobile.ui.feed.FeedPost
import com.twocents.mobile.ui.leaderboard.LeaderboardEntry
import com.twocents.mobile.ui.messages.RoomMember
import com.twocents.mobile.ui.profile.ProfileUser

internal fun ComposeAuthorProfile.toUserDisplay(nickname: String? = null, elo: Int? = null, joined: String? = null) =
    UserDisplayModel(uuid, nickname, balance, subscriptionType, age, gender, arena, elo, joined, role)

internal fun FeedPost.toUserDisplay(nickname: String? = author.alias, elo: Int? = author.eloRating?.toInt(), joined: String? = null) =
    UserDisplayModel(authorUuid, nickname, author.balance, author.subscriptionType, author.age, author.gender, author.arena, elo, joined, author.role)

internal fun FeedAuthor.toUserDisplay(uuid: String, nickname: String? = alias, joined: String? = null) =
    UserDisplayModel(uuid, nickname, balance, subscriptionType, age, gender, arena, eloRating?.toInt(), joined, role)

internal fun RoomMember.toUserDisplay(nickname: String? = alias ?: username) =
    UserDisplayModel(uuid, nickname, balance, subscriptionType, age, gender, arena, role = role)

internal fun LeaderboardEntry.toUserDisplay(nickname: String? = null) =
    UserDisplayModel(uuid, nickname, balance, subscriptionType, age = age, gender = gender, arena = arena, role = role)

internal fun ProfileUser.toUserDisplay(nickname: String? = null) =
    UserDisplayModel(uuid, nickname, balance, subscriptionType, age, gender, arena, elo, createdAt, role)

package com.twocents.mobile.ui.common

/** Feature-neutral identity data consumed by net-worth and metadata chrome. */
internal data class UserDisplayModel(
    val uuid: String,
    val nickname: String? = null,
    val balance: Double = 0.0,
    val subscriptionType: Int = 0,
    val age: Int? = null,
    val gender: String? = null,
    val arena: String? = null,
    val elo: Int? = null,
    val joined: String? = null,
    val role: String? = null,
)

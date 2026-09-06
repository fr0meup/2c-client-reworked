package com.twocents.mobile

data class AuthState(
    val token: String,
    val userUuid: String,
    val secretKey: String,
)

data class LoginCredentials(
    val token: String,
    val userUuid: String,
    val secretKey: String,
)

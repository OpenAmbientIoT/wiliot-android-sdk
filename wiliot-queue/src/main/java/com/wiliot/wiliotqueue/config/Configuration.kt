package com.wiliot.wiliotqueue.config

data class Configuration(
    val authToken: String? = null,
    internal val gwToken: String? = null,
    internal val gwRefreshToken: String? = null,
    val sendLogs: Boolean = false
)
package com.wiliot.wiliotnetworkmeta.utils

import com.wiliot.wiliotcore.env.EnvironmentWiliot
import com.wiliot.wiliotnetworkmeta.BuildConfig
import okhttp3.Request

internal fun Request.signedRequest(token: String?): Request {
    return newBuilder()
        .apply {
            token?.let {
                header("Authorization", "Bearer $it")
            }
        }
        .build()
}

fun EnvironmentWiliot.coreApiBase(): String {
    return apiBaseUrl
}
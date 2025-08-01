package com.wiliot.wiliotnetworkedge.utils

import com.wiliot.wiliotcore.env.EnvironmentWiliot
import com.wiliot.wiliotnetworkedge.BuildConfig
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
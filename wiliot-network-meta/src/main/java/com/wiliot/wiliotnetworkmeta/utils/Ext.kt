package com.wiliot.wiliotnetworkmeta.utils

import com.wiliot.wiliotcore.legacy.EnvironmentWiliot
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
    return when (this) {
        EnvironmentWiliot.PROD_AWS -> BuildConfig.PROD_AWS_API_BASE
        EnvironmentWiliot.PROD_GCP -> BuildConfig.PROD_GCP_API_BASE
        EnvironmentWiliot.TEST_AWS -> BuildConfig.TEST_AWS_API_BASE
        EnvironmentWiliot.TEST_GCP -> BuildConfig.TEST_GCP_API_BASE
        EnvironmentWiliot.DEV_AWS -> BuildConfig.DEV_AWS_API_BASE
        EnvironmentWiliot.DEV_GCP -> BuildConfig.DEV_GCP_API_BASE
    }
}
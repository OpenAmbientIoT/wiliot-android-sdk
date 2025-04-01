package com.wiliot.wiliotcore.embedded.auth

import com.wiliot.wiliotcore.BuildConfig
import com.wiliot.wiliotcore.legacy.EnvironmentWiliot

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
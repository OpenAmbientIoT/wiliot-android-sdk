package com.wiliot.wiliotnetworkmeta.interceptor

import com.wiliot.wiliotcore.utils.network.proceedSafely
import com.wiliot.wiliotnetworkmeta.WiliotNetworkMeta
import okhttp3.Interceptor
import okhttp3.Response

internal class MetaRequestSigningInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request()
            .url
            .newBuilder()
            .build()
        val request = chain.request()
            .newBuilder().apply {
                WiliotNetworkMeta.configuration.authToken?.let {
                    addHeader("Authorization", "Bearer $it")
                }
            }
            .url(url)
            .build()
        return chain.proceedSafely(request)
    }

}
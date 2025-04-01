package com.wiliot.wiliotqueue.interceptor

import com.wiliot.wiliotcore.utils.network.proceedSafely
import com.wiliot.wiliotqueue.WiliotQueue
import okhttp3.Interceptor
import okhttp3.Response

class RequestSigningInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request()
            .url
            .newBuilder()
            .build()
        val request = chain.request()
            .newBuilder()
            .apply {
                if (url.pathSegments.contains("refresh").not()) {
                    WiliotQueue.configuration.authToken?.let {
                        addHeader("Authorization", "Bearer $it")
                    }
                }
            }
            .url(url)
            .build()
        return chain.proceedSafely(request)
    }

}
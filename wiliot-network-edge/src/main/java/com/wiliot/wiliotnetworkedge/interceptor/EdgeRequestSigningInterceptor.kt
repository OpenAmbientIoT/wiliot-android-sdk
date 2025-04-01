package com.wiliot.wiliotnetworkedge.interceptor

import com.wiliot.wiliotcore.utils.network.proceedSafely
import com.wiliot.wiliotnetworkedge.WiliotNetworkEdge
import okhttp3.Interceptor
import okhttp3.Response

class EdgeRequestSigningInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request()
            .url
            .newBuilder()
            .build()
        val request = chain.request()
            .newBuilder().apply {
                WiliotNetworkEdge.configuration.authToken?.let {
                    addHeader("Authorization", "Bearer $it")
                }
            }
            .url(url)
            .build()
        return chain.proceedSafely(request)
    }

}
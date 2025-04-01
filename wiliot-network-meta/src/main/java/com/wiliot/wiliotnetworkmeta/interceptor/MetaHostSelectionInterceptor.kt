package com.wiliot.wiliotnetworkmeta.interceptor

import com.wiliot.wiliotcore.utils.network.proceedSafely
import okhttp3.Interceptor
import okhttp3.Response

internal class MetaHostSelectionInterceptor(
    private val hostUrlSource: MetaHostUrlSource
) : Interceptor {

    companion object {
        const val DUMMY_HOST = "http://api.dummy.host"
    }

    interface MetaHostUrlSource {
        fun getBaseUrl(): String
    }

    @Volatile
    private var host: String? = null

    private fun syncBaseUrl() {
        this.host = hostUrlSource.getBaseUrl()
    }

      override fun intercept(chain: Interceptor.Chain): Response {
        syncBaseUrl()

        var request = chain.request()
        val host = this.host
        if (host != null) {
            val newUrl = request.url.toString().replace(DUMMY_HOST, host)
            request = request.newBuilder()
                .url(newUrl)
                .build()
        } else {
            throw IllegalStateException("Host is null")
        }

        return chain.proceedSafely(request)
    }

}
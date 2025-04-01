package com.wiliot.wiliotqueue.di

import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.utils.Reporter
import com.wiliot.wiliotcore.utils.network.proceedSafely
import com.wiliot.wiliotqueue.BuildConfig
import com.wiliot.wiliotqueue.interceptor.HostSelectionInterceptor
import com.wiliot.wiliotqueue.interceptor.RequestSigningInterceptor
import com.wiliot.wiliotqueue.interceptor.TokenRefreshAuthenticator
import com.wiliot.wiliotqueue.utils.coreApiBase
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor

private object Common {

    private const val HEADER_CONTENT_TYPE = "Content-Type"
    private const val HEADER_CONTENT_TYPE_JSON = "application/json"

    private var httpLoggingInterceptor: HttpLoggingInterceptor? = null
    private var requestInterceptor: Interceptor? = null
    private var tokenRefreshAuthenticator: Authenticator? = null
    private var requestSigningInterceptor: Interceptor? = null
    private var requestHostSelectionInterceptor: Interceptor? = null

    fun provideHTTPLoggingInterceptor(): HttpLoggingInterceptor {
        if (httpLoggingInterceptor == null) {
            httpLoggingInterceptor = HttpLoggingInterceptor()
            httpLoggingInterceptor!!.setLevel(
                if (BuildConfig.DEBUG)
                    HttpLoggingInterceptor.Level.BODY
                else
                    HttpLoggingInterceptor.Level.NONE
            )
        }
        return httpLoggingInterceptor!!
    }

    fun provideRequestInterceptor(): Interceptor {
        if (requestInterceptor == null) {
            requestInterceptor = Interceptor { chain ->
                val request = chain.request()
                    .newBuilder()
                    .addHeader(HEADER_CONTENT_TYPE, HEADER_CONTENT_TYPE_JSON)
                    .build()
                return@Interceptor chain.proceedSafely(request)
            }
        }
        return requestInterceptor!!
    }

    fun provideTokenRefreshAuthenticator(): Authenticator {
        if (tokenRefreshAuthenticator == null) {
            tokenRefreshAuthenticator = TokenRefreshAuthenticator(Wiliot.tokenExpirationCallback)
        }
        return tokenRefreshAuthenticator!!
    }

    fun provideRequestSigningInterceptor(): Interceptor {
        if (requestSigningInterceptor == null) {
            requestSigningInterceptor = RequestSigningInterceptor()
        }
        return requestSigningInterceptor!!
    }

    fun provideRequestHostSelectionInterceptor(): Interceptor {
        if (requestHostSelectionInterceptor == null) {
            requestHostSelectionInterceptor =
                HostSelectionInterceptor(object : HostSelectionInterceptor.HostUrlSource {
                    override fun getBaseUrl(): String {
                        return Wiliot.configuration.environment.coreApiBase().also {
                            if (BuildConfig.DEBUG) Reporter.log("CORE API BASE: $it", "HostSelectionInterceptor")
                        }
                    }
                })
        }
        return requestHostSelectionInterceptor!!
    }

}

internal fun httpLoggingInterceptor() = Common.provideHTTPLoggingInterceptor()

internal fun requestInterceptor() = Common.provideRequestInterceptor()

internal fun tokenRefreshAuthenticator() = Common.provideTokenRefreshAuthenticator()

internal fun requestSigningInterceptor() = Common.provideRequestSigningInterceptor()

internal fun requestHostSelectionInterceptor() = Common.provideRequestHostSelectionInterceptor()
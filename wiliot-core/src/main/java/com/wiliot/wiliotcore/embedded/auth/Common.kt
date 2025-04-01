package com.wiliot.wiliotcore.embedded.auth

import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import com.wiliot.wiliotcore.BuildConfig
import com.wiliot.wiliotcore.utils.network.WiliotHeadersInterceptor
import com.wiliot.wiliotcore.utils.network.proceedSafely
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

private object Common {

    private const val HEADER_CONTENT_TYPE = "Content-Type"
    private const val HEADER_CONTENT_TYPE_JSON = "application/json"

    private const val CONNECT_TIMEOUT = 3000L
    private const val READ_TIMEOUT = 3000L
    private const val WRITE_TIMEOUT = 3000L

    private var httpLoggingInterceptor: HttpLoggingInterceptor? = null
    private var requestInterceptor: Interceptor? = null
    private var requestHostSelectionInterceptor: Interceptor? = null

    private var client: OkHttpClient? = null
    private var service: WltAuthApiService? = null

    private fun provideHTTPLoggingInterceptor(): HttpLoggingInterceptor {
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

    private fun provideRequestInterceptor(): Interceptor {
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

    private fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor = provideHTTPLoggingInterceptor(),
        requestInterceptor: Interceptor = provideRequestInterceptor(),
    ): OkHttpClient {
        if (client == null) {
            client = OkHttpClient.Builder()
                .addInterceptor(WiliotHeadersInterceptor())
                .addInterceptor(requestInterceptor)
                .connectTimeout(CONNECT_TIMEOUT, TimeUnit.MILLISECONDS)
                .writeTimeout(WRITE_TIMEOUT, TimeUnit.MILLISECONDS)
                .readTimeout(READ_TIMEOUT, TimeUnit.MILLISECONDS)
                .addInterceptor(loggingInterceptor)
                .build()
        }
        return client!!
    }

    fun provideRetrofitService(
        client: OkHttpClient = provideOkHttpClient()
    ): WltAuthApiService {
        if (service == null) {
            service =  Retrofit.Builder()
                .baseUrl(AuthHostSelectionInterceptor.DUMMY_HOST)
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(CoroutineCallAdapterFactory())
                .client(client)
                .build()
                .create(WltAuthApiService::class.java)
        }
        return service!!
    }

}

internal fun wltAuthApiService(): WltAuthApiService = Common.provideRetrofitService()

private class AuthHostSelectionInterceptor(
    private val hostUrlSource: EdgeHostUrlSource
) : Interceptor {

    companion object {
        const val DUMMY_HOST = "http://api.dummy.host"
    }

    interface EdgeHostUrlSource {
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
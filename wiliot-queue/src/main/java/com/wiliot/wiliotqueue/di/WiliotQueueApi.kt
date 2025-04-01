package com.wiliot.wiliotqueue.di

import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import com.wiliot.wiliotcore.utils.network.WiliotHeadersInterceptor
import com.wiliot.wiliotqueue.api.WiliotQueueApiService
import com.wiliot.wiliotqueue.interceptor.HostSelectionInterceptor
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

private object WiliotQueueApi {

    private const val CONNECT_TIMEOUT = 3000L
    private const val READ_TIMEOUT = 3000L
    private const val WRITE_TIMEOUT = 3000L

    private var okHttpClient: OkHttpClient? = null
    private var apiService: WiliotQueueApiService? = null

    private fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor = httpLoggingInterceptor(),
        requestInterceptor: Interceptor = requestInterceptor(),
        tokenRefreshAuthenticator: Authenticator = tokenRefreshAuthenticator(),
        signingInterceptor: Interceptor = requestSigningInterceptor(),
        requestHostSelectionInterceptor: Interceptor = requestHostSelectionInterceptor(),
    ): OkHttpClient {
        if (okHttpClient == null) {
            okHttpClient = OkHttpClient.Builder()
                .addInterceptor(requestInterceptor)
                .addInterceptor(requestHostSelectionInterceptor)
                .addInterceptor(signingInterceptor)
                .authenticator(tokenRefreshAuthenticator)
                .addInterceptor(WiliotHeadersInterceptor())
                .connectTimeout(CONNECT_TIMEOUT, TimeUnit.MILLISECONDS)
                .writeTimeout(WRITE_TIMEOUT, TimeUnit.MILLISECONDS)
                .readTimeout(READ_TIMEOUT, TimeUnit.MILLISECONDS)
                .addInterceptor(loggingInterceptor)
                .build()
        }
        return okHttpClient!!
    }

    fun retrofitService(
        client: OkHttpClient = provideOkHttpClient()
    ): WiliotQueueApiService {
        if (apiService == null) {
            apiService = Retrofit.Builder()
                .baseUrl(HostSelectionInterceptor.DUMMY_HOST)
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(CoroutineCallAdapterFactory())
                .client(client)
                .build()
                .create(WiliotQueueApiService::class.java)
        }
        return apiService!!
    }

}

fun apiService() = WiliotQueueApi.retrofitService()


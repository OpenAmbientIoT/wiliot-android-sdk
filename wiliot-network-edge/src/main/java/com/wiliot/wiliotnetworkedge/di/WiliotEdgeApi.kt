package com.wiliot.wiliotnetworkedge.di

import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import com.wiliot.wiliotcore.utils.network.WiliotHeadersInterceptor
import com.wiliot.wiliotnetworkedge.BuildConfig
import com.wiliot.wiliotnetworkedge.api.WiliotEdgeApiService
import com.wiliot.wiliotnetworkedge.interceptor.EdgeHostSelectionInterceptor
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

private object WiliotEdgeApi {

    private const val CONNECT_TIMEOUT = 3000L
    private const val READ_TIMEOUT = 3000L
    private const val WRITE_TIMEOUT = 3000L

    private var okHttpClient: OkHttpClient? = null
    private var apiService: WiliotEdgeApiService? = null

    fun provideOkHttpClient(
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
                .apply {
                    if (BuildConfig.SDK_HTTP_LOGS_ENABLED)
                        addInterceptor(loggingInterceptor)
                }
                .build()
        }
        return okHttpClient!!
    }

    fun retrofitService(
        client: OkHttpClient = provideOkHttpClient(),
    ): WiliotEdgeApiService {
        if (apiService == null) {
            apiService = Retrofit.Builder()
                .baseUrl(EdgeHostSelectionInterceptor.DUMMY_HOST)
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(CoroutineCallAdapterFactory())
                .client(client)
                .build()
                .create(WiliotEdgeApiService::class.java)
        }
        return apiService!!
    }

}

fun apiService() = WiliotEdgeApi.retrofitService()
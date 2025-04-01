package com.wiliot.wiliotcore.embedded.auth

import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

internal interface WltAuthApiService {

    @POST
    fun apiKeyToAuth(
        @Url url: String,
        @Header("Authorization") apiKey: String
    ): Call<SdkAuthResponse>

}

internal data class SdkAuthResponse(
    @SerializedName("access_token")
    val accessToken: String
)
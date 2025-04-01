package com.wiliot.wiliotnetworkmeta.api

import com.wiliot.wiliotnetworkmeta.api.model.Gateway
import com.wiliot.wiliotnetworkmeta.api.model.ResponseResolvedBeacon
import kotlinx.coroutines.Deferred
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface WiliotMetaApiService {

    @POST("/v1/owner/{ownerId}/resolve")
    fun resolveBeaconDataAsync(
        @Body gateway: Gateway,
        @Path("ownerId", encoded = true) ownerId: String
    ): Deferred<Response<ResponseResolvedBeacon>>

}
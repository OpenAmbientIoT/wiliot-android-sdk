package com.wiliot.wiliotnetworkedge.api

import com.wiliot.wiliotnetworkedge.api.model.BridgeResponse
import kotlinx.coroutines.Deferred
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface WiliotEdgeApiService {

    @GET("/v1/owner/{ownerId}/bridge/{bridgeId}")
    fun resolveBridgeAsync(
        @Path("ownerId", encoded = true) ownerId: String,
        @Path("bridgeId", encoded = true) bridgeId: String
    ): Deferred<Response<BridgeResponse>>

}
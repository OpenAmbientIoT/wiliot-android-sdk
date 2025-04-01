package com.wiliot.wiliotnetworkmeta.gql

import com.wiliot.wiliotnetworkmeta.gql.model.ApiRequest
import com.wiliot.wiliotnetworkmeta.gql.model.AssetsResponse
import kotlinx.coroutines.Deferred
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface GQLApiService {

    @POST("/v1/traceability/owner/{ownerId}/metadataFetch")
    fun askForAssetsAsync(
        @Path("ownerId", encoded = true) ownerId: String,
        @Body body: ApiRequest
    ): Deferred<Response<AssetsResponse>>

}
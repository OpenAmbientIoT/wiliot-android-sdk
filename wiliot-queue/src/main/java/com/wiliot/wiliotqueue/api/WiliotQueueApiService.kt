package com.wiliot.wiliotqueue.api

import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotqueue.api.model.LoginResponse
import com.wiliot.wiliotqueue.api.model.MqttRegistryResponse
import com.wiliot.wiliotqueue.api.model.RegisterGWBody
import kotlinx.coroutines.Deferred
import retrofit2.Response
import retrofit2.http.*

interface WiliotQueueApiService {

    @POST("/v1/owner/{ownerId}/gateway/{gatewayId}/mobile")
    fun registerGWAsync(
        @Path("ownerId", encoded = true) ownerId: String,
        @Path("gatewayId", encoded = true) gatewayId: String = Wiliot.getFullGWId(),
        @Body body: RegisterGWBody = RegisterGWBody(),
    ): Deferred<Response<MqttRegistryResponse>>

    @Headers("noAuth: true")
    @POST("/v1/gateway/refresh?")
    fun refreshGWTokenAsync(
        @Query("refresh_token", encoded = true) refresh_token: String,
    ): Deferred<Response<LoginResponse>>

}
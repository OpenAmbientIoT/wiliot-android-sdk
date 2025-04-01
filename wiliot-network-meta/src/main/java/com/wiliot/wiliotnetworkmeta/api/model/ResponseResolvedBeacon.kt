package com.wiliot.wiliotnetworkmeta.api.model

data class ResponseResolvedBeacon(
    val data: List<ApiPacket>,
    val message: String
)
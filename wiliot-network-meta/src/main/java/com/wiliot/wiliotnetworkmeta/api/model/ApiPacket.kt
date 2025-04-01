package com.wiliot.wiliotnetworkmeta.api.model

import com.wiliot.wiliotcore.model.Asset

data class ApiPacket(
    val externalId: String,
    val timestamp: Long,
    val ownerId: String?,
    val labels: List<String>?,
    val asset: Asset?
)
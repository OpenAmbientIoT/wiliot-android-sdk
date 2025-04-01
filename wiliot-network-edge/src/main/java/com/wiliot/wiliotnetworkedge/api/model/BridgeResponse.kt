package com.wiliot.wiliotnetworkedge.api.model

import com.google.gson.annotations.SerializedName
import com.wiliot.wiliotcore.model.*

data class BridgeResponse(
    val data: BridgeModel,
)

data class BridgeModel(
    val id: String,
    val name: String?,
    val version: String,
    val claimed: Boolean,
    val owned: Boolean,
    val config: BridgeConfigModel?,
    val desiredConfig: BridgeConfigModel?,
    val zone: ZoneModel?,
    val location: LocationModel?,
    val connections: List<BridgeConnectionModel>?,
    val modules: BridgeConfigModules?,
    val boardType: String?
)

data class BridgeConnectionModel(
    val gatewayId: String,
    val connected: Boolean,
    val connectionUpdatedAt: Long,
)

data class BridgeConfigModel(
    val pacingMode: Int? = null,
    val txPeriodMs: Int? = null,
    val rxTxPeriodMs: Int? = null,
    val txRepetition: Int? = null,
    val energyPattern: Int? = null,
    val pacerInterval: Int? = null,
    val txProbability: Int? = null,
    val sub1GhzFrequency: Int? = null,
    @SerializedName("2.4GhzOutputPower")
    val _2_4GhzOutputPower: Int? = null,
    val otaUpgradeEnabled: Int? = null,
    val globalPacingGroup: Int? = null,
    val globalPacingEnabled: Int? = null,
    val sub1GhzOutputPower: Int? = null,
)

fun BridgeResponse.parseResponse(): Bridge {
    return Bridge(
        id = data.id,
        name = data.name,
        claimed = data.claimed,
        owned = data.owned,
        fwVersion = data.version,
        pacingRate = data.config?.pacerInterval,
        energizingRate = data.config?.energyPattern,
        connections = data.connections?.map {
            BridgeConnection(
                gatewayId = it.gatewayId,
                connected = it.connected,
                connectionUpdatedAt = it.connectionUpdatedAt
            )
        },
        zone = data.zone.validObjectOrNull(),
        location = data.location.validObjectOrNull(),
        resolved = true,
        processedButNotResolved = false,
        rawReportedConfig = data.config?.toDomainBridgeConfig(),
        rawDesiredConfig = data.desiredConfig?.toDomainBridgeConfig(),
        modules = data.modules,
        boardType = data.boardType
    )
}

fun BridgeModel.toDomainBridge(): Bridge {
    return Bridge(
        id = id,
        name = name,
        claimed = claimed,
        owned = owned,
        fwVersion = version,
        pacingRate = config?.pacerInterval,
        energizingRate = config?.energyPattern,
        zone = zone.validObjectOrNull(),
        connections = this.connections?.map {
            BridgeConnection(
                gatewayId = it.gatewayId,
                connected = it.connected,
                connectionUpdatedAt = it.connectionUpdatedAt
            )
        },
        location = location.validObjectOrNull(),
        resolved = true,
        processedButNotResolved = false,
        modules = modules,
        boardType = boardType
    )
}

fun BridgeConfigModel.toDomainBridgeConfig(): BridgeConfig {
    return BridgeConfig(
        pacingMode = this.pacingMode,
        txPeriodMs = this.txPeriodMs,
        rxTxPeriodMs = this.rxTxPeriodMs,
        txRepetition = this.txRepetition,
        energyPattern = this.energyPattern,
        pacerInterval = this.pacerInterval,
        txProbability = this.txProbability,
        sub1GhzFrequency = this.sub1GhzFrequency,
        _2_4GhzOutputPower = this._2_4GhzOutputPower,
        globalPacingGroup = this.globalPacingGroup,
        otaUpgradeEnabled = this.otaUpgradeEnabled,
        sub1GhzOutputPower = this.sub1GhzOutputPower
    )
}

private fun ZoneModel?.validObjectOrNull(): CZone? {
    if (this == null) return null
    if (this.id == null) return null
    if (this.name == null) return null
    return CZone(id, name)
}

private fun LocationModel?.validObjectOrNull(): CLocation? {
    if (this == null) return null
    if (this.id == null) return null
    if (this.name == null) return null
    return CLocation(id, name, address, locationType)
}
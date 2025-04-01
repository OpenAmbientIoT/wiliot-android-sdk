package com.wiliot.wiliotcore.contracts

import com.wiliot.wiliotcore.model.Asset
import com.wiliot.wiliotcore.model.AssetWrapper
import com.wiliot.wiliotcore.model.IResolveInfo
import com.wiliot.wiliotcore.model.IResolveInfoImpl
import com.wiliot.wiliotcore.model.PacketData

interface MetaNetworkManagerContract {
    suspend fun resolvePacketData(packetData: PacketData): IResolveInfoImpl?
    suspend fun askForAssets(packet: IResolveInfo, ignoreFlowVersion: Boolean = false): List<Asset>?
    suspend fun askForAssetByPixelId(pixelId: String): AssetWrapper
}
package com.wiliot.wiliotcore.contracts

import com.wiliot.wiliotcore.model.PacketData

interface UpstreamExtraDataProcessingContract {

    fun stop()

    fun start()

    fun removeOutdatedBeaconsData(outdated: Long)

    fun clearAllBeaconsMetadata()

    fun processSignalLevel(set: Set<PacketData>, isProMode: Boolean)

    fun processStoredBeaconAddition(packet: PacketData)

    fun processNewBeaconAddition(packet: PacketData)

    fun flagBeacons(shouldUpdate: Boolean)

}
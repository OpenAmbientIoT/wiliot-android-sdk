package com.wiliot.wiliotcore.contracts

import com.wiliot.wiliotcore.model.DataPacket

interface VirtualBridgeContract {
    fun addDirectPacket(dataPacket: DataPacket)
    fun addMelPacket(packetRaw: String)
}
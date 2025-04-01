package com.wiliot.wiliotcore.contracts

import com.wiliot.wiliotcore.model.Packet

/**
 * Upstream channel to add Virtual Bridge payloads to upload queue.
 */
interface UpstreamVirtualBridgeChannel {
    fun sendPackets(packets: List<Packet>)
}
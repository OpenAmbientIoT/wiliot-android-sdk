package com.wiliot.wiliotvirtualbridge.utils

import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.model.BridgeHbPacketAbstract
import com.wiliot.wiliotcore.model.BridgeHbPacketV5
import com.wiliot.wiliotcore.utils.ScanResultInternal
import com.wiliot.wiliotcore.utils.toHexString
import java.nio.ByteBuffer

internal object HeartBeatPacketGenerator {

    internal fun generateHeartbeatPacket(
        sequenceId: Int,
        /**
         * All the received packets since last HB
         */
        receivedPackets: Int,

        /**
         * All the sent packets since last HB
         */
        sentPackets: Int,

        /**
         * All the unique pixels since HB
         */
        uniqueTagsMacAddresses: Int
    ): BridgeHbPacketAbstract {
        return BridgeHbPacketV5(
            value = generatePayload(
                sequenceId = sequenceId,
                receivedPackets = receivedPackets,
                sentPackets = sentPackets,
                uniqueTagsMacAddresses = uniqueTagsMacAddresses
            ),
            scanResult = ScanResultInternal(
                rssi = 0,
                device = ScanResultInternal.Device(
                    name = null,
                    address = generateMacFromString(Wiliot.getFullGWId())
                ),
                isConnectable = false
            ),
            timestamp = System.currentTimeMillis()
        )
    }

    private fun generatePayload(
        sequenceId: Int,
        /**
         * All the received packets since last HB
         */
        receivedPackets: Int,

        /**
         * All the sent packets since last HB
         */
        sentPackets: Int,

        /**
         * All the unique pixels since HB
         */
        uniqueTagsMacAddresses: Int
    ): String {
        val msgType: Int = 2
        val apiVersion: Int = 10
        val brgMac: String = generateMacFromString(Wiliot.getFullGWId()).replace(":", "")
        val nonWltRxPktsCtr: Int = 0
        val badCrcPkts: Int = 0
        val dynamic: Boolean = false
        val effectivePacer: Int = 0
        val txQueueWatermark: Int = 0

        val payload = generateUsefulPayload(
            msgType = msgType,
            apiVersion = apiVersion,
            seqId = sequenceId,
            brgMac = brgMac,
            nonWltRxPktsCtr = nonWltRxPktsCtr,
            badCrcPktsCtr = badCrcPkts,
            wltRxPktsCtr = receivedPackets,
            wltTxPktsCtr = sentPackets,
            tagsCtr = uniqueTagsMacAddresses,
            txQueueWatermark = txQueueWatermark,
            dynamic = dynamic,
            effectivePacerIncrement = effectivePacer
        )

        return "C6FC0000EE$payload".uppercase()
    }

    private fun generateUsefulPayload(
        msgType: Int,
        apiVersion: Int,
        seqId: Int,
        brgMac: String,
        nonWltRxPktsCtr: Int,
        badCrcPktsCtr: Int,
        wltRxPktsCtr: Int,
        wltTxPktsCtr: Int,
        tagsCtr: Int,
        txQueueWatermark: Int,
        dynamic: Boolean,
        effectivePacerIncrement: Int
    ): String {
        val buffer = ByteBuffer.allocate(24)

        // 8 bits - msgType
        buffer.put(msgType.toByte())

        // 8 bits - apiVersion
        buffer.put(apiVersion.toByte())

        // 8 bits - seqId
        buffer.put(seqId.toByte())

        // 48 bits - brgMac
        val macBytes = brgMac.hexStringToByteArray()
        buffer.put(macBytes)

        // 24 bits - nonWltRxPktsCtr
        buffer.put24BitValue(nonWltRxPktsCtr)

        // 24 bits - badCrcPktsCtr
        buffer.put24BitValue(badCrcPktsCtr)

        // 24 bits - wltRxPktsCtr
        buffer.put24BitValue(wltRxPktsCtr)

        // 16 bits - wltTxPktsCtr
        buffer.putShort(wltTxPktsCtr.toShort())

        // 16 bits - tagsCtr
        buffer.putShort(tagsCtr.toShort())

        // 8 bits - txQueueWatermark
        buffer.put(txQueueWatermark.toByte())

        // 1 bit - dynamic, 7 bits - effectivePacerIncrement
        val pacingByte = ((if (dynamic) 1 else 0) shl 7) or (effectivePacerIncrement and 0x7F)
        buffer.put(pacingByte.toByte())

        // Convert buffer to hex string
        return buffer.array().toHexString()
    }

}
package com.wiliot.wiliotvirtualbridge.utils

import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.config.VirtualBridgeConfig
import com.wiliot.wiliotcore.model.MelModulePacket
import com.wiliot.wiliotcore.utils.ScanResultInternal
import com.wiliot.wiliotcore.utils.toHexString
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

internal object MelModulePacketGenerator {

    internal fun generateMelPacket(
        sequenceId: Int,
        config: VirtualBridgeConfig
    ): MelModulePacket {
        return MelModulePacket(
            value = generatePayload(sequenceId, config),
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
        config: VirtualBridgeConfig
    ): String {

        val moduleType = 2
        val msgType = 5
        val apiVersion = 10
        val brgMac = generateMacFromString(Wiliot.getFullGWId()).replace(":", "")
        val globalPacingGroup = 0
        val adaptivePacer = false
        val unifiedEchoPkts = true
        val pacerInterval = (TimeUnit.MILLISECONDS.toSeconds(config.pacingRate)).toInt()
        val pktFilter = 0x00
        val txRepetition = 0
        val commOutputPower = 0
        val commPattern = 0

        val payload = generateUsefulPayload(
            moduleType = moduleType,
            msgType = msgType,
            apiVersion = apiVersion,
            seqId = sequenceId,
            brgMac = brgMac,
            globalPacingGroup = globalPacingGroup,
            adaptivePacer = adaptivePacer,
            unifiedEchoPkt = unifiedEchoPkts,
            pacerInterval = pacerInterval,
            pktFilter = pktFilter,
            txRepetition = txRepetition,
            commOutputPower = commOutputPower,
            commPattern = commPattern
        )

        return "c6fc0000ee$payload".uppercase()
    }

    private fun generateUsefulPayload(
        moduleType: Int,
        msgType: Int,
        apiVersion: Int,
        seqId: Int,
        brgMac: String,
        globalPacingGroup: Int,
        adaptivePacer: Boolean,
        unifiedEchoPkt: Boolean,
        pacerInterval: Int,
        pktFilter: Int,
        txRepetition: Int,
        commOutputPower: Int,
        commPattern: Int
    ): String {
        // Allocate a buffer to hold 24 bytes (192 bits), including 76 bits of padding
        val buffer = ByteBuffer.allocate(24)

        // Pack moduleType and msgType into a single byte (4 bits each)
        buffer.put(((moduleType and 0x0F) shl 4 or (msgType and 0x0F)).toByte())

        // Pack apiVersion and seqId into 1 byte each
        buffer.put(apiVersion.toByte())
        buffer.put(seqId.toByte())

        // Pack brgMac (48 bits = 6 bytes)
        val macBytes = brgMac.hexStringToByteArray()
        buffer.put(macBytes)

        // Pack globalPacingGroup, unused bits, adaptivePacer, and unifiedEchoPkt into a single byte
        val pacingGroupByte = ((globalPacingGroup and 0x0F) shl 4) or
                (0b00 shl 2) or
                ((if (adaptivePacer) 1 else 0) shl 1) or
                (if (unifiedEchoPkt) 1 else 0)
        buffer.put(pacingGroupByte.toByte())

        // Pack pacerInterval (16 bits)
        buffer.putShort(pacerInterval.toShort())

        // Pack pktFilter and txRepetition into a single byte (5 bits for pktFilter, 3 bits for txRepetition)
        val filterRepetitionByte = ((pktFilter and 0x1F) shl 3) or (txRepetition and 0x07)
        buffer.put(filterRepetitionByte.toByte())

        // Pack commOutputPower (8 bits)
        buffer.put(commOutputPower.toByte())

        // Pack commPattern (4 bits) and add padding (4 bits)
        val patternAndPaddingByte = (commPattern and 0x0F) shl 4
        buffer.put(patternAndPaddingByte.toByte())

        // Fill remaining 76 bits (9.5 bytes) with zeroes
        buffer.put(ByteArray(9) { 0.toByte() })

        // Convert buffer to a hexadecimal string
        return buffer.array().toHexString()
    }

}
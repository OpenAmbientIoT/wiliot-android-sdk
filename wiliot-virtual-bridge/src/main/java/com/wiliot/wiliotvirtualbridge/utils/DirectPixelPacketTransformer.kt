package com.wiliot.wiliotvirtualbridge.utils

import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.model.AbstractEchoPacket
import com.wiliot.wiliotcore.model.Ble5EchoPacket
import com.wiliot.wiliotcore.model.DataPacketType
import com.wiliot.wiliotcore.model.Packet.Companion.DATA_PACKET_LEN_CHARS
import com.wiliot.wiliotcore.model.UnifiedEchoPacket
import com.wiliot.wiliotcore.utils.ScanResultInternal
import com.wiliot.wiliotcore.utils.twoComplement
import com.wiliot.wiliotvirtualbridge.repository.PixelPacketGroup

internal object DirectPixelPacketPacerTransformer {

    internal fun PixelPacketGroup.toEchoPacket(): AbstractEchoPacket {
        if (this.lastPacket == null) {
            throw IllegalArgumentException("Can not create AbstractEchoPacket because lastPacket is null")
        }
        val rawPayload = this.lastPacket

        val isBle5 = rawPayload.value.length != DATA_PACKET_LEN_CHARS

        return if (isBle5) toBle5EchoPacket() else toUnifiedEchoPacket()
    }

    private fun PixelPacketGroup.toBle5EchoPacket(): Ble5EchoPacket {
        if (this.lastPacket == null) {
            throw IllegalArgumentException("Can not create UnifiedEchoPacket because lastPacket is null")
        }
        val rawPayload = this.lastPacket

        val length = rawPayload.value.substring(0, 2).toInt(16)
        val meta = generateMeta()

        return rawPayload.value.replaceByte(0) {
            (length + 3).toString(16)
        }.replaceBytes(Pair(2, 3)) {
            DataPacketType.RETRANSMITTED.prefix
        }.let {
            "$it$meta".uppercase()
        }.let {
            Ble5EchoPacket(
                value = it,
                scanResult = ScanResultInternal(
                    rssi = 0,
                    isConnectable = false,
                    device = ScanResultInternal.Device(
                        name = null,
                        address = generateMacFromString(Wiliot.getFullGWId()).replace(":", "")
                    )
                ),
                timestamp = System.currentTimeMillis()
            )
        }
    }

    private fun PixelPacketGroup.toUnifiedEchoPacket(): UnifiedEchoPacket {
        if (this.lastPacket == null) {
            throw IllegalArgumentException("Can not create UnifiedEchoPacket because lastPacket is null")
        }
        val rawPayload = this.lastPacket

        return rawPayload.value
            .replaceBytes(Pair(0, 1)) {
                DataPacketType.RETRANSMITTED.prefix
            }
            .replaceByte(4) { toUInt(16).or((0x3F).toUInt()).toString(16) } // adjusting GroupIdMajor with 0x3F
            .replaceBytes(Pair(15, 17)) {
                generateMeta()
            }.let {
                UnifiedEchoPacket(
                    value = it.uppercase(),
                    scanResult = ScanResultInternal(
                        rssi = 0,
                        isConnectable = false,
                        device = ScanResultInternal.Device(
                            name = null,
                            address = generateMacFromString(Wiliot.getFullGWId()).replace(":", "")
                        )
                    ),
                    timestamp = System.currentTimeMillis()
                )
            }
    }

    private fun PixelPacketGroup.generateMeta(): String {
        if (this.lastPacket == null) {
            throw IllegalArgumentException("Can not create meta because lastPacket is null")
        }
        val nfpkt = this.counter.takeIf { it <= 255 } ?: 255
        val rssi = this.lastPacket.scanRssi.twoComplement()
        val brgLatency = 0
        val globalPacing = 0

        val result = ((nfpkt and 0xFF) shl 16) or
                ((rssi and 0x3F) shl 10) or
                ((brgLatency and 0x3F) shl 4) or
                (globalPacing and 0x0F)
        return String.format("%06X", result)
    }

}
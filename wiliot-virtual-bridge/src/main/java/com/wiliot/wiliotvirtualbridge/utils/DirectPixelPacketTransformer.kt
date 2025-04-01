package com.wiliot.wiliotvirtualbridge.utils

import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.model.CombinedSiPacket
import com.wiliot.wiliotcore.model.DataPacketType
import com.wiliot.wiliotcore.utils.ScanResultInternal
import com.wiliot.wiliotcore.utils.twoComplement
import com.wiliot.wiliotvirtualbridge.repository.PixelPacketGroup

internal object DirectPixelPacketPacerTransformer {

    internal fun PixelPacketGroup.toEchoPacket(): CombinedSiPacket {
        if (this.lastPacket == null) {
            throw IllegalArgumentException("Can not create CombinedSiPacket because lastPacket is null")
        }
        val rawPayload = this.lastPacket

        return rawPayload.value
            .replaceBytes(Pair(0, 1)) {
                DataPacketType.RETRANSMITTED.prefix
            }
            .replaceByte(4) { toUInt(16).or((0x3F).toUInt()).toString(16) } // adjusting GroupIdMajor with 0x3F
            .replaceBytes(Pair(15, 17)) {
                val nfpkt = this@toEchoPacket.counter.takeIf { it <= 255 } ?: 255
                val rssi = rawPayload.scanRssi.twoComplement()
                val brgLatency = 0
                val globalPacing = 0

                val result = ((nfpkt and 0xFF) shl 16) or
                        ((rssi and 0x3F) shl 10) or
                        ((brgLatency and 0x3F) shl 4) or
                        (globalPacing and 0x0F)
                String.format("%06X", result)
            }.let {
                CombinedSiPacket(
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

}
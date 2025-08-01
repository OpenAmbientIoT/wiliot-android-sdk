package com.wiliot.wiliotqueue.mqtt.model

import com.wiliot.wiliotcore.WiliotCounter
import com.wiliot.wiliotcore.model.BridgeHbPacketAbstract
import com.wiliot.wiliotcore.model.BridgePacketAbstract
import com.wiliot.wiliotcore.model.CombinedSiPacket
import com.wiliot.wiliotcore.model.DataPacketType
import com.wiliot.wiliotcore.model.MelModulePacket
import com.wiliot.wiliotcore.model.PacketData
import java.io.Serializable

abstract class MQTTBaseData(
    open val payload: String?,
    open val rssi: Int?,
    open val timestamp: Long = System.currentTimeMillis(),
) : Serializable {

    val sequenceId: Long = WiliotCounter.value.also { WiliotCounter.inc() }

}

// Wrapper for Bridge Config Packets, Heartbeats, Mel Packets
data class PackedEdgeDataMQTT(
    override val payload: String?,
    override val rssi: Int?,
    override val timestamp: Long = System.currentTimeMillis(),
    val aliasBridgeId: String?
) : MQTTBaseData(payload, rssi, timestamp)

// Base wrapper for data+meta coupled (or not) packets
abstract class PackedDataMQTT(
    override val payload: String?,
    override val rssi: Int?,
    override val timestamp: Long = System.currentTimeMillis(),
) : MQTTBaseData(payload, rssi, timestamp)

// Wrapper for regular data+meta (or data) packets
class PackedDataMetaMQTT(
    override val payload: String?,
    override val rssi: Int?,
    override val timestamp: Long = System.currentTimeMillis(),
    val aliasBridgeId: String? = null, // for mac addresses
    val retransmitted: Boolean
) : PackedDataMQTT(payload, rssi, timestamp) {
    companion object {

        private fun PacketData.isCombinedSiData(): Boolean = packet is CombinedSiPacket

        private fun PacketData.isRetransmittedData(): Boolean = packet.value.startsWith(DataPacketType.RETRANSMITTED.prefix, ignoreCase = true)

        private fun PacketData.isSensorData(): Boolean = packet.value.startsWith(DataPacketType.SENSOR_DATA.prefix, ignoreCase = true)

        fun fromPacketData(packetData: PacketData) = when {
            packetData.isCombinedSiData() -> PackedDataMetaMQTT(
                payload = packetData.data,
                rssi = packetData.rssi,
                timestamp = packetData.timestamp,
                aliasBridgeId = (packetData.packet as CombinedSiPacket).aliasBridgeId,
                retransmitted = true
            )

            else -> if (packetData.isRetransmittedData() || packetData.isSensorData()) PackedDataMetaMQTT(
                payload = packetData.data,
                rssi = packetData.rssi,
                timestamp = packetData.timestamp,
                retransmitted = true // packetData.isRetransmittedData() || packetData.isSensorData()
            ) else null
        }

    }

}

data class PackedDataInternalSensorMQTT(
    override val timestamp: Long,
    override val rssi: Int? = 0,
    override val payload: String?,
    val isSensor: Boolean = true,
    val isEmbedded: Boolean = true,
    val isScrambled: Boolean = false,
    val nfpkt: Int = 0,
    val sensorServiceId: String = "00000a",
    val sensorId: String
) : PackedDataMQTT(payload, rssi, timestamp)

//==============================================================================================
// *** Utils ***
//==============================================================================================

// region [Utils]

fun BridgeHbPacketAbstract.toMqttData(): PackedEdgeDataMQTT {
    return PackedEdgeDataMQTT(
        payload = value,
        rssi = scanRssi,
        timestamp = timestamp,
        aliasBridgeId = aliasDeviceId().takeIf { this@toMqttData.apiVersion >= 9u }
    )
}

fun MelModulePacket.toMqttData(): PackedEdgeDataMQTT {
    return PackedEdgeDataMQTT(
        payload = value,
        rssi = scanRssi,
        timestamp = timestamp,
        aliasBridgeId = aliasDeviceId().takeIf { this@toMqttData.apiVersion >= 9u }
    )
}

fun BridgePacketAbstract.toMqttData(): PackedEdgeDataMQTT {
    return PackedEdgeDataMQTT(
        payload = value,
        rssi = scanRssi,
        timestamp = timestamp,
        aliasBridgeId = null
    )
}

fun PacketData.toMqttData(): PackedDataMetaMQTT? {
    return PackedDataMetaMQTT.fromPacketData(this)
}

// endregion

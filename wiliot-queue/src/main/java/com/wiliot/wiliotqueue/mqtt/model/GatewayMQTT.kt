package com.wiliot.wiliotqueue.mqtt.model

import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.config.Configuration
import com.wiliot.wiliotcore.model.Location
import java.io.Serializable
import java.util.Date

@Suppress("unused")
internal class GatewayMQTT(
    packetList: List<MQTTBaseData>,
    private val location: Location?,
) : Serializable {

    private val gatewayId: String = Wiliot.getFullGWId()
    private val gatewayType: String = if (Wiliot.configuration.cloudManaged) Configuration.MDK_GATEWAY_TYPE else Configuration.SOFTWARE_GATEWAY_TYPE
    private val gatewayName: String = Wiliot.getFullGWId()

    private val timestamp: Long = System.currentTimeMillis()
    private var packets: List<MQTTBaseData> = ArrayList()

    init {
        this.packets += packetList
    }
}
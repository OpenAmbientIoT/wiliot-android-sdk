package com.wiliot.wiliotnetworkmeta.api.model

import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.config.Configuration
import com.wiliot.wiliotcore.model.PackedData
import java.io.Serializable

@Suppress("unused")
/* internal */ class Gateway(
    packetList: ArrayList<PackedData>,
    private val ambientTemperature: Float?,
    private val deviceTemperature: Float?
) : Serializable {

    private val gatewayId: String = Wiliot.getFullGWId()
    private val gatewayType: String = if (Wiliot.configuration.cloudManaged) Configuration.MDK_GATEWAY_TYPE else Configuration.SOFTWARE_GATEWAY_TYPE
    private val timestamp: Long = System.currentTimeMillis()
    private var packets: List<PackedData> = ArrayList()

    init {
        this.packets += packetList
    }
}
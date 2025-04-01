package com.wiliot.wiliotcore.model

import com.google.gson.annotations.SerializedName
import com.wiliot.wiliotcore.Wiliot

open class SharedGatewayModel(
    @Transient open val gatewayId: String,
    @Transient open val gatewayType: String,
    @Transient open val gatewayConf: GatewayConfig,
)

data class GatewayConfig(
    val apiVersion: String = "203",
    val gatewayVersion: String,
    val bleAddress: String? = Wiliot.virtualBridgeId?.replace(":", ""),
    val additional: AdditionalGatewayConfig?
)

data class AdditionalGatewayConfig(
    @SerializedName("pacingPeriod")
    val pacerIntervalSeconds: Long?,
    val upstreamEnabled: Boolean?,
    @SerializedName("uploadPixelsTraffic")
    val pixelsTrafficEnabled: Boolean?,
    @SerializedName("uploadConfigurationTraffic")
    val edgeTrafficEnabled: Boolean?,
    val bleLogsEnabled: Boolean?,
    val versionName: String? = null,
    @SerializedName("gwDataMode")
    val dataOutputTrafficFilter: String?
) {
    enum class DataOutputTrafficFilter {

        /**
         * [PIXELS_ONLY] means that only [DataPacket] that are Direct Pixel's packets will be
         * delivered to the Cloud. Note, that such packets will be processed through the
         * :wiliot-virtual-bridge module
         */
        PIXELS_ONLY,

        /**
         * [BRIDGES_ONLY] means that only physically retransmitted packets will be delivered to the
         * Cloud. All the Direct Pixel's packets will be skipped.
         */
        BRIDGES_ONLY,

        /**
         * [BRIDGES_AND_PIXELS] means that all the data will be delivered to the Cloud. Note,
         * that Direct Pixel's packets will be processed through the :wiliot-virtual-bridge module
         */
        BRIDGES_AND_PIXELS;

        companion object {
            fun fromSerial(serial: String) = entries.firstOrNull { it.name == serial }
        }
    }
}

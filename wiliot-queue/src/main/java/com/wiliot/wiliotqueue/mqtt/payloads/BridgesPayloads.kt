package com.wiliot.wiliotqueue.mqtt.payloads

import com.google.gson.annotations.SerializedName
import com.wiliot.wiliotcore.FrameworkDelegate.Companion.extractSemanticVersion
import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.config.Configuration
import com.wiliot.wiliotcore.model.AdditionalGatewayConfig
import com.wiliot.wiliotcore.model.GatewayConfig
import com.wiliot.wiliotcore.model.SharedGatewayModel
import com.wiliot.wiliotcore.utils.helper.WiliotAppConfigurationSource
import java.util.concurrent.TimeUnit

data class PacketsLogPayload(
    val gatewayLogs: List<Any>
)

data class SoftwareGatewayCapabilitiesPayload(
    override val gatewayId: String,
    override val gatewayType: String,
    override val gatewayConf: GatewayConfig,
    val tagMetadataCouplingSupported: Boolean,
    val downlinkSupported: Boolean,
    val bridgeOtaUpgradeSupported: Boolean,
    val fwUpgradeSupported: Boolean = false
) : SharedGatewayModel(gatewayId = gatewayId, gatewayType = gatewayType, gatewayConf = gatewayConf) {
    companion object {
        fun create(
            cloudManaged: Boolean
        ) = SoftwareGatewayCapabilitiesPayload(
            gatewayId = Wiliot.getFullGWId(),
            gatewayType = if (cloudManaged) Configuration.MDK_GATEWAY_TYPE else Configuration.SOFTWARE_GATEWAY_TYPE,
            tagMetadataCouplingSupported = false,
            downlinkSupported = true,
            bridgeOtaUpgradeSupported = cloudManaged,
            gatewayConf = GatewayConfig(
                additional = AdditionalGatewayConfig(
                    pacerIntervalSeconds = TimeUnit.MILLISECONDS.toSeconds(Wiliot.configuration.pacingPeriodMs),
                    upstreamEnabled = WiliotAppConfigurationSource.configSource.isUpstreamEnabled(),
                    pixelsTrafficEnabled = Wiliot.configuration.uploadPixelsTraffic,
                    edgeTrafficEnabled = Wiliot.configuration.uploadConfigurationTraffic,
                    bleLogsEnabled = WiliotAppConfigurationSource.configSource.isBleLogsEnabled(),
                    dataOutputTrafficFilter = Wiliot.configuration.dataOutputTrafficFilter.name,
                    versionName = Wiliot.delegate.applicationVersionName().extractSemanticVersion()
                ),
                gatewayVersion = Wiliot.delegate.applicationVersionName().extractSemanticVersion()
            )
        )
    }
}

@Deprecated("No need to use this model")
data class DownlinkHeartbeatPayload(
    val info: String = "DownlinkHeartbeat",
    val gwId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val receivedMessages: Int
)

data class GatewayInfo(
    @SerializedName("hardwareStatus")
    val hwStatus: String? = null,
    @SerializedName("batteryStatus")
    val bStatus: String? = null,
    @SerializedName("batteryCurrentMicroAmps")
    val bCurrentMicroAmp: Long? = null,
    @SerializedName("batteryCurrentAvgMicroAmps")
    val bCurrentAvgMicroAmp: Int? = null,
    @SerializedName("batteryChargeLevel")
    val bCapacity: Int? = null
)

data class MDKStatusPayload(
    override val gatewayId: String,
    override val gatewayType: String,
    override val gatewayConf: GatewayConfig,
    var gatewayInfo: GatewayInfo? = null,
    val tagMetadataCouplingSupported: Boolean,
    val downlinkSupported: Boolean,
    val bridgeOtaUpgradeSupported: Boolean,
    val fwUpgradeSupported: Boolean = false
): SharedGatewayModel(gatewayType = gatewayType, gatewayId = gatewayId, gatewayConf = gatewayConf) {
    companion object {
        fun create(
            hwStatus: String?,
            bStatus: String,
            bCurrentMicroAmp: Long,
            bCapacity: Int,
            bCurrentAvgMicroAmp: Int,
            cloudManaged: Boolean
        ) = MDKStatusPayload(
            gatewayId = Wiliot.getFullGWId(),
            gatewayType = if (cloudManaged) Configuration.MDK_GATEWAY_TYPE else Configuration.SOFTWARE_GATEWAY_TYPE,
            gatewayConf = GatewayConfig(
                additional = AdditionalGatewayConfig(
                    pacerIntervalSeconds = TimeUnit.MILLISECONDS.toSeconds(Wiliot.configuration.pacingPeriodMs),
                    upstreamEnabled = WiliotAppConfigurationSource.configSource.isUpstreamEnabled(),
                    pixelsTrafficEnabled = Wiliot.configuration.uploadPixelsTraffic,
                    edgeTrafficEnabled = Wiliot.configuration.uploadConfigurationTraffic,
                    bleLogsEnabled = WiliotAppConfigurationSource.configSource.isBleLogsEnabled(),
                    dataOutputTrafficFilter = Wiliot.configuration.dataOutputTrafficFilter.name,
                    versionName = Wiliot.delegate.applicationVersionName().extractSemanticVersion()
                ),
                gatewayVersion = Wiliot.delegate.applicationVersionName().extractSemanticVersion()
            ),
            gatewayInfo = GatewayInfo(
                hwStatus = hwStatus,
                bStatus = bStatus,
                bCurrentMicroAmp = bCurrentMicroAmp,
                bCurrentAvgMicroAmp = bCurrentAvgMicroAmp,
                bCapacity = bCapacity
            ),
            tagMetadataCouplingSupported = false,
            downlinkSupported = true, // MDK has this option by default
            bridgeOtaUpgradeSupported = true // MDK has this option by default
        )
    }

}

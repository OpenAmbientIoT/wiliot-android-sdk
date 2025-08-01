package com.wiliot.wiliotcore.config

import com.wiliot.wiliotcore.FlowVersion
import com.wiliot.wiliotcore.env.EnvironmentWiliot
import com.wiliot.wiliotcore.env.Environments
import com.wiliot.wiliotcore.model.AdditionalGatewayConfig.DataOutputTrafficFilter
import java.util.concurrent.TimeUnit

data class Configuration(

    // User-identity configs
    /**
     * Owner ID of current session. Required to run SDK. Running SDK with 'null' value
     * will cause an error
     */
    val ownerId: String? = null,
    /**
     * Asset flow version. Kind of legacy to support old accounts. Required to specify this value
     * before running SDK, otherwise it will cause an error. Should be of values [FlowVersion.V1] or
     * [FlowVersion.V2]. In most cases it could be hardcoded to [FlowVersion.V2]
     */
    @Deprecated(
        "FlowVersion.V1 is deprecated and will be removed in future releases along with [flowVersion] config field.",
        ReplaceWith("FlowVersion.V2"),
        DeprecationLevel.WARNING
    )
    val flowVersion: FlowVersion = FlowVersion.V2,
    /**
     * Environment of current session. Required to run SDK.
     * Default value is [Environments.WILIOT_PROD_AWS]
     *
     * To configure SDK for different environments, you can use [Environments] enum.
     *
     * To configure custom environment, you can use [Environments.addCustomEnvironment].
     */
    val environment: EnvironmentWiliot = DEFAULT_ENVIRONMENT,

    // Run flags and configs
    /**
     * Time for the internal data/metadata to be stored in memory.
     * After given time, all outdated data and meta will be deleted from memory to free up resources.
     * This is initial value and it could be changed by SDK during runtime according to available
     * hardware resources
     */
    val expirationLimit: Long = TimeUnit.HOURS.toMillis(1),
    /**
     * Flag for “immortality” of app. App and services will be relaunched automatically in case of
     * crash or any other issues that leads application down
     */
    val phoenix: Boolean = false,
    /**
     * Flag that defines SDK configuration source: either by Cloud commands or by local configuration;
     * Default value is 'true', and you shouldn't disable it without good reason (TEST/DEBUG purposes)
     */
    val cloudManaged: Boolean = true,

    // SDK config
    /**
     * A flag that determines whether special BLE packets will be broadcast by the mobile GW.
     * It helps to calibrate Pixels transmission mechanism.
     * This flag should be set to ‘true’ in most cases
     */
    val isBleAdvertisementEnabled: Boolean = true,
    /**
     * Flag that enables/disables uploading data/meta/sensor packets to the Cloud.
     * Setting it to 'false' will disable all data transmission to the Cloud for both:
     * Direct Pixel Packets and Retransmission by Bridges Pixel Packets, and also for sensors.
     */
    val enableDataTraffic: Boolean = true,
    /**
     * Flag that enables/disables uploading Edge devices packets to the Cloud
     * (like Bridge configurations, heartbeats, interface packets, module packets etc).
     */
    val enableEdgeTraffic: Boolean = true,
    /**
     * Flag that enables/disables MQTT data transmission.
     * If it set to 'true' no data will be delivered to the Cloud.
     */
    val excludeMqttTraffic: Boolean = false,
    /**
     * Data filtering before sending to the Cloud.
     * For more details look at [DataOutputTrafficFilter] enum
     */
    val dataOutputTrafficFilter: DataOutputTrafficFilter = DataOutputTrafficFilter.BRIDGES_AND_PIXELS,
    /**
     * Debug flag to count received BT packets. In most cases there is no need to enable it.
     */
    val btPacketsCounterEnabled: Boolean = false
) {

    companion object {
        val DEFAULT_ENVIRONMENT = Environments.WILIOT_PROD_AWS

        const val SDK_GATEWAY_TYPE = "android"
    }

    /**
     * Returns 'true' if all the necessary configuration fields configured properly.
     * It should be used before starting SDK to make sure that SDK could run with given configuration.
     */
    fun isValid(): Boolean {
        return ownerId != null && flowVersion != FlowVersion.INVALID
    }

    fun toFullString(): String {
        return "Configuration(" +
                "ownerId=$ownerId, " +
                "flowVersion=$flowVersion, " +
                "environment=$environment, " +
                "isBleAdvertisementEnabled=$isBleAdvertisementEnabled, " +
                "uploadPixelsTraffic=$enableDataTraffic, " +
                "uploadConfigurationTraffic=$enableEdgeTraffic, " +
                "dataOutputTrafficFilter=$dataOutputTrafficFilter" +
                ")"
    }

    override fun toString(): String {
        return "Configuration(" +
                "environment=$environment, " +
                "ownerId=$ownerId, " +
                "flowVersion=$flowVersion" +
                ")"
    }

}

package com.wiliot.wiliotcore.config

import com.wiliot.wiliotcore.FlowVersion
import com.wiliot.wiliotcore.legacy.EnvironmentWiliot
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
    val flowVersion: FlowVersion = FlowVersion.INVALID,
    /**
     * Environment to perform in. For regular use-case should be [EnvironmentWiliot.PROD_AWS] or
     * [EnvironmentWiliot.PROD_GCP]
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
    val precisePositioningEnabled: Boolean = false,

    // SDK config
    /**
     * A flag that determines whether special BLE packets will be broadcast by the mobile GW.
     * It helps to calibrate Pixels transmission mechanism.
     * This flag should be set to ‘true’ in most cases
     */
    val isBleAdvertisementEnabled: Boolean = true,
    /**
     * Pacing period in milliseconds
     */
    val pacingPeriodMs: Long = DEFAULT_PACING_PERIOD_MS,
    /**
     * Flag that enables/disables uploading Pixels data/meta packets to the Cloud.
     */
    val uploadPixelsTraffic: Boolean = true,
    /**
     * Flag that enables/disables uploading Edge devices packets to the Cloud
     * (like Bridge configurations, heartbeats etc).
     */
    val uploadConfigurationTraffic: Boolean = true,
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
        val DEFAULT_ENVIRONMENT = EnvironmentWiliot.PROD_AWS
        const val DEFAULT_PACING_PERIOD_MS: Long = 10_000 // ms

        const val SDK_GATEWAY_TYPE = "android"

        const val DEFAULT_BRIDGE_PRESENCE_TIMEOUT_MS: Long = 120_000 // 2 min
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
                "pacingPeriod=$pacingPeriodMs, " +
                "uploadPixelsTraffic=$uploadPixelsTraffic, " +
                "uploadConfigurationTraffic=$uploadConfigurationTraffic, " +
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

package com.wiliot.wiliotcore.model

import com.google.gson.annotations.SerializedName
import com.wiliot.wiliotcore.BuildConfig
import com.wiliot.wiliotcore.utils.Reporter
import java.time.Instant
import kotlin.math.max

enum class Sub1gOutputPower(val profile: UInt, val value: UInt) {
    SUB1G_OUTPUT_POWER_14_PROFILE(0u, 14u),
    SUB1G_OUTPUT_POWER_17_PROFILE(1u, 17u),
    SUB1G_OUTPUT_POWER_20_PROFILE(2u, 20u),
    SUB1G_OUTPUT_POWER_23_PROFILE(3u, 23u),
    SUB1G_OUTPUT_POWER_26_PROFILE(4u, 26u),
    SUB1G_OUTPUT_POWER_29_PROFILE(5u, 29u),
    SUB1G_OUTPUT_POWER_32_PROFILE(6u, 32u);

    companion object {
        fun parse(profile: UInt) = values().firstOrNull {
            it.profile == profile
        }?.value ?: SUB1G_OUTPUT_POWER_32_PROFILE.value
    }
}

enum class Sub1gFrequency(val profile: UInt, val value: UInt) {
    SUB1G_FREQ_915_MHz_PROFILE   (0u, 915000u),
    SUB1G_FREQ_865_7_MHz_PROFILE (1u, 865700u),
    SUB1G_FREQ_916_3_MHz_PROFILE (2u, 916300u),
    SUB1G_FREQ_917_5_MHz_PROFILE (3u, 917500u),
    SUB1G_FREQ_918_MHz_PROFILE   (4u, 918000u),
    SUB1G_FREQ_919_1_MHz_PROFILE (5u, 919100u);

    companion object {
        fun parse(profile: UInt) = values().firstOrNull {
            it.profile == profile
        }?.value ?: SUB1G_FREQ_915_MHz_PROFILE.value
    }
}

data class BridgeConfiguration(
    val txProbability: UInt? = null,
    val energyPattern: UInt? = null,
    val globalPacingEnabled: UInt? = null,
    val txPeriodMs: UInt? = null,
    @SerializedName("2.4GhzOutputPower")
    val _2_4GhzOutputPower: UInt? = null,
    val rxTxPeriodMs: UInt? = null,
    val pacerInterval: Long? = null,
    val sub1GhzOutputPower: UInt? = null,
    val sub1GhzFrequency: UInt? = null,
)

data class BridgeStatus(
    val id: String,
    var packet: Packet? = null,
    var boardType: UInt? = null,
    var version: String? = null,
    var config: BridgeConfiguration? = null,
    var formationType: FormationType,
    var timestamp: Long = Instant.now().toEpochMilli()
) {

    enum class FormationType {
        FROM_SIDE_INFO, FROM_CONFIG_PKT, FROM_EARLY_PKT, FROM_MEL, FROM_HB, SYNTHETIC
    }

    fun meaningFullUpdateUsing(newStatus: BridgeStatus): Boolean {
        if (newStatus.id.contentEquals(id).not())
            return false // double check the id. should be available only for the same id
        timestamp = max(timestamp, newStatus.timestamp)
        var hasMeaning = false
        if (newStatus.timestamp == timestamp) {
            newStatus.version?.let {
                if (!it.contentEquals(version)) {
                    hasMeaning = true
                    version = it
                }
            }
            newStatus.boardType?.let {
                if (it != boardType) {
                    hasMeaning = true
                    boardType = it
                }
            }
            newStatus.config?.let {
                if (config == it) {
                } else {
                    hasMeaning = true
                    config = it
                }
            }
            if (hasMeaning) {
                packet = newStatus.packet
                formationType = newStatus.formationType
            }
        }
        return hasMeaning
    }

    init {
        if (BuildConfig.DEBUG) {
            Reporter.log("BridgeStatus -> $this", "BridgeStatus")
        }
    }
}
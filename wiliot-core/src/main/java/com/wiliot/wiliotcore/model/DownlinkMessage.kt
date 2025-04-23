package com.wiliot.wiliotcore.model

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.stream.MalformedJsonException
import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.model.AdditionalGatewayConfig.DataOutputTrafficFilter
import com.wiliot.wiliotcore.utils.decodeHexToByteArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private val gson = Gson()

data class DownlinkMessage(
    val payload: String?
) {

    fun toDomainMessageOrNull(): DownlinkDomainMessage? {
        return when {
            isActionMessage() -> gson.fromJson(payload, DownlinkActionMessage::class.java)
            isGatewayConfigurationMessage() -> gson.fromJson(payload, DownlinkConfigurationMessage::class.java)
            isCustomBrokerMessage() -> gson.fromJson(payload, DownlinkCustomBrokerMessage::class.java)
            else -> null
        }
    }

    private fun isActionMessage(): Boolean {
        if (payload.isNullOrBlank()) return false
        return try {
            var actionKeyFound = false
            JSONObject(payload).keys().forEach {
                if (it == "action") {
                    actionKeyFound = true
                    return@forEach
                }
            }
            actionKeyFound
        } catch (ex: MalformedJsonException) {
            false
        }
    }

    private fun isCustomBrokerMessage(): Boolean {
        if (payload.isNullOrBlank()) return false
        return try {
            var customBrokerKeyFound = false
            JSONObject(payload).keys().forEach {
                if (it == "customBroker") {
                    customBrokerKeyFound = true
                    return@forEach
                }
            }
            customBrokerKeyFound
        } catch (ex: MalformedJsonException) {
            false
        }
    }

    private fun isGatewayConfigurationMessage(): Boolean {
        if (payload.isNullOrBlank()) return false
        return try {
            var gatewayConfKeyFound = false
            JSONObject(payload).keys().forEach {
                if (it == "gatewayConf") {
                    gatewayConfKeyFound = true
                    return@forEach
                }
            }
            gatewayConfKeyFound
        } catch (ex: MalformedJsonException) {
            false
        }
    }

}

interface DownlinkDomainMessage

data class DownlinkConfigurationMessage(
    override val gatewayId: String,
    override val gatewayType: String,
    override val gatewayConf: GatewayConfig
): SharedGatewayModel(gatewayId = gatewayId, gatewayType, gatewayConf), DownlinkDomainMessage {

    fun isUpstreamEnabled(default: Boolean): Boolean {
        return gatewayConf.additional?.upstreamEnabled ?: default
    }

    fun pacingPeriodMs(default: Long): Long {
        return gatewayConf.additional?.pacerIntervalSeconds?.let { TimeUnit.SECONDS.toMillis(it) } ?: default
    }

    fun isPixelsTrafficEnabled(default: Boolean): Boolean {
        return gatewayConf.additional?.pixelsTrafficEnabled ?: default
    }

    fun isEdgeTrafficEnabled(default: Boolean): Boolean {
        return gatewayConf.additional?.edgeTrafficEnabled ?: default
    }

    fun isBleLogsEnabled(default: Boolean): Boolean {
        return gatewayConf.additional?.bleLogsEnabled ?: default
    }

    fun dataOutputTrafficFilter(default: DataOutputTrafficFilter): DataOutputTrafficFilter {
        return gatewayConf.additional?.dataOutputTrafficFilter?.let { DataOutputTrafficFilter.fromSerial(it) } ?: default
    }

}

data class DownlinkCustomBrokerMessage(
    val customBroker: Boolean,
    val port: Int,
    val brokerUrl: String,
    val username: String,
    val password: String,
    val updateTopic: String,
    val statusTopic: String,
    val dataTopic: String
): DownlinkDomainMessage

data class DownlinkActionMessage(
    // BASE MODEL
    val protocolVersion: Int,
    @SerializedName("action")
    private val _action: Any,
    val gatewayId: String?,
    @SerializedName("txPacket")
    private val _txPacket: String?,
    @SerializedName("txMaxDurationMs")
    private val _txMaxDurationMs: Long?,
    val txMaxRetries: Int?,

    // BRIDGE OTA FIELDS
    @SerializedName("imageDirUrl")
    val bridgeFirmwareDirectoryURL: String?,
    @SerializedName("upgradeBlSd")
    val upgradeBootloader: Boolean?,
    val bridgeId: String?
) : DownlinkDomainMessage {
    val action: DownlinkAction
        get() {
            return when (_action) {
                is Number -> {
                    DownlinkAction.values().firstOrNull {
                        it.serial == _action.toInt()
                    } ?: DownlinkAction.UNKNOWN
                }

                is String -> if (_action.startsWith("-")) {
                    val realAction = _action.toInt()
                    DownlinkAction.values().firstOrNull {
                        it.serial == realAction
                    } ?: DownlinkAction.UNKNOWN
                } else DownlinkAction.ADVERTISE

                else -> DownlinkAction.UNKNOWN
            }
        }

    private val txPacket: String?
        get() = try {
            if (_action is String) _action.split(" ")[1] else _txPacket
        } catch (ex: Exception) {
            _txPacket
        }

    val txMaxDurationMs: Long?
        get() = try {
            (if (_action is String) _action.split(" ").last().toLong() else _txMaxDurationMs)
        } catch (ex: Exception) {
            _txMaxDurationMs
        }

    fun txPacketAsBytesArray(): ByteArray = txPacket.orEmpty().decodeHexToByteArray()

    fun rawTxPacket(): String? = txPacket
}

enum class DownlinkAction(val serial: Int) {
    ADVERTISE(0),
    BRIDGE_OTA(1),
    PREPARE_BRIDGE_IMAGE(2),
    REBOOT_BRIDGE(3),
    UPGRADE_BRIDGE(4),
    CLEAR_IMAGES(5),


    @Deprecated(
        message = "Use downlink configuration message instead",
        level = DeprecationLevel.ERROR
    )
    DISABLE_UPLINK(-2),

    @Deprecated(
        message = "Use downlink configuration message instead",
        level = DeprecationLevel.ERROR
    )
    ENABLE_UPLINK(-3),

    @Deprecated(
        message = "Use downlink configuration message instead",
        level = DeprecationLevel.ERROR
    )
    DISABLE_BLE_LOGS(-4),

    @Deprecated(
        message = "Use downlink configuration message instead",
        level = DeprecationLevel.ERROR
    )
    ENABLE_BLE_LOGS(-5),

    UNKNOWN(-1)
}

val DownlinkMessage.isVmelMessage: Boolean
    get() {
        if (payload == null) return false
        val brgId = Wiliot.virtualBridgeId?.replace(":", "") ?: return false
        // ActionGetModule
        if (payload.uppercase().contains("${brgId}03".uppercase())) return true
        // Configuration
        return payload.uppercase().contains("C6FC0000ED25")
                && payload.uppercase().contains(brgId.uppercase())
    }

fun DownlinkMessage.eligible(): Boolean {
    return when (val domainMessage = this.toDomainMessageOrNull()) {
        is DownlinkActionMessage -> domainMessage.action.eligible()
        is DownlinkConfigurationMessage -> return Wiliot.configuration.cloudManaged
        is DownlinkCustomBrokerMessage -> return Wiliot.configuration.cloudManaged
        else -> false
    }
}

fun DownlinkAction.eligible(): Boolean {
    return when (this) {
        DownlinkAction.BRIDGE_OTA -> return Wiliot.configuration.cloudManaged
        else -> true
    }
}
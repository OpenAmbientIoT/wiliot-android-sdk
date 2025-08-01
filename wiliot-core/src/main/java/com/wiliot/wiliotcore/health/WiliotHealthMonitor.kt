package com.wiliot.wiliotcore.health

import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.config.VirtualBridgeConfig
import com.wiliot.wiliotcore.model.AdditionalGatewayConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * [WiliotHealthMonitor] designed to keep counters of received BT packets, upstream last delivery
 * timestamp, connection state etc.
 */
object WiliotHealthMonitor {

    private val mState = MutableStateFlow(WiliotHealth())
    val state: StateFlow<WiliotHealth> = mState

    private val monitorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var heartbeatJob: Job? = null

    /**
     * Used to update counter of received BT packets during last minute
     */
    fun updateLastMinuteCounter(c: Int) {
        mState.update { it.copy(btPacketsLastMinute = c) }
    }

    /**
     * Used to update counter of received BT packets during last 10 minutes
     */
    fun updateLast10MinutesCounter(c: Int) {
        mState.update { it.copy(btPacketsLast10minutes = c) }
    }

    /**
     * Notify [WiliotHealthMonitor] with SDK (GW mode) started/stopped state
     */
    fun updateLaunchedState(launched: Boolean) {
        mState.update {
            if (launched) {
                it.copy(
                    monitorLaunched = true,
                    gwStartTime = System.currentTimeMillis(),
                    trafficFilter = Wiliot.configuration.dataOutputTrafficFilter
                )
            } else {
                WiliotHealth()
            }
        }
        heartbeatJob = if (launched) {
            monitorScope.launch {
                beat()
            }
        } else {
            heartbeatJob?.cancel()
            null
        }
    }

    private suspend fun beat() {
        mState.update {
            it.copy(
                monitorLastHeartbeat = System.currentTimeMillis(),
                trafficFilter = Wiliot.configuration.dataOutputTrafficFilter
            )
        }
        delay(500)
        beat()
    }

    /**
     * Used to notify [WiliotHealthMonitor] that MQTT connection established.
     */
    fun notifyMqttConnectionEstablished(succeed: Boolean) {
        mState.update { it.copy(mqttClientConnected = succeed) }
    }

    /**
     * Called every time Downstream receives new Downlink message
     */
    fun notifyDownlinkMessageReceived() {
        val cntCounter = mState.value.downlinkMessagesCounter
        if (cntCounter >= Integer.MAX_VALUE - 10) {
            mState.update { it.copy(downlinkMessagesCounter = 0) }
        } else {
            mState.update { it.copy(downlinkMessagesCounter = cntCounter + 1) }
        }
    }

    /**
     * Called every time on MQTT message delivery completion (by Upstream module)
     */
    fun notifyUplinkMessageDelivered() {
        mState.update { it.copy(lastUplinkDataSentTime = System.currentTimeMillis()) }
    }

    /**
     * Updates packets counter received by virtual Bridge.
     */
    fun updateVirtualBridgePktIn(newPackets: Int) {
        val newValue = if (mState.value.vBridgePktIn < Long.MAX_VALUE - (newPackets + 1)) {
            mState.value.vBridgePktIn + newPackets
        } else {
            0 // reset counter if it exceeds Long.MAX_VALUE
        }
        mState.update { it.copy(vBridgePktIn = newValue) }
    }

    /**
     * Updates packets counter sent by virtual Bridge.
     */
    fun updateVirtualBridgePktOut(newPackets: Int) {
        val newValue = if (mState.value.vBrgPktOut < Long.MAX_VALUE - (newPackets + 1)) {
            mState.value.vBrgPktOut + newPackets
        } else {
            0 // reset counter if it exceeds Long.MAX_VALUE
        }
        mState.update { it.copy(vBrgPktOut = newValue) }
    }

    fun updateVirtualBridgeUniquePxMAC(newCount: Int) {
        mState.update { it.copy(vBridgeUniquePxMAC = newCount) }
    }

}

data class WiliotHealth(
    /**
     * Indicates that [WiliotHealthMonitor] launched and active
     */
    val monitorLaunched: Boolean = false,
    /**
     * Counter of BT packets received during last minute
     */
    val btPacketsLastMinute: Int = 0,
    /**
     * Counter of BT packets received during last 10 minutes
     */
    val btPacketsLast10minutes: Int = 0,
    /**
     * SDK (GW mode) startup timestamp im millis
     */
    val gwStartTime: Long = 0,
    /**
     * Indicates if MQTT client connected to the broker
     */
    val mqttClientConnected: Boolean = false,
    /**
     * Counter for Downlink messages received from Cloud
     */
    val downlinkMessagesCounter: Int = 0,
    /**
     * Timestamp of last MQTT message delivery
     */
    val lastUplinkDataSentTime: Long = 0,
    /**
     * Internal heartbeat timestamp
     */
    val monitorLastHeartbeat: Long = 0,
    /**
     * Traffic filter for additional gateway configuration (gwDataMode)
     */
    val trafficFilter: AdditionalGatewayConfig.DataOutputTrafficFilter? = null,
    /**
     * Address of virtual Bridge
     */
    val bleAddress: String? = Wiliot.virtualBridgeId,
    /**
     * Packets, received by virtual Bridge
     */
    val vBridgePktIn: Long = 0,
    /**
     * Packets, sent by virtual Bridge
     */
    val vBrgPktOut: Long = 0,

    /**
     * Unique MAC addresses count of Pixels processed by virtual Bridge.
     */
    val vBridgeUniquePxMAC: Int = 0
) {

    /**
     * @return GW uptime in format dd:hh:mm:ss
     */
    fun getUptime(): String {
        if (gwStartTime == 0L) return "STOPPED"
        return (System.currentTimeMillis() - gwStartTime).toHumanReadableTime()
    }

    /**
     * @return how many time has passed after last MQTT message delivery (Upstream) in format dd:hh:mm:ss
     */
    fun getLastUplinkSentTime(): String {
        if (lastUplinkDataSentTime == 0L) return "STOPPED"
        return (System.currentTimeMillis() - lastUplinkDataSentTime).toHumanReadableTime() + " ago"
    }

    private fun Long.toHumanReadableTime(): String {
        val seconds = (this / 1000) % 60
        val minutes = (this / (1000 * 60)) % 60
        val hours = (this / (1000 * 60 * 60)) % 24
        val days = this / (1000 * 60 * 60 * 24)
        val daysStr = if (days > 0) "${days}:" else ""
        val hoursStr = if (hours > 0 || days > 0) String.format("%02d:", hours) else ""
        val minutesStr = String.format("%02d:", minutes)
        val secondsStr = String.format("%02d", seconds)
        return "$daysStr$hoursStr$minutesStr$secondsStr"
    }

}
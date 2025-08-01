package com.wiliot.wiliotdownstream.domain.repository

import com.wiliot.wiliotadvertising.BleCommandsAdvertising
import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.health.WiliotHealthMonitor
import com.wiliot.wiliotcore.model.DownlinkAction
import com.wiliot.wiliotcore.model.DownlinkActionMessage
import com.wiliot.wiliotcore.model.DownlinkDomainMessage
import com.wiliot.wiliotcore.model.DownlinkMessage
import com.wiliot.wiliotcore.model.eligible
import com.wiliot.wiliotcore.model.isVmelMessage
import com.wiliot.wiliotcore.utils.Reporter
import com.wiliot.wiliotcore.utils.logTag
import com.wiliot.wiliotdownstream.feature.downstream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Repository of Downstream commands management
 */
object DownstreamRepository {

    private val logTag = logTag()
    internal const val DEFAULT_COMMAND_DELAY = 150L

    private val mCurrentMessage: MutableStateFlow<DownlinkDomainMessage?> = MutableStateFlow(null)
    val currentMessage: StateFlow<DownlinkDomainMessage?>
        get() = mCurrentMessage

    private val backgroundScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Called when MQTT client receives new message from Cloud.
     *
     * @param [msg] raw Downlink message object.
     *
     * Downlink repository could handle only one message for the period of time defined in this
     * message or provided by internal SDK logic. Downlink repository doesn't have any type of queueing
     * for messages. Assuming that, all the messages that will be received in timeframe of previous
     * message processing/execution will be skipped.
     * Example: Downlink receives message to Advertise some BT packet during 500 ms. If during 500 ms
     * new message will be received it will be skipped, because we can only execute one Downlink command
     * a time.
     */
    fun onNewMessage(msg: DownlinkMessage?) {
        Reporter.log("onNewMessage($msg)", logTag)
        if (msg == null) return
        WiliotHealthMonitor.notifyDownlinkMessageReceived()
        if (msg.isVmelMessage) {
            msg.toDomainMessageOrNull()?.let {
                it as? DownlinkActionMessage
            }?.rawTxPacket()?.let {
                Wiliot.downstream().vBridge?.addMelPacket(it)
            }
            return
        }
        if (mCurrentMessage.value != null) {
            Reporter.log("onNewMessage -> Current message has not been processed yet. Ignoring new ones...", logTag)
            return
        }
        if (msg.eligible().not()) {
            Reporter.log("onNewMessage -> New message is not eligible for current SDK configuration. Ignoring...", logTag)
            return
        }
        Reporter.log("onNewMessage -> message accepted and will be processed", logTag)
        with(msg.toDomainMessageOrNull()) {
            mCurrentMessage.value = this
            if (this.timeBasedClearanceSupported()) scheduleClearance(this)
        }
    }

    /**
     * Notify [DownstreamRepository] with durable operation finished (e.g. Bridge OTA Firmware upgrade).
     *
     * @param [msg] exact message that was initializer for the durable job.
     */
    fun notifyMessageProcessed(msg: DownlinkDomainMessage) {
        if (currentMessage.value == msg) clearCurrentMessage()
    }

    /**
     * Release [DownstreamRepository]. It clears current message and stops BLE Advertisement
     */
    fun release() {
        Reporter.log("release", logTag)
        clearCurrentMessage()
        BleCommandsAdvertising.stop()
    }

    private fun scheduleClearance(msg: DownlinkDomainMessage?) {
        backgroundScope.launch {
            delay((msg as? DownlinkActionMessage)?.txMaxDurationMs ?: DEFAULT_COMMAND_DELAY)
            clearCurrentMessage()
            BleCommandsAdvertising.stop()
        }
    }

    private fun clearCurrentMessage() {
        mCurrentMessage.value = null
    }

    private fun DownlinkDomainMessage?.timeBasedClearanceSupported(): Boolean {
        return if (this is DownlinkActionMessage) {
            when (this.action) {
                DownlinkAction.BRIDGE_OTA -> false
                else -> true
            }
        } else true
    }

}

package com.wiliot.wiliotcore.contracts

import android.content.ContextWrapper
import com.wiliot.wiliotcore.legacy.EnvironmentWiliot
import com.wiliot.wiliotcore.model.DownlinkMessage
import kotlinx.coroutines.flow.StateFlow

/**
 * Contract for the Queue management commands delegate. This API is for Queue management purposes
 * from Downstream module.
 */
interface CommandsQueueManagerContract {

    /**
     * This [StateFlow] should keep MQTT connection hash. Each time MQTT established new connection,
     * the value of [mqttConnectionHash] should be replaced with new one. It is recommended
     * to use randomly generated UUIDs.
     * [mqttConnectionHash] is used to trigger necessary logic in case MQTT connection session
     * was changed (e.g. it was reconnected)
     */
    val mqttConnectionHash: StateFlow<String?>

    /**
     * Used to perform subscription on the Downlink 'update' MQTT topic. It allows app to receive
     * commands from the Cloud (e.g. Configuration change or commands to broadcast special packets
     * to the nearby devices)
     */
    fun subscribeOnDownlink(environmentWiliot: EnvironmentWiliot): StateFlow<DownlinkMessage?>

    /**
     * Used to clear current Downlink command received from the Cloud. Should be used after current
     * command already executed to free up command slot. App will not handle further commands from
     * the Cloud until command' slot is not cleared.
     */
    fun clearDownlinkCommand()

    /**
     * Used for periodic sending current Downlink status to the Cloud. The HB messages includes
     * configuration of the GW, so Cloud could always have actual information about GW configuration
     */
    fun sendDownlinkHeartbeat()

    /**
     * Used to make sure that Scope and Actor of Queue is active. In case it is not active, it
     * will be fixed automatically. Should be called at Downstream service startup
     */
    fun ensureUploadQueueActive()

    /**
     * Used to make transport service work as a Foreground service.
     * Should be called at Downstream service startup. Actual implementation covered
     * by implementation of [com.wiliot.wiliotcore.contracts.MessageQueueManagerContract.bindMqttToForeground]
     */
    fun goTransportServiceForeground(contextWrapper: ContextWrapper)
}
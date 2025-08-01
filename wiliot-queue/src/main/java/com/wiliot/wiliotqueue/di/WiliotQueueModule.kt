package com.wiliot.wiliotqueue.di

import android.app.Notification
import android.content.ContextWrapper
import android.os.BatteryManager
import android.os.HardwarePropertiesManager.*
import android.util.Log
import com.google.gson.ExclusionStrategy
import com.google.gson.FieldAttributes
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.WiliotCounter
import com.wiliot.wiliotcore.contracts.CommandsQueueManagerContract
import com.wiliot.wiliotcore.contracts.MessageQueueManagerContract
import com.wiliot.wiliotcore.health.WiliotHealthMonitor
import com.wiliot.wiliotcore.env.EnvironmentWiliot
import com.wiliot.wiliotcore.env.Environments
import com.wiliot.wiliotcore.model.*
import com.wiliot.wiliotcore.utils.Reporter
import com.wiliot.wiliotcore.utils.asJWT
import com.wiliot.wiliotcore.utils.batteryManager
import com.wiliot.wiliotcore.utils.batteryStatusString
import com.wiliot.wiliotcore.utils.every
import com.wiliot.wiliotcore.utils.getUsername
import com.wiliot.wiliotcore.utils.hardwareStatusString
import com.wiliot.wiliotcore.utils.isValidJwt
import com.wiliot.wiliotcore.utils.service.WltServiceNotification
import com.wiliot.wiliotqueue.BuildConfig
import com.wiliot.wiliotqueue.WiliotQueue
import com.wiliot.wiliotqueue.api.WiliotQueueApiService
import com.wiliot.wiliotqueue.di.MessageQueueManager.UploadMsg.Companion.SYNC_PERIOD
import com.wiliot.wiliotqueue.di.MessageQueueManager.UploadMsg.Companion.uploadJob
import com.wiliot.wiliotqueue.mqtt.model.*
import com.wiliot.wiliotqueue.mqtt.payloads.SoftwareGatewayHeartbeatPayload
import com.wiliot.wiliotqueue.mqtt.payloads.PacketsLogPayload
import com.wiliot.wiliotqueue.mqtt.payloads.SoftwareGatewayCapabilitiesPayload
import com.wiliot.wiliotqueue.repository.TokenStorageSource
import com.wiliot.wiliotqueue.repository.tokenStorageSource
import com.wiliot.wiliotqueue.utils.MQTTDataSerializerJson
import com.wiliot.wiliotqueue.utils.NullableUIntJson
import com.wiliot.wiliotqueue.utils.disconnectAndStopService
import com.wiliot.wiliotqueue.utils.ensureForegroundRunning
import info.mqtt.android.service.MqttAndroidClient
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.MqttSecurityException
import java.io.IOException
import java.lang.reflect.Field
import java.util.UUID
import java.util.concurrent.TimeUnit

class MessageQueueManager private constructor(
    private val mqttClientProvider: MqttClientProviderContract = mqttProvider(),
    private val mTokenStorage: TokenStorageSource = tokenStorageSource(),
) : MessageQueueManagerContract, CommandsQueueManagerContract {

    companion object {
        private var INSTANCE: MessageQueueManager? = null

        internal fun getInstance(): MessageQueueManager {
            if (INSTANCE == null) INSTANCE = MessageQueueManager()
            return INSTANCE!!
        }
    }

    private fun buildNewScope() = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val downlinkNetScope = buildNewScope()
    private var downlinkNetJob: Job? = null
    private val uplinkNetScope = buildNewScope()
    private var uploadScope = buildNewScope()
    private var connectionTerminationScope = buildNewScope()
    private var uploadActor: SendChannel<UploadMsg>? = null
    private val mDownlinkCommand: MutableStateFlow<DownlinkMessage?> = MutableStateFlow(null)
    private val downlinkCommand: StateFlow<DownlinkMessage?>
        get() = mDownlinkCommand

    private val mConnectionHash: MutableStateFlow<String?> = MutableStateFlow(null)
    private val connectionHash: StateFlow<String?>
        get() = mConnectionHash

    private val logTag = "FW/MessageQueueManager"
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(UInt::class.java, NullableUIntJson())
        .registerTypeAdapter(MQTTBaseData::class.java, MQTTDataSerializerJson())
        .setExclusionStrategies(
            object : ExclusionStrategy {
                override fun shouldSkipClass(clazz: Class<*>?): Boolean {
                    return false
                }

                override fun shouldSkipField(f: FieldAttributes?): Boolean {
                    val fieldName = f?.name
                    val theClass = f?.declaringClass

                    return isFieldInSuperclass(theClass, fieldName)
                }

                private fun isFieldInSuperclass(subclass: Class<*>?, fieldName: String?): Boolean {
                    var superclass: Class<*>? = subclass?.superclass
                    var field: Field?

                    while (superclass != null) {
                        field = getField(superclass, fieldName)

                        if (field != null)
                            return true

                        superclass = superclass.superclass
                    }

                    return false
                }

                private fun getField(theClass: Class<*>, fieldName: String?): Field? {
                    return try {
                        theClass.getDeclaredField(fieldName!!)
                    } catch (e: Exception) {
                        null
                    }

                }
            }
        )
        .create()

    private val mutex = Mutex()
    private val willPayloadByteArray = gson.toJson(
        PacketsLogPayload(
            gatewayLogs = listOf("WLT_INFO: Connection lost")
        )
    ).encodeToByteArray()

    //==============================================================================================
    // *** Contract ***
    //==============================================================================================

    // region [Contract]

    override fun publishPayload(
        payload: MutableSet<BasePacketData>
    ) {
        enqueuePublishBeaconsDataMQTT(payload)
    }

    override fun publishMel(
        payload: List<MelModulePacket>,
        environmentWiliot: EnvironmentWiliot
    ) {
        enqueuePublishMel(payload)
    }

    override fun publishCapabilities() {
        publishCapabilitiesData()
    }

    override fun publishGatewayHeartbeat() {
        publishGatewayHeartbeatData()
    }

    override fun publishBridgeStatus(
        payload: List<BridgeStatus>,
        environmentWiliot: EnvironmentWiliot,
    ) {
        enqueuePublishBridgeStatusDataMQTT(payload)
    }

    override fun publishBridgeHb(
        payload: List<BridgeHbPacketAbstract>,
        environmentWiliot: EnvironmentWiliot,
    ) {
        enqueuePublishBridgeHeartbeat(payload)
    }

    override suspend fun publishPacketLog(
        payload: List<Any>,
        environmentWiliot: EnvironmentWiliot,
    ) {
        try {
            publishPacketLogDataMQTT(
                payload,
                pickMqttClient(environmentWiliot)
            )
        } catch (e: IOException) {
            Reporter.log("Connectivity. Failed to send packets", logTag)
        }
    }

    override suspend fun publishMelAck(payload: Ack, environmentWiliot: EnvironmentWiliot) {
        try {
            publishMelAckMQTT(
                payload,
                pickMqttClient(environmentWiliot)
            )
        } catch (e: IOException) {
            Reporter.log("Connectivity. Failed to send packets", logTag)
        }
    }

    override suspend fun publishBridgeAckAction(
        payload: List<Any>,
        environmentWiliot: EnvironmentWiliot,
    ) {
        try {
            publishBridgeAckDataMQTT(
                payload,
                pickMqttClient(environmentWiliot)
            )
        } catch (e: IOException) {
            Reporter.log("Connectivity. Failed to send packets", logTag)
        }
    }

    override fun initActorAsync() {
        var fullRecreation = false

        val actorCreator: (CoroutineScope.() -> Unit) = {
            uploadActor = uploadActor().apply {
                invokeOnClose {
                    Reporter.log("Packets actor closed with: $it", logTag)
                }
            }
        }

        initAndStartConnectionTerminatorJob()

        try {
            uploadScope.ensureActive()
        } catch (e: CancellationException) {
            uploadScope = buildNewScope().apply(actorCreator)
            fullRecreation = true
        }
        if (!fullRecreation) {
            if (uploadActor == null) {
                uploadScope.apply(actorCreator)
            }
        }
    }

    override fun releaseConnections(withScope: Boolean) {
        Reporter.log("releaseConnections(MQTT)", logTag)
        if (withScope) {
            mConnectionHash.value = null
            uploadScope.cancel()
        }
        val processedClients: MutableSet<Int> = mutableSetOf()
        Environments.set.forEach { env ->
            mqttClientProvider.getForEnvironment(
                env.envName
            ).let { client ->
                if (client.hashCode() !in processedClients) {
                    processedClients.add(client.hashCode())
                    try {
                        if (client.isConnected) client.disconnectAndStopService()
                    } catch (e: Exception) {
                        Reporter.exception(exception = e, where = logTag)
                    }
                }
            }
        }
    }

    override fun stopConnectionMonitoring() {
        Reporter.log("stopConnectionMonitoring", logTag)
        connectionTerminatorJob?.cancel()
        connectionTerminatorJob = null
    }

    override val mqttConnectionHash: StateFlow<String?>
        get() = connectionHash

    override fun subscribeOnDownlink(): StateFlow<DownlinkMessage?> {
        Reporter.log("subscribeOnDownlink", logTag)
        downlinkNetJob?.runCatching { cancel() }
        downlinkNetJob = downlinkNetScope.launch {
            subscribeDownlinkCommands(
                onFailedToConnect = {
                    Reporter.log("subscribeOnDownlink -> Failed to connect", logTag)
                    subscribeOnDownlink()
                }
            ) {
                mDownlinkCommand.value = it
            }
        }
        return downlinkCommand
    }

    override fun clearDownlinkCommand() {
        mDownlinkCommand.value = null
    }

    override fun sendDownlinkHeartbeat() {
        // https://wiliot.atlassian.net/browse/WMB-1099

        if (Wiliot.configuration.cloudManaged) {
            ensureScopeAndActorActive()
            sendDownlinkHb()
        }
    }

    override fun ensureUploadQueueActive() {
        Reporter.log("ensureUploadQueueActive", logTag)
        ensureScopeAndActorActive()
    }

    override fun goTransportServiceForeground(contextWrapper: ContextWrapper) {
        Reporter.log("goTransportServiceForeground", logTag)
        goMqttForeground(contextWrapper)
    }

    // endregion

    //==============================================================================================
    // *** Domain ***
    //==============================================================================================

    // region [Domain]

    private var lastQueueUpload: Long = System.currentTimeMillis()

    private var connectionTerminatorJob: Job? = null

    private fun initAndStartConnectionTerminatorJob() {
        connectionTerminatorJob?.cancel()
        connectionTerminatorJob = connectionTerminationScope.every(
            TimeUnit.SECONDS.toMillis(60),
            initialDelay = TimeUnit.SECONDS.toMillis(60)
        ) {
            if (Wiliot.configuration.cloudManaged) return@every
            if ((System.currentTimeMillis() - lastQueueUpload) >= TimeUnit.SECONDS.toMillis(59)) {
                Reporter.log("Inactivity in queue detected; dropping connection...", logTag)
                releaseConnections(withScope = false)
            }
        }
    }

    private fun ensureScopeAndActorActive() {
        if (Wiliot.configuration.cloudManaged) {
            if (uploadScope.isActive.not() || uploadActor == null) {
                initActorAsync()
            }
        }
    }

    private fun goMqttForeground(context: ContextWrapper) =
        WltServiceNotification.getWltMQTTServiceNotification(context)?.let { notification ->
            bindMqttToForeground(notification)
        }.also {
            Wiliot.notifyServicesStarting()
        }

    private fun sendDownlinkHb() {
        uploadScope.launch {
            uploadActor?.send(DownlinkHeartbeat)
        }
    }

    private fun sendSendCapabilities() {
        uploadScope.launch {
            uploadActor?.send(SendCapabilities)
        }
    }

    private fun sendSendGatewayHeartbeat() {
        uploadScope.launch {
            uploadActor?.send(DownlinkHeartbeat)
        }
    }

    private fun sendAddToQueue(data: List<MQTTBaseData>, terminator: (() -> Unit)? = null) {
        uploadScope.launch {
            uploadActor?.send(AddUploadData(data, terminator))
        }
    }

    private fun sendInvokeTerminators() {
        uploadScope.launch {
            uploadActor?.send(InvokeTerminators)
        }
    }

    private fun pickMqttClient(
        environmentWiliot: EnvironmentWiliot = Wiliot.configuration.environment,
    ): MqttAndroidClient {
        return mqttClientProvider.getForEnvironment(environmentWiliot.envName)
    }

    private fun enqueuePublishBridgeHeartbeat(
        payload: List<BridgeHbPacketAbstract>,
    ) {
        Reporter.log("enqueuePublishBridgeHeartbeat", logTag)
        sendAddToQueue(payload.map { it.toMqttData() })
    }

    private fun enqueuePublishMel(
        payload: List<MelModulePacket>
    ) {
        Reporter.log("enqueuePublishMel", logTag)
        sendAddToQueue(payload.map { it.toMqttData() })
    }

    private fun enqueuePublishBridgeStatusDataMQTT(
        payload: List<BridgeStatus>,
    ) {
        Reporter.log("enqueuePublishBridgeStatusDataMQTT", logTag)
        payload.filter {
            it.formationType == BridgeStatus.FormationType.FROM_CONFIG_PKT
        }.mapNotNull {
            it.packet as? BridgePacketAbstract
        }.takeIf {
            it.isNotEmpty()
        }?.let { packets ->
            sendAddToQueue(packets.map { it.toMqttData() })
        }
    }

    private fun enqueuePublishBeaconsDataMQTT(
        beacons: MutableSet<BasePacketData>,
    ) {
        Reporter.log("enqueuePublishBeaconsDataMQTT", logTag)

        beacons.mapNotNull {
            when (it) {
                is PacketData -> it.toMqttData().takeIf { packedData ->
                    // Since PIXELS_ONLY means that packets will be processed by VirtualBridge,
                    // we always should apply rule packedData.aliasBridgeId != null || packedData.retransmitted

                    /*when (Wiliot.configuration.dataOutputTrafficFilter) {
                        DataOutputTrafficFilter.BRIDGES_AND_PIXELS -> true
                        DataOutputTrafficFilter.BRIDGES_ONLY -> packedData.aliasBridgeId != null || packedData.retransmitted
                        DataOutputTrafficFilter.PIXELS_ONLY -> packedData.aliasBridgeId == null && packedData.retransmitted.not()
                    }*/

                    packedData != null && (packedData.aliasBridgeId != null || packedData.retransmitted)
                }

                else -> null
            }
        }.takeIf {
            it.isNotEmpty()
        }?.let {
            sendAddToQueue(
                it
            ) {
                try {
                    // Basically, no termination operation needed. May be removed in future
                    Reporter.log("Terminator for beacons executed", logTag)
                } catch (ex: Exception) {
                    Reporter.exception("Terminator for beacons failed", ex, logTag)
                }
            }
        }

    }

    private fun publishCapabilitiesData() {
        Reporter.log("publishCapabilitiesData", logTag)
        sendSendCapabilities()
    }

    private fun publishGatewayHeartbeatData() {
        Reporter.log("publishGatewayHeartbeatData", logTag)
        sendSendGatewayHeartbeat()
    }

    private suspend fun publishPacketLogDataMQTT(
        payload: List<Any>,
        client: MqttAndroidClient,
    ) {
        if (payload.isEmpty()) return
        if (WiliotQueue.configuration.sendLogs.not()) return
        Reporter.log("publishPacketLogDataMQTT -> started", logTag)
        val gwOwnerId = getGwOwnerId()
        gwOwnerId?.let { ownerId ->
            val fullGWId = Wiliot.getFullGWId()
            try {
                client.establishMqttClientConnection(
                    ownerId,
                    callerTag = "publishPacketLogDataMQTT"
                )
            } catch (e: MqttSecurityException) {
                WiliotHealthMonitor.notifyMqttConnectionEstablished(false)
                val oldConfig = WiliotQueue.configuration
                WiliotQueue.configuration = oldConfig.copy(
                    gwToken = null
                )
                publishPacketLogDataMQTT(payload, client)
            } catch (e: Exception) {
                WiliotHealthMonitor.notifyMqttConnectionEstablished(false)
                e.printStackTrace()
            }

            val topic = statusTopic(ownerId, fullGWId)

            try {
                val jsonPayload = gson.toJson(
                    PacketsLogPayload(
                        gatewayLogs = payload
                    )
                ).also {
                    Reporter.log("publishPacketLogDataMQTT: $it", logTag)
                }
                jsonPayload?.let {
                    client.publish(
                        topic,
                        MqttMessage(it.encodeToByteArray())
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun publishMelAckMQTT(
        payload: Ack,
        client: MqttAndroidClient,
    ) {
        Reporter.log("publishMelAckMQTT -> started", logTag)
        val gwOwnerId = getGwOwnerId()
        gwOwnerId?.let { ownerId ->
            val fullGWId = Wiliot.getFullGWId()
            try {
                client.establishMqttClientConnection(ownerId, callerTag = "publishMelAckMQTT")
            } catch (e: MqttSecurityException) {
                WiliotHealthMonitor.notifyMqttConnectionEstablished(false)
                val oldConfig = WiliotQueue.configuration
                WiliotQueue.configuration = oldConfig.copy(
                    gwToken = null
                )
                publishMelAckMQTT(payload, client)
            } catch (e: Exception) {
                WiliotHealthMonitor.notifyMqttConnectionEstablished(false)
                e.printStackTrace()
            }

            val topic = statusTopic(ownerId, fullGWId)

            try {
                val jsonPayload = gson.toJson(payload).also {
                    Reporter.log("publishMelAckMQTT: $it", logTag)
                }
                jsonPayload?.let {
                    client.publish(
                        topic,
                        MqttMessage(it.encodeToByteArray())
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun publishBridgeAckDataMQTT(
        payload: List<Any>,
        client: MqttAndroidClient,
    ) {
        if (payload.isEmpty()) return
        Reporter.log("publishBridgeAckDataMQTT -> started", logTag)
        val gwOwnerId = getGwOwnerId()
        gwOwnerId?.let { ownerId ->
            val fullGWId = Wiliot.getFullGWId()
            try {
                client.establishMqttClientConnection(
                    ownerId,
                    callerTag = "publishBridgeAckDataMQTT"
                )
            } catch (e: MqttSecurityException) {
                WiliotHealthMonitor.notifyMqttConnectionEstablished(false)
                val oldConfig = WiliotQueue.configuration
                WiliotQueue.configuration = oldConfig.copy(
                    gwToken = null
                )
                publishBridgeAckDataMQTT(payload, client)
            } catch (e: Exception) {
                WiliotHealthMonitor.notifyMqttConnectionEstablished(false)
                e.printStackTrace()
            }

            val topic = statusTopic(ownerId, fullGWId)

            try {
                val jsonPayload = gson.toJson(
                    PacketsLogPayload(
                        gatewayLogs = payload
                    )
                ).also {
                    Reporter.log("publishPacketLogDataMQTT: $it", logTag)
                }
                jsonPayload?.let {
                    client.publish(
                        topic,
                        MqttMessage(it.encodeToByteArray())
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun subscribeDownlinkCommands(
        onFailedToConnect: () -> Unit,
        callback: (DownlinkMessage) -> Unit,
    ) {
        Reporter.log("subscribeDownlinkCommands", logTag)

        val client = pickMqttClient(Wiliot.configuration.environment)

        var connectionEstablished = true // default value; will be false in case of exception

        val gwOwnerId = getGwOwnerId()
        gwOwnerId?.let { ownerId ->
            val fullGWId = Wiliot.getFullGWId()
            try {
                client.establishMqttClientConnection(
                    ownerId,
                    callerTag = "subscribeDownlinkCommands"
                )
            } catch (e: MqttSecurityException) {
                WiliotHealthMonitor.notifyMqttConnectionEstablished(false)
                Reporter.exception(
                    "subscribeDownlinkCommands -> Error connecting client",
                    e,
                    logTag
                )
                val oldConfig = WiliotQueue.configuration
                WiliotQueue.configuration = oldConfig.copy(
                    gwToken = null
                )
                connectionEstablished = false
                onFailedToConnect.invoke()
            } catch (e: Exception) {
                WiliotHealthMonitor.notifyMqttConnectionEstablished(false)
                connectionEstablished = false
                if (e is MqttException && e.reasonCode.compareTo(MqttException.REASON_CODE_CLIENT_TIMEOUT) == 0) {
                    Reporter.exception(
                        "subscribeDownlinkCommands -> Attempt to connect failed, will retry",
                        e,
                        logTag
                    )
                    onFailedToConnect.invoke()
                } else {
                    Reporter.exception(
                        "subscribeDownlinkCommands -> Unknown error",
                        e,
                        logTag
                    )
                    e.printStackTrace()
                }
            }

            if (connectionEstablished.not()) return

            val topic = updateTopic(ownerId, fullGWId)
            try {
                client.subscribe(
                    topic,
                    0
                ) { _, message ->
                    Reporter.log("DS TOPIC RAW MESSAGE: $message", logTag)
                    try {
                        val msg = String(message.payload)
                        callback.invoke(DownlinkMessage(msg))
                    } catch (ex: Exception) {
                        Reporter.log("Corrupted message", logTag)
                    }
                }
                Reporter.log("subscribeDownlinkCommands -> subscribed on topic", logTag)
            } catch (e: Exception) {
                Reporter.exception(
                    "subscribeDownlinkCommands -> Unable to subscribe Downlink on topic 'update'",
                    e,
                    logTag
                )
                e.printStackTrace()
            }
        } ?: kotlin.run {
            Reporter.exception(
                "subscribeDownlinkCommands -> Unable to subscribe Downlink; ownerId = null",
                null,
                logTag
            )
        }
    }

    private fun createGatewayMQTT(
        location: Location?,
        dataItems: Collection<MQTTBaseData>,
    ): GatewayMQTT = with(dataItems) {
        GatewayMQTT(
            this.toList(),
            location
        )
    }

    private fun getOwnerId(): String? {
        val ownerId = Wiliot.configuration.ownerId
        ownerId.orEmpty().takeIf { it.isBlank() }?.apply {
            throw IllegalStateException(
                "ownerId is not specified in configuration"
            )
        }
        return ownerId
    }

    private suspend fun getGwOwnerId(): String? {
        if (Wiliot.dynamicBrokerConfig.isDynamicCustomBroker) {
            return Wiliot.dynamicBrokerConfig.ownerId
        }
        return takeGwAccessToken("getGwOwnerId", checkExpiration = false)
            .takeUnless { it.isBlank() }?.asJWT()?.getUsername()
    }

    private suspend fun askGwTokenRefresh(
        gwRefreshToken: String
    ): String {
        val service = pickApiService()
        var result = ""
        service.refreshGWTokenAsync(gwRefreshToken).await().let { response ->
            response
                .takeIf { it.isSuccessful }?.apply {
                    val body = response.body()
                    result = body?.accessToken.orEmpty()
                    val oldConfig = WiliotQueue.configuration
                    WiliotQueue.configuration = oldConfig.copy(
                        gwToken = result,
                        gwRefreshToken = body?.refreshToken.orEmpty()
                    )
                    mTokenStorage.saveGwRefreshToken(
                        token = WiliotQueue.configuration.gwRefreshToken,
                        environmentWiliot = Wiliot.configuration.environment,
                        ownerId = getOwnerId() ?: throw IllegalStateException(
                            "ownerId is not specified in configuration"
                        )
                    )
                }
            response
                .takeIf { !it.isSuccessful }?.apply {
                    askGwRegistration()
                }
        }
        return result
    }

    private suspend fun askGwRegistration(): String {
        Reporter.log("askGwRegistration", logTag)
        var result = ""
        getOwnerId()?.let { ownerId ->
            Reporter.log("askGwRegistration -> ownerId received ($ownerId)", logTag)
            val service = pickApiService()
            service.registerGWAsync(ownerId).await().also { response ->
                response.takeIf { it.isSuccessful }?.apply {
                    Reporter.log("askGwRegistration -> successful response", logTag)
                    val body = response.body()?.data
                    result = body?.accessToken.orEmpty()
                    val oldConfig = WiliotQueue.configuration
                    WiliotQueue.configuration = oldConfig.copy(
                        gwToken = result,
                        gwRefreshToken = body?.refreshToken.orEmpty()
                    )
                    mTokenStorage.saveGwRefreshToken(
                        token = WiliotQueue.configuration.gwRefreshToken,
                        environmentWiliot = Wiliot.configuration.environment,
                        ownerId = ownerId
                    )
                }
            }
        }
        return result
    }

    private suspend fun exchangeGwRefreshToAccess(): String =
        mutex.withLock {
            val refreshToken = WiliotQueue.configuration.gwRefreshToken
                ?: Wiliot.configuration.ownerId?.let {
                    mTokenStorage.getGwRefreshToken(Wiliot.configuration.environment, it)
                }

            refreshToken.let { gwRefreshToken ->

                // Check if access token still invalid or not
                val storedToken = WiliotQueue.configuration.gwToken
                if (storedToken.isValidJwt("$logTag[gw_access_token/check_still_invalid]")) {
                    Reporter.log(
                        "exchangeGwRefreshToAccess -> GW token already valid (still not expired)",
                        logTag
                    )
                    return@let storedToken!!
                }

                if (gwRefreshToken.isNullOrBlank()) {
                    Reporter.log("exchangeGwRefreshToAccess -> askGWRegistration", logTag)
                    askGwRegistration().also {
                        Reporter.log(
                            "exchangeGwRefreshToAccess -> askGWRegistration -> registered",
                            logTag
                        )
                    }
                } else {
                    Reporter.log("exchangeGwRefreshToAccess -> askGWTokenRefresh", logTag)
                    askGwTokenRefresh(gwRefreshToken = gwRefreshToken).also {
                        Reporter.log(
                            "exchangeGwRefreshToAccess -> askGWTokenRefresh -> ${it.isNotBlank()}",
                            logTag
                        )
                    }
                }
            }
        }

    private suspend fun takeGwAccessToken(
        callerTag: String,
        checkExpiration: Boolean = true
    ): String =
        WiliotQueue.configuration.gwToken.let { gwAccessToken: String? ->
            if (gwAccessToken.isValidJwt(
                    "$logTag[gw_access_token/take_check]",
                    checkExpiration = checkExpiration
                ).not()
            ) {
                Reporter.log(
                    "takeGwAccessToken(caller: $callerTag, checkExpiration: $checkExpiration) -> GW token expired",
                    logTag
                )
                exchangeGwRefreshToAccess().apply {
                    releaseConnections(false)
                }
            } else {
                Reporter.log(
                    "takeGwAccessToken(caller: $callerTag, checkExpiration: $checkExpiration) -> GW token is valid",
                    logTag
                )
                gwAccessToken!!
            }
        }

    private fun statusTopic(ownerId: String, fullGWId: String): String {
        return if (Wiliot.dynamicBrokerConfig.isDynamicCustomBroker)
            Wiliot.dynamicBrokerConfig.customConfig!!.statusTopic
        else
            "status/$ownerId/$fullGWId"
    }

    private fun dataTopic(ownerId: String, fullGWId: String): String {
        return if (Wiliot.dynamicBrokerConfig.isDynamicCustomBroker)
            Wiliot.dynamicBrokerConfig.customConfig!!.dataTopic
        else
            "data/$ownerId/$fullGWId"
    }

    private fun updateTopic(ownerId: String, fullGWId: String): String {
        return if (Wiliot.dynamicBrokerConfig.isDynamicCustomBroker)
            Wiliot.dynamicBrokerConfig.customConfig!!.updateTopic
        else
            "update/$ownerId/$fullGWId"
    }

    private fun pickApiService(): WiliotQueueApiService {
        return apiService()
    }

    override fun bindMqttToForeground(notification: Notification) {
        pickMqttClient(Wiliot.configuration.environment).apply {
            setForegroundService(notification)
            ensureForegroundRunning()
        }
    }

    private suspend fun sendCapabilitiesMessage() {
        Reporter.log("sendCapabilitiesMessage", logTag)
        val gwOwnerId = getGwOwnerId()
        val fullGWId = Wiliot.getFullGWId()
        val client = pickMqttClient(Wiliot.configuration.environment)
        gwOwnerId?.let { ownerId ->
            try {
                if (BuildConfig.DEBUG) {
                    Reporter.log("sendCapabilitiesMessage -> connecting...", logTag)
                }
                client.establishMqttClientConnection(ownerId, callerTag = "sendCapabilitiesMessage")
                Reporter.log("sendCapabilitiesMessage -> client connected", logTag)
            } catch (e: MqttSecurityException) {
                if (BuildConfig.DEBUG) {
                    Log.e(logTag, "Error sending capabilities", e)
                }
                WiliotHealthMonitor.notifyMqttConnectionEstablished(false)
                val oldConfig = WiliotQueue.configuration
                WiliotQueue.configuration = oldConfig.copy(gwToken = null)
                sendCapabilitiesMessage()
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e(logTag, "Error sending capabilities", e)
                }
                WiliotHealthMonitor.notifyMqttConnectionEstablished(false)
                e.printStackTrace()
            }

            if (client.isConnected) {

                val topic = statusTopic(ownerId, fullGWId)

                try {
                    val jsonPayload =
                        Gson().toJson(SoftwareGatewayCapabilitiesPayload.create(cloudManaged = Wiliot.configuration.cloudManaged))
                    client.publish(
                        topic,
                        MqttMessage(jsonPayload.encodeToByteArray())
                    )
                    Reporter.log("sendCapabilitiesMessage -> sent ($jsonPayload)", logTag)
                } catch (e: Exception) {
                    Reporter.log(
                        "sendCapabilitiesMessage -> exception occurred",
                        logTag
                    )
                    e.printStackTrace()
                }
            } else {
                Reporter.log("sendCapabilitiesMessage -> client not connected!", logTag)
            }
        }
    }

    private suspend fun sendDownlinkHeartbeatMessage() {
        Reporter.log("sendDownlinkHeartbeatMessage", logTag)
        val gwOwnerId = getGwOwnerId()
        val fullGWId = Wiliot.getFullGWId()
        val client = pickMqttClient(Wiliot.configuration.environment)
        gwOwnerId?.let { ownerId ->
            try {
                if (BuildConfig.DEBUG) {
                    Reporter.log("sendDownlinkHeartbeatMessage -> connecting...", logTag)
                }
                client.establishMqttClientConnection(
                    ownerId,
                    callerTag = "sendDownlinkHeartbeatMessage"
                )
                Reporter.log("sendDownlinkHeartbeatMessage -> client connected", logTag)
            } catch (e: MqttSecurityException) {
                if (BuildConfig.DEBUG) {
                    Log.e(logTag, "Error sending downlink heartbeat", e)
                }
                WiliotHealthMonitor.notifyMqttConnectionEstablished(false)
                val oldConfig = WiliotQueue.configuration
                WiliotQueue.configuration = oldConfig.copy(gwToken = null)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e(logTag, "Error sending downlink heartbeat", e)
                }
                WiliotHealthMonitor.notifyMqttConnectionEstablished(false)
                e.printStackTrace()
            }

            if (client.isConnected) {

                val topic = statusTopic(ownerId, fullGWId)

                var bStatus = batteryStatusString(BatteryManager.BATTERY_STATUS_UNKNOWN)
                var hwStatus: String? = null
                var bCurrentMicroAmp = 0L
                var bCurrentAvgMicroAmp = 0
                var bCapacity = -1

                Wiliot.withApplicationContext {
                    hwStatus =
                        hardwareStatusString() + " BATTERY: ${Wiliot.lastObtainedBatteryTemp}"
                    bStatus =
                        batteryStatusString(batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS))
                    bCurrentMicroAmp =
                        batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                    bCapacity =
                        batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    bCurrentAvgMicroAmp =
                        batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
                }

                try {
                    val jsonPayload =
                        Gson().toJsonTree(
                            SoftwareGatewayHeartbeatPayload.create(
                                hwStatus,
                                bStatus,
                                bCurrentMicroAmp,
                                bCapacity,
                                bCurrentAvgMicroAmp
                            )
                        ).asJsonObject
                    if (Wiliot.extraGatewayInfoSynchronized.isNotEmpty()) {
                        val gwInfo = jsonPayload["gatewayInfo"].asJsonObject
                        Wiliot.extraGatewayInfoSynchronized.toList().forEach {
                            gwInfo.addProperty(it.first, it.second)
                        }
                    }
                    client.publish(
                        topic,
                        MqttMessage(jsonPayload.toString().encodeToByteArray())
                    )
                    Reporter.log("sendDownlinkHeartbeatMessage -> sent", logTag)
                } catch (e: Exception) {
                    Reporter.log(
                        "sendDownlinkHeartbeatMessage -> exception occurred",
                        logTag
                    )
                    e.printStackTrace()
                }
            } else {
                Reporter.log("sendDownlinkHeartbeatMessage -> client not connected!", logTag)
            }
        }
    }

    private suspend fun uploadQueue(data: List<MQTTBaseData>) {
        Reporter.log("uploadQueue (${data.size})", logTag)

        if (data.isEmpty() && Wiliot.configuration.cloudManaged.not()) {
            Reporter.log("uploadQueue -> early skip (empty data)", logTag)
            return
        }

        val gwOwnerId = getGwOwnerId()
        val client = pickMqttClient(Wiliot.configuration.environment)
        gwOwnerId?.let { ownerId ->
            val fullGWId = Wiliot.getFullGWId()
            try {
                client.establishMqttClientConnection(ownerId, callerTag = "uploadQueue")
            } catch (e: MqttSecurityException) {
                WiliotHealthMonitor.notifyMqttConnectionEstablished(false)
                val oldConfig = WiliotQueue.configuration
                WiliotQueue.configuration = oldConfig.copy(gwToken = null)
                uploadQueue(data)
            } catch (e: Exception) {
                WiliotHealthMonitor.notifyMqttConnectionEstablished(false)
                e.printStackTrace()
            }

            val location = Wiliot.locationManager.getLastLocation()
            val gwLoc =
                if (null == location) Location(0.0, 0.0) else Location(
                    location.latitude,
                    location.longitude
                )

            val topic = dataTopic(ownerId, fullGWId)

            if (data.isNotEmpty()) {
                lastQueueUpload = System.currentTimeMillis()
                val gateway = createGatewayMQTT(gwLoc, data)
                try {
                    client.publish(
                        topic,
                        MqttMessage(gson.toJson(gateway).encodeToByteArray())
                    )
                    if (BuildConfig.DEBUG) {
                        Reporter.log("uploadQueue -> publish", logTag)
                    }
                    sendInvokeTerminators()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                if (BuildConfig.DEBUG) {
                    Reporter.log("uploadQueue -> skip (empty data)", logTag)
                }
            }
        } ?: Reporter.exception("uploadQueue -> GW owner id is empty", null, logTag)
    }

    private var connectionMutex = Mutex()

    @Throws(MqttSecurityException::class)
    private suspend fun MqttAndroidClient.establishMqttClientConnection(
        ownerId: String,
        callerTag: String? = null
    ) {
        connectionMutex.withLock {
            val fullGWId = Wiliot.getFullGWId()
            var gwAccessToken: String?
            this.takeIf { !it.isConnected }?.apply {
                gwAccessToken = if (Wiliot.dynamicBrokerConfig.isDynamicCustomBroker.not())
                    takeGwAccessToken("establishMqttClientConnection")
                else
                    Wiliot.dynamicBrokerConfig.customConfig!!.password
                if (BuildConfig.DEBUG) {
                    Reporter.log(
                        "establishMqttClientConnection(tag: $callerTag) -> client connect...",
                        logTag
                    )
                }
                connect(
                    options = generateMqttConnectOptions(ownerId, gwAccessToken, fullGWId)
                ).waitForCompletion()
                WiliotHealthMonitor.notifyMqttConnectionEstablished(true)
                if (BuildConfig.DEBUG) {
                    Reporter.log(
                        "establishMqttClientConnection(tag: $callerTag) -> client connected",
                        logTag
                    )
                }
                mConnectionHash.value = UUID.randomUUID().toString()
            } ?: kotlin.run {
                WiliotHealthMonitor.notifyMqttConnectionEstablished(true)
            }
        }
    }

    private fun generateMqttConnectOptions(
        ownerId: String,
        gwAccessToken: String?,
        fullGWId: String,
    ) =
        MqttConnectOptions().apply {
            userName = if (Wiliot.dynamicBrokerConfig.isDynamicCustomBroker) Wiliot.dynamicBrokerConfig.customConfig!!.username else ownerId
            password = gwAccessToken?.toCharArray()
            isAutomaticReconnect = false
            keepAliveInterval = 60
            setWill(
                if (Wiliot.dynamicBrokerConfig.isDynamicCustomBroker)
                    Wiliot.dynamicBrokerConfig.customConfig!!.statusTopic
                else
                    "status/$ownerId/$fullGWId",
                willPayloadByteArray,
                0,
                false
            )
        }


    // region [[ - Upload Actor - ]]

    private sealed class UploadMsg {
        companion object {
            const val SYNC_PERIOD = 1000L
            var uploadJob: Job? = null
        }
    }

    private data object SendCapabilities : UploadMsg()

    private data object DownlinkHeartbeat : UploadMsg()

    private data object PushUploadQueue : UploadMsg()

    private data object InvokeTerminators : UploadMsg()

    private data class AddUploadData(
        val data: List<MQTTBaseData>,
        val terminator: (() -> Unit)? = null,
    ) : UploadMsg()

    @OptIn(ObsoleteCoroutinesApi::class)
    private fun CoroutineScope.uploadActor() = actor<UploadMsg> {
        Reporter.log("Upload actor created", logTag)
        val uploadQueue: MutableList<MQTTBaseData> = mutableListOf()
        val terminators: MutableList<() -> Unit> = mutableListOf()

        uploadJob = uploadScope.launch {
            Reporter.log("uploadJob launched", logTag)
            do {
                kotlin.runCatching {
                    uploadJob?.ensureActive()
                    delay(SYNC_PERIOD)
                    this@actor.channel.send(PushUploadQueue)
                }
            } while (uploadJob?.isActive == true)
            Reporter.log("uploadJob not active anymore", logTag)
        }.apply {
            invokeOnCompletion {
                Reporter.log("uploadJob completed", logTag)
            }
        }

        for (msg in channel) {

            if (BuildConfig.DEBUG) {
                Reporter.log("QueueUpload message <${msg::class.java.simpleName}>", logTag)
            }

            when (msg) {
                is SendCapabilities -> {
                    uplinkNetScope.launch {
                        sendCapabilitiesMessage()
                    }
                }

                is DownlinkHeartbeat -> {
                    uplinkNetScope.launch {
                        sendDownlinkHeartbeatMessage()
                    }
                }

                is InvokeTerminators -> {
                    terminators.forEach { it.invoke() }
                    terminators.clear()
                }

                is AddUploadData -> {
                    uploadQueue.addAll(msg.data.sortedBy { it.sequenceId })
                    msg.terminator?.let { terminators.add(it) }
                }

                is PushUploadQueue -> {
                    uploadQueue.toList().also {
                        uplinkNetScope.launch {
                            uploadQueue(it.sortedBy { it.sequenceId })
                        }
                    }
                    uploadQueue.clear()
                }
            }
        }

    }

    // endregion

    // endregion

    init {
        WiliotCounter.reset()
    }

}

fun messageQueueManager(): MessageQueueManagerContract = MessageQueueManager.getInstance()

fun commandsQueueManager(): CommandsQueueManagerContract = MessageQueueManager.getInstance()


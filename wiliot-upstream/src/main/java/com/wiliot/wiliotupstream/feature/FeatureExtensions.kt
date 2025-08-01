@file:OptIn(ExperimentalCoroutinesApi::class)

package com.wiliot.wiliotupstream.feature

import com.wiliot.wiliotcalibration.BleAdvertising
import com.wiliot.wiliotcore.ServiceState
import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.contracts.MessageQueueManagerContract
import com.wiliot.wiliotcore.contracts.UpstreamExtraDataProcessingContract
import com.wiliot.wiliotcore.contracts.UpstreamExtraEdgeProcessingContract
import com.wiliot.wiliotcore.contracts.UpstreamFeedbackChannel
import com.wiliot.wiliotcore.contracts.UpstreamVirtualBridgeChannel
import com.wiliot.wiliotcore.contracts.VirtualBridgeContract
import com.wiliot.wiliotcore.contracts.WiliotUpstreamModule
import com.wiliot.wiliotcore.contracts.wiring.MessageQueueManagerProvider
import com.wiliot.wiliotcore.model.Ack
import com.wiliot.wiliotcore.model.Packet
import com.wiliot.wiliotcore.registerModule
import com.wiliot.wiliotcore.utils.Reporter
import com.wiliot.wiliotcore.utils.logTag
import com.wiliot.wiliotcore.utils.weak
import com.wiliot.wiliotupstream.domain.repository.BeaconDataRepository
import com.wiliot.wiliotupstream.domain.scanners.BLEScanner
import com.wiliot.wiliotupstream.utils.MemoryClearanceUtil
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.lang.ref.WeakReference

/**
 * Main control unit of Upstream module
 */
class Upstream private constructor() : WiliotUpstreamModule {

    private val logTag = logTag()

    internal var queueManagerProvider: WeakReference<MessageQueueManagerProvider>? = null

    companion object {

        private var uInstance: Upstream? = null

        /**
         * Singleton implementation of [Upstream] control unit
         */
        internal fun getInstance(): Upstream {
            if (uInstance == null) {
                uInstance = Upstream()
                Wiliot.registerModule(uInstance!!)
            }
            return uInstance!!
        }
    }

    private val mState = MutableStateFlow(UpstreamState.STOPPED)

    /**
     * Contains live flow representing current [Upstream] state
     */
    override val upstreamState: StateFlow<UpstreamState> = mState

    internal var scanner: BLEScanner? = null

    internal var extraEdgeProcessor: UpstreamExtraEdgeProcessingContract? = null
        set(value) {
            field = value
            if (mState.value != UpstreamState.STOPPED) value?.start()
        }

    internal var extraDataProcessor: UpstreamExtraDataProcessingContract? = null
        set(value) {
            field = value
            if (mState.value != UpstreamState.STOPPED) value?.start()
        }

    internal var vBridge: VirtualBridgeContract? = null

    @OptIn(ObsoleteCoroutinesApi::class)
    override val feedbackChannel: UpstreamFeedbackChannel = object : UpstreamFeedbackChannel {
        override fun sendAck(ackPayload: Ack) {
            BeaconDataRepository.sendMelAckPayload(ackPayload)
        }
    }

    @OptIn(ObsoleteCoroutinesApi::class)
    override val upstreamVirtualBridgeChannel: UpstreamVirtualBridgeChannel = object : UpstreamVirtualBridgeChannel {
        override fun sendPackets(packets: List<Packet>) {
            BeaconDataRepository.sendPacketsFromVirtualBridge(packets)
        }
    }

    @OptIn(ObsoleteCoroutinesApi::class)
    fun getAllBeacons() {
        BeaconDataRepository.getAllBeacons(true)
    }

    /**
     * Start [com.wiliot.wiliotupstream.domain.service.ForegroundService]. It allows to listen BLE
     * packets, process them and send to the Cloud
     */
    @OptIn(ObsoleteCoroutinesApi::class)
    override fun start() {
        Reporter.log("start; config = ${Wiliot.configuration}", logTag)
        if (Wiliot.configuration.isValid().not()) throw IllegalStateException(
            "Can not start Upstream. Configuration is invalid: ${Wiliot.configuration}"
        )
        BeaconDataRepository.initActorAsync()
        Wiliot.withApplicationContext {
            Wiliot.locationManager.startObserveLocation(this)
            try {
                Wiliot.accelerometer.start(this)
            } catch (ex: Exception) {
                // Nothing
            }
            scanner = BLEScanner()
            scanner?.start(this)
        }
        extraEdgeProcessor?.start()
        extraDataProcessor?.start()
        mState.value = UpstreamState.RUNNING_BACKGROUND
    }

    @OptIn(ObsoleteCoroutinesApi::class)
    @ExperimentalCoroutinesApi
    @Deprecated("Try to avoid using this method")
    fun restartScanner(softRestart: Boolean = false) {
        Reporter.log("restartScanner(softRestart: $softRestart)", logTag)
        if (softRestart) {
            withExtraDataProcessor { flagBeacons(shouldUpdate = true) }
        } else {
            BeaconDataRepository.clearList()
        }
        scanner?.restartScanner()
    }

    /**
     * Stop [com.wiliot.wiliotupstream.domain.service.ForegroundService].
     */
    @OptIn(ObsoleteCoroutinesApi::class)
    override fun stop() {
        Reporter.log("stop", logTag)
        scanner?.stopAll()
        try {
            Wiliot.accelerometer.stop()
        } catch (ex: Exception) {
            // Nothing
        }
        BleAdvertising.stop()
        Wiliot.withApplicationContext {
            Wiliot.locationManager.stopLocationUpdates(this)
        }
        extraEdgeProcessor?.stop()
        extraDataProcessor?.stop()
        BeaconDataRepository.clearList()
        BeaconDataRepository.suspendActor()
        mState.value = UpstreamState.STOPPED
    }

    @OptIn(ObsoleteCoroutinesApi::class)
    internal fun releaseMemory(trimLevel: Int) {
        Reporter.log("releaseMemory($trimLevel)", logTag)
        val newExpirationLimit = MemoryClearanceUtil.getOptimisedExpirationLimit(
            MemoryClearanceUtil.Call.TrimCallback(trimLevel)
        ) {
            BeaconDataRepository.clearList()
        }
        Reporter.log("releaseMemory -> newExpirationLimit: $newExpirationLimit", logTag)
        Wiliot.configuration = Wiliot.configuration.copy(
            expirationLimit = newExpirationLimit
        )
    }

    override fun setupQueueProvider(provider: MessageQueueManagerProvider) {
        queueManagerProvider = provider.weak()
    }

    /**
     * Set implementation of [UpstreamExtraEdgeProcessingContract]
     */
    override fun setExtraEdgeProcessor(processor: UpstreamExtraEdgeProcessingContract?) {
        this.extraEdgeProcessor = processor
    }

    /**
     * Set implementation of [UpstreamExtraDataProcessingContract]
     */
    override fun setExtraDataProcessor(processor: UpstreamExtraDataProcessingContract?) {
        this.extraDataProcessor = processor
    }

    override fun setVirtualBridge(vBridge: VirtualBridgeContract) {
        this.vBridge = vBridge
    }

}

/**
 * Returns instance of [Upstream] singleton
 */
@Suppress("unused", "UnusedReceiverParameter")
fun Wiliot.upstream(): Upstream = Upstream.getInstance()

internal fun withExtraEdgeProcessor(task: UpstreamExtraEdgeProcessingContract.() -> Unit) {
    Wiliot.upstream().extraEdgeProcessor.takeUnless { it == null }?.let { task.invoke(it) }
}

internal fun withExtraDataProcessor(task: UpstreamExtraDataProcessingContract.() -> Unit) {
    Wiliot.upstream().extraDataProcessor.takeUnless { it == null }?.let { task.invoke(it) }
}

internal val Upstream.queueManager: MessageQueueManagerContract
    get() {
        if (queueManagerProvider == null) throw RuntimeException(
            "QueueManagerProvider is not initialized. Use Upstream.queueManagerBy() extension to initialize it"
        )
        return try {
            queueManagerProvider!!.get()!!.provideMessageQueueManager()
        } catch (ex: Exception) {
            throw RuntimeException(
                "QueueManagerProvider failed to provide MessageQueueManagerContract impl",
                ex
            )
        }
    }

@Suppress("UnusedReceiverParameter")
fun Wiliot.WiliotInitializationScope.initUpstream() {
    if (Wiliot.isInitialized.not()) throw RuntimeException(
        "Unable to execute `initUpstream`. First you should initialize Wiliot with ApplicationContext."
    )
    Upstream.getInstance()
}

typealias UpstreamState = ServiceState
package com.wiliot.wiliotresolveedge

import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.contracts.EdgeNetworkManagerContract
import com.wiliot.wiliotcore.contracts.UpstreamExtraEdgeProcessingContract
import com.wiliot.wiliotcore.contracts.WiliotResolveEdgeModule
import com.wiliot.wiliotcore.contracts.wiring.EdgeNetworkManagerProvider
import com.wiliot.wiliotcore.model.*
import com.wiliot.wiliotcore.registerModule
import com.wiliot.wiliotcore.utils.Reporter
import com.wiliot.wiliotcore.utils.logTag
import com.wiliot.wiliotcore.utils.weak
import com.wiliot.wiliotresolveedge.domain.repository.EdgeResolveDataRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import java.lang.ref.WeakReference

object WiliotEdgeResolver : WiliotResolveEdgeModule {

    private val logTag = logTag()

    const val DEFAULT_ASSET_BLOCKER_DELAY = 5000L

    private var initialized: Boolean = false

    @Suppress("MemberVisibilityCanBePrivate")
    internal var networkManagerProvider: WeakReference<EdgeNetworkManagerProvider>? = null

    private var mProcessor: UpstreamExtraEdgeProcessingContract? = null
    override val processor get() = mProcessor

    @OptIn(ObsoleteCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    internal fun initialize() {
        Reporter.log("initialize", logTag)

        if (initialized.not()) {
            mProcessor = object : UpstreamExtraEdgeProcessingContract {

                private val cLogTag = "$logTag/Processor"

                override fun start() {
                    Reporter.log("start", cLogTag)
                    EdgeResolveDataRepository.initActorAsync()
                }

                override fun stop() {
                    Reporter.log("stop", cLogTag)
                    EdgeResolveDataRepository.clearList()
                    EdgeResolveDataRepository.suspendActor()
                }

                override fun notifyBridgesPresence(bridgesIds: List<String>, rssiMap: Map<String, Int>) {
                    EdgeResolveDataRepository.updateBridgePresence(bridgesIds, rssiMap)
                }

                override fun bridgesAdded(bridges: List<BridgeStatus>) {
                    EdgeResolveDataRepository.sendBridgesAdded(bridges)
                }

                override fun checkUnresolvedBridges() {
                    EdgeResolveDataRepository.sendCheckUnresolvedBridges()
                }

                override fun askForBridgesIfNeeded(payload: List<BridgeStatus>) {
                    EdgeResolveDataRepository.sendAskForBridgesInfoIfNeeded(payload)
                }

            }

            Wiliot.registerModule(this)

            initialized = true
        }
    }

    //==============================================================================================
    // *** Domain ***
    //==============================================================================================

    // region [Domain]

    internal suspend fun askForBridge(bridgeId: String) = networkManager!!.askForBridge(bridgeId)

    // endregion

    //==============================================================================================
    // *** API ***
    //==============================================================================================

    // region [API]

    @OptIn(ObsoleteCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    fun bridgesFlow() = EdgeResolveDataRepository.bridgesState

    @OptIn(ObsoleteCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    fun flagBridge(bridgeId: String) {
        EdgeResolveDataRepository.flagBridge(bridgeId)
    }

    @OptIn(ObsoleteCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    fun notifyBridgeFirmwareVersionChanged(bridgeId: String, newVersion: String) {
        EdgeResolveDataRepository.notifyBridgeFirmwareVersionChanged(bridgeId, newVersion)
    }

    /**
     * Use this outside the SDK core if you need to get details by Bridge ID
     */
    @OptIn(ObsoleteCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    suspend fun loadBridgeById(id: String, triggerRepo: Boolean = false): BridgeWrapper {
        if (triggerRepo) {
            EdgeResolveDataRepository.sendAskForBridgeInfo(BridgeStatus(id = id, formationType = BridgeStatus.FormationType.SYNTHETIC))
        }
        return askForBridge(id)
    }

    // endregion

    override fun setupNetworkManagerProvider(provider: EdgeNetworkManagerProvider) {
        networkManagerProvider = provider.weak()
    }

}

internal val WiliotEdgeResolver.networkManager: EdgeNetworkManagerContract
    get() {
        if (networkManagerProvider == null) throw RuntimeException(
            "EdgeNetworkManagerProvider is not initialized. Use WiliotEdgeResolver.networkManagerBy() extension to initialize it"
        )
        return try {
            networkManagerProvider!!.get()!!.provideEdgeNetworkManager()
        } catch (ex: Exception) {
            throw RuntimeException(
                "EdgeNetworkManagerProvider failed to provide EdgeNetworkManagerContract impl",
                ex
            )
        }
    }

@Suppress("UnusedReceiverParameter")
fun Wiliot.WiliotInitializationScope.initEdgeResolver() {
    if (Wiliot.isInitialized.not()) throw RuntimeException(
        "Unable to execute `initEdgeResolver`. First you should initialize Wiliot with ApplicationContext."
    )
    WiliotEdgeResolver.initialize()
}
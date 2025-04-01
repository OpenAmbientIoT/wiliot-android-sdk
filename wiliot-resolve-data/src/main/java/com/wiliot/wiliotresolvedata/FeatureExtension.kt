package com.wiliot.wiliotresolvedata

import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.contracts.MetaNetworkManagerContract
import com.wiliot.wiliotcore.contracts.UpstreamExtraDataProcessingContract
import com.wiliot.wiliotcore.contracts.WiliotResolveDataModule
import com.wiliot.wiliotcore.contracts.wiring.MetaNetworkManagerProvider
import com.wiliot.wiliotcore.model.Asset
import com.wiliot.wiliotcore.model.AssetWrapper
import com.wiliot.wiliotcore.model.IResolveInfo
import com.wiliot.wiliotcore.model.IResolveInfoImpl
import com.wiliot.wiliotcore.model.PacketData
import com.wiliot.wiliotcore.registerModule
import com.wiliot.wiliotcore.utils.Reporter
import com.wiliot.wiliotcore.utils.logTag
import com.wiliot.wiliotcore.utils.weak
import com.wiliot.wiliotresolvedata.domain.repository.MetaResolveDataRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import java.lang.ref.WeakReference

object WiliotDataResolver : WiliotResolveDataModule {

    private val logTag = logTag()

    const val DEFAULT_ASSET_BLOCKER_DELAY = 5000L

    private var initialized: Boolean = false

    internal var networkManagerProvider: WeakReference<MetaNetworkManagerProvider>? = null

    private var mProcessor: UpstreamExtraDataProcessingContract? = null
    override val processor get() = mProcessor

    @OptIn(ObsoleteCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    internal fun initialize() {
        Reporter.log("initialize", logTag)

        if (initialized.not()) {
            mProcessor = object : UpstreamExtraDataProcessingContract {

                private val cLogTag = "$logTag/Processor"

                override fun start() {
                    Reporter.log("start", cLogTag)
                    MetaResolveDataRepository.initActorAsync()
                }

                override fun stop() {
                    Reporter.log("stop", cLogTag)
                    MetaResolveDataRepository.clearList()
                    MetaResolveDataRepository.suspendActor()
                }

                override fun removeOutdatedBeaconsData(outdated: Long) {
                    MetaResolveDataRepository.sendRemoveOutdatedBeaconsData(outdated)
                }

                override fun clearAllBeaconsMetadata() {
                    MetaResolveDataRepository.clearBeacons()
                }

                override fun processSignalLevel(set: Set<PacketData>, isProMode: Boolean) {
                    MetaResolveDataRepository.sendProcessGetForSignalLevel(set, isProMode)
                }

                override fun processStoredBeaconAddition(packet: PacketData) {
                    MetaResolveDataRepository.sendProcessStoredBeaconAddition(packet)
                }

                override fun processNewBeaconAddition(packet: PacketData) {
                    MetaResolveDataRepository.sendProcessNewBeaconAddition(packet)
                }

                override fun flagBeacons(shouldUpdate: Boolean) {
                    MetaResolveDataRepository.flagBeacons(shouldUpdate = true)
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

    internal suspend fun askForAssets(packet: IResolveInfo, ignoreFlow: Boolean = false) =
        networkManager.askForAssets(packet, ignoreFlow)

    internal suspend fun getWrappedAsset(pixelId: String) = networkManager.askForAssetByPixelId(pixelId)

    // endregion

    //==============================================================================================
    // *** API ***
    //==============================================================================================

    // region [API]

    @OptIn(ObsoleteCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    fun assetsFlow() = MetaResolveDataRepository.assetsState

    @OptIn(ObsoleteCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    fun beaconsFlow() = MetaResolveDataRepository.beaconsState

    @OptIn(ObsoleteCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    fun resolveInfoFlow() = MetaResolveDataRepository.resolveState

    @Suppress("unused")
    @OptIn(ObsoleteCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    fun clearAssets(packet: IResolveInfo) {
        MetaResolveDataRepository.clearAssets(packet)
    }

    @OptIn(ObsoleteCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    fun flagAssets(packet: IResolveInfo) {
        MetaResolveDataRepository.flagAssets(packet)
    }

    @OptIn(ObsoleteCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    fun refreshOwnership(packet: IResolveInfo) {
        MetaResolveDataRepository.refreshOwnership(packet)
    }

    /**
     * Use this outside the SDK core if you need to get Asset details by Pixel ID
     */
    suspend fun loadAssetByPixelId(id: String): Asset? {
        return askForAssets(
            object : IResolveInfo {
                override var name: String = id

                // dummy implementation
                override val deviceMAC: String = "000000000000"
                override var ownerId: String? = null
                override var labels: List<String>? = null
                override var resolveTimestamp: Long = 1
                override var waitingForUpdate: Boolean? = false
                override var asset: Asset? = null
            },
            ignoreFlow = true
        )?.firstOrNull()
    }

    suspend fun loadWrappedAssetByPixelId(id: String): AssetWrapper {
        return getWrappedAsset(id)
    }

    // endregion

    override fun setupNetworkManagerProvider(provider: MetaNetworkManagerProvider) {
        networkManagerProvider = provider.weak()
    }

}

internal val WiliotDataResolver.networkManager: MetaNetworkManagerContract
    get() {
        if (networkManagerProvider == null) throw RuntimeException(
            "MetaNetworkManagerProvider is not initialized. Use WiliotDataResolver.networkManagerBy() extension to initialize it"
        )
        return try {
            networkManagerProvider!!.get()!!.provideMetaNetworkManager()
        } catch (ex: Exception) {
            throw RuntimeException(
                "MetaNetworkManagerProvider failed to provide MetaNetworkManagerContract impl",
                ex
            )
        }
    }

internal suspend fun WiliotDataResolver.resolvePacketData(
    packetData: PacketData,
): IResolveInfoImpl? {
    return networkManager.resolvePacketData(
        packetData
    )
}

@Suppress("UnusedReceiverParameter")
fun Wiliot.WiliotInitializationScope.initDataResolver() {
    if (Wiliot.isInitialized.not()) throw RuntimeException(
        "Unable to execute `initDataResolver`. First you should initialize Wiliot with ApplicationContext."
    )
    WiliotDataResolver.initialize()
}
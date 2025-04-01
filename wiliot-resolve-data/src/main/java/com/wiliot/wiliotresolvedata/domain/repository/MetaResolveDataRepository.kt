package com.wiliot.wiliotresolvedata.domain.repository

import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.model.Asset
import com.wiliot.wiliotcore.model.IResolveInfo
import com.wiliot.wiliotcore.model.IResolveInfoImpl
import com.wiliot.wiliotcore.model.PacketData
import com.wiliot.wiliotcore.utils.Reporter
import com.wiliot.wiliotcore.utils.logTag
import com.wiliot.wiliotresolvedata.WiliotDataResolver
import com.wiliot.wiliotresolvedata.resolvePacketData
import com.wiliot.wiliotupstream.feature.upstream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
object MetaResolveDataRepository {

    private val logTag = logTag()

    private fun buildNewScope() = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var beaconsScope = buildNewScope()
    private var assetsScope = buildNewScope()
    private var beaconsActor: SendChannel<BeaconRepoMsg> = beaconsScope.beaconsRepoActor()
    private var assetsActor: SendChannel<AssetRepoMsg> = assetsScope.assetsRepoActor()

    private val mAssetsState = MutableStateFlow<List<Asset>>(listOf())
    val assetsState = mAssetsState.asStateFlow()
    private val mResolveState = MutableStateFlow<List<IResolveInfo>>(listOf())
    val resolveState = mResolveState.asStateFlow()
    private val mBeaconsState = MutableStateFlow<List<PacketData>>(listOf())
    val beaconsState = mBeaconsState.asStateFlow()

    internal fun initActorAsync() {
        try {
            beaconsScope.ensureActive()
        } catch (e: CancellationException) {
            beaconsScope = buildNewScope().apply {
                beaconsActor = beaconsRepoActor().apply {
                    invokeOnClose {
                        Reporter.log("Beacons actor closed with: $it", logTag)
                    }
                }
            }
        }

        try {
            assetsScope.ensureActive()
        } catch (e: CancellationException) {
            assetsScope = buildNewScope().apply {
                assetsActor = assetsRepoActor().apply {
                    invokeOnClose {
                        Reporter.log("Assets actor closed with: $it", logTag)
                    }
                }
            }
        }
    }

    internal fun suspendActor() {
        beaconsScope.cancel()
        assetsScope.cancel()
    }

    internal fun clearList() {
        clearBeacons()
        clearStates()
    }

    internal fun clearAssets(assetResolveInfo: IResolveInfo? = null) {
        assetsScope.launch {
            assetsActor.send(ClearAssets(assetResolveInfo))
        }
    }

    internal fun flagAssets(assetResolveInfo: IResolveInfo? = null) {
        assetsScope.launch {
            assetsActor.send(FlagAssets(assetResolveInfo))
        }
    }

    internal fun clearBeacons() {
        beaconsScope.launch {
            beaconsActor.send(ClearBeacons())
        }
    }

    private fun clearStates() {
        mAssetsState.value = listOf()
        mResolveState.value = listOf()
        mBeaconsState.value = listOf()
    }

    internal fun refreshOwnership(beaconData: IResolveInfo) {
        val response = CompletableDeferred<Unit?>().apply {
            invokeOnCompletion {
                if (null == it) {
                    Wiliot.upstream().getAllBeacons()
                }
            }
        }
        beaconsScope.launch {
            beaconsActor.send(ClearBeacons(beaconData, response))
        }
    }

    internal fun updateAssetsState(assets: List<Asset>) {
        mAssetsState.value = assets
            .sortedWith(Asset.sortingComparator())
    }

    internal fun updateResolveState(resolve: List<IResolveInfo>) {
        mResolveState.value = resolve
    }

    internal fun updateBeaconsState(resolve: List<PacketData>) {
        val newList = resolve
            .groupingBy { it.name }
            .reduce { _, accumulator, element ->
                accumulator.amount += element.amount
                accumulator
            }.values.sortedBy { it.name }
        mBeaconsState.value = newList
    }

    internal fun sendAddClaimableAsset(asset: Asset) {
        assetsScope.launch {
            assetsActor.send(AddClaimableAsset(asset))
        }
    }

    internal fun sendAskForAsset(payload: IResolveInfo) {
        assetsScope.launch {
            assetsActor.send(AskForAsset(payload))
        }
    }

    internal suspend fun resolve(packetData: PacketData): IResolveInfoImpl? {
        return WiliotDataResolver.resolvePacketData(packetData)
    }

    internal fun flagBeacons(packet: IResolveInfo? = null, shouldUpdate: Boolean) {
        beaconsScope.launch {
            beaconsActor.send(FlagBeacons(packet, shouldUpdate))
        }
    }

    internal fun sendRemoveOutdatedBeaconsData(outdated: Long) {
        beaconsScope.launch {
            beaconsActor.send(RemoveOutdated(outdated))
        }
    }

    internal fun sendProcessGetForSignalLevel(set: Set<PacketData>, isProMode: Boolean) {
        beaconsScope.launch {
            beaconsActor.send(ProcessGetForSignalLevel(set, isProMode))
        }
    }

    internal fun sendProcessStoredBeaconAddition(packet: PacketData) {
        beaconsScope.launch {
            beaconsActor.send(ProcessStoredBeaconAddition(packet))
        }
    }

    internal fun sendProcessNewBeaconAddition(packet: PacketData) {
        beaconsScope.launch {
            beaconsActor.send(ProcessNewBeaconAddition(packet))
        }
    }

}
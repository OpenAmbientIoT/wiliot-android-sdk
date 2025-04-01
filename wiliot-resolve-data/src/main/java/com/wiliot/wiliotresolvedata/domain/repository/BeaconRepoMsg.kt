package com.wiliot.wiliotresolvedata.domain.repository

import com.wiliot.wiliotcore.model.*
import com.wiliot.wiliotcore.utils.Reporter
import com.wiliot.wiliotcore.utils.logTag
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.actor
import java.util.TreeSet
import java.util.function.Predicate
import java.util.stream.Collectors

internal sealed class BeaconRepoMsg

internal class ClearBeacons(
    val packet: IResolveInfo? = null,
    val response: CompletableDeferred<Unit?>? = null,
) : BeaconRepoMsg()

internal class FlagBeacons(
    val packet: IResolveInfo? = null,
    val shouldUpdate: Boolean,
) : BeaconRepoMsg()

internal class RemoveOutdated(
    val outdated: Long,
) : BeaconRepoMsg()

internal class ProcessGetForSignalLevel(
    val set: Set<PacketData>,
    val isProMode: Boolean,
) : BeaconRepoMsg()

internal class ProcessStoredBeaconAddition(
    val packet: PacketData,
) : BeaconRepoMsg()

internal class ProcessNewBeaconAddition(
    val packet: PacketData,
) : BeaconRepoMsg()

private val signalLevelFilterPredicate = Predicate<PacketData> {
    it.isStarterKitTag || it.belongsToMe
}

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
internal fun CoroutineScope.beaconsRepoActor() = actor<BeaconRepoMsg> {
    Reporter.log("Beacons actor created", logTag())
    val resolveInfo = TreeSet<IResolveInfo> { o1, o2 -> o1.deviceMAC.compareTo(o2.deviceMAC) }

    //region implementations

    fun clearBeacons(msg: ClearBeacons) = with(msg) {
        if (null == packet) {
            resolveInfo.clear()
        } else if (!packet.labels.isNullOrEmpty()) {
            val label = packet.labels?.get(0)
            resolveInfo.removeAll {
                it.labels?.contains(label) == true
            }
            response?.complete(null)
        } else {
            resolveInfo.removeAll {
                it.asset?.id.contentEquals(packet.asset?.id)
            }
            response?.complete(null)
        }
        MetaResolveDataRepository.updateResolveState(resolveInfo.toList())
    }

    fun flagBeacons(msg: FlagBeacons) = with(msg) {
        resolveInfo.forEach {
            if (packet == null) {
                it.shouldUpdate = shouldUpdate
            } else {
                if (it.deviceMAC.contentEquals(packet.deviceMAC)) it.shouldUpdate = shouldUpdate
            }
        }
    }

    fun removeOutdated(msg: RemoveOutdated) = with(msg) {
        with(resolveInfo) {
            removeIf { it.resolveTimestamp <= outdated }
        }
    }

    fun processGetForSignalLevel(msg: ProcessGetForSignalLevel) = with(msg) {
        val result = set
            .stream()
            .map { packetData ->
                val resolve =
                    resolveInfo.find { iResolveInfo ->
                        iResolveInfo.deviceMAC == packetData.deviceMAC
                    }
                packetData.updateResolve(resolve)
                packetData
            }.run {
                if (isProMode) {
                    this.filter { !it.isUnknown }
                } else {
                    this.filter(signalLevelFilterPredicate)
                }
            }
            .collect(Collectors.toSet())
        MetaResolveDataRepository.updateBeaconsState(result.toList())
    }

    suspend fun processStoredBeaconAddition(msg: ProcessStoredBeaconAddition) = with(msg) {
        val matchingResolveInfoCount = resolveInfo.count { iResolveInfo ->
            iResolveInfo.deviceMAC.contentEquals(
                packet.deviceMAC
            )
        }
        val itemWaitingForUpdate = resolveInfo.mapNotNull { iResolveInfo ->
            iResolveInfo.takeIf {
                it.deviceMAC.contentEquals(
                    packet.deviceMAC
                ) && it.shouldUpdate
            }
        }.maxByOrNull { it.resolveTimestamp }
        val isWaitingForUpdate = itemWaitingForUpdate != null
        val shouldResolve = matchingResolveInfoCount == 0 || isWaitingForUpdate

        if (shouldResolve) {
            MetaResolveDataRepository.resolve(packet)?.apply {
                resolveInfo.add(this)

                if (isWaitingForUpdate) {
                    if (ownerId?.contentEquals(itemWaitingForUpdate?.ownerId) == true) {
                        MetaResolveDataRepository.flagBeacons(packet = this, shouldUpdate = false)
                    } else {
                        MetaResolveDataRepository.clearAssets(this)
                        MetaResolveDataRepository.updateResolveState(resolveInfo.toList())
                    }
                } else {
                    if (this.isStarterKitTag) {
                        asset?.let {
                            MetaResolveDataRepository.sendAddClaimableAsset(it)
                        }
                    } else {
                        this.takeUnless { p ->
                            p.name == "N/A" || p.name == "not-registered" || p.name == "Unresolved"
                        }?.let { packet ->
                            MetaResolveDataRepository.sendAskForAsset(packet)
                        }
                    }
                    MetaResolveDataRepository.updateResolveState(resolveInfo.toList())
                }
            }
        }
    }

    suspend fun processNewBeaconAddition(msg: ProcessNewBeaconAddition) = with(msg) {
        MetaResolveDataRepository.resolve(packet)?.apply {
            resolveInfo.add(this)
            if (this.isStarterKitTag) {
                this.name
                asset?.let {
                    MetaResolveDataRepository.sendAddClaimableAsset(it)
                }
            } else {
                this.takeUnless { p ->
                    p.name == "N/A" || p.name == "not-registered" || p.name == "Unresolved"
                }?.let { packet ->
                    MetaResolveDataRepository.sendAskForAsset(packet)
                }
            }
            MetaResolveDataRepository.updateResolveState(resolveInfo.toList())
        }
    }

    //endregion

    for (msg in channel) {
        when (msg) {
            is ClearBeacons -> clearBeacons(msg)
            is FlagBeacons -> flagBeacons(msg)
            is RemoveOutdated -> removeOutdated(msg)
            is ProcessGetForSignalLevel -> processGetForSignalLevel(msg)
            is ProcessStoredBeaconAddition -> processStoredBeaconAddition(msg)
            is ProcessNewBeaconAddition -> processNewBeaconAddition(msg)
        }
    }
}
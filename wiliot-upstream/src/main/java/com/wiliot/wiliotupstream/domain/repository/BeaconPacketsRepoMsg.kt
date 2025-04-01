package com.wiliot.wiliotupstream.domain.repository

import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.model.PacketData
import com.wiliot.wiliotcore.utils.Reporter
import com.wiliot.wiliotcore.utils.logTag
import com.wiliot.wiliotupstream.feature.upstream
import com.wiliot.wiliotupstream.feature.withExtraDataProcessor
import com.wiliot.wiliotupstream.utils.MemoryClearanceUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.actor
import java.util.TreeSet

internal sealed class BeaconPacketsRepoMsg

internal data object ClearBeacons : BeaconPacketsRepoMsg()

internal class AddBeaconToResolveOnly(
    val packet: PacketData,
) : BeaconPacketsRepoMsg()

internal class GetForSignalLevel(
    val isProMode: Boolean = false,
) : BeaconPacketsRepoMsg()


@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
internal fun CoroutineScope.beaconsRepoActor() = actor<BeaconPacketsRepoMsg> {
    Reporter.log("BeaconsPackets actor created", logTag())
    val inertBeacons = TreeSet<PacketData> { o1, o2 -> o1.compareTo(o2) }

    fun addBeaconToResolveOnly(msg: AddBeaconToResolveOnly) = with(msg) {
        withExtraDataProcessor {
            val stamp = System.currentTimeMillis() - 30_000
            val outdated = stamp - Wiliot.configuration.expirationLimit
            removeOutdatedBeaconsData(outdated)
            MemoryClearanceUtil.getOptimisedExpirationLimit(MemoryClearanceUtil.Call.Clearance).also {
                Wiliot.configuration = Wiliot.configuration.copy(
                    expirationLimit = it
                )
            }
        }
        packet.let { packet ->
            inertBeacons.contains(packet).let { beaconIsStored ->
                if (beaconIsStored) {
                    inertBeacons.firstOrNull { it.deviceMAC == packet.deviceMAC }
                        ?.apply {
                            this.updateUsingData(packet)
                            withExtraDataProcessor { processStoredBeaconAddition(packet) }
                        }
                } else {
                    inertBeacons.add(packet)
                    withExtraDataProcessor { processNewBeaconAddition(packet) }
                }
            }
            Wiliot.upstream().getAllBeacons()
        }
    }

    fun clearBeacons() {
        inertBeacons.clear()
        withExtraDataProcessor { clearAllBeaconsMetadata() }
    }

    fun getForSignalLevel(msg: GetForSignalLevel) = with(msg) {
        val inertCopy = inertBeacons.toSet().map { it.copy() }
        withExtraDataProcessor { processSignalLevel((inertCopy).toSet(), msg.isProMode) }
    }

    for (msg in channel) {
        when (msg) {
            is ClearBeacons -> clearBeacons()
            is GetForSignalLevel -> getForSignalLevel(msg)
            is AddBeaconToResolveOnly -> addBeaconToResolveOnly(msg)
        }
    }

}

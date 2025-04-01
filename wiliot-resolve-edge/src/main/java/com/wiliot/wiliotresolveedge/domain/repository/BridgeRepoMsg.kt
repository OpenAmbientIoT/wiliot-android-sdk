package com.wiliot.wiliotresolveedge.domain.repository

import com.wiliot.wiliotcore.model.Bridge
import com.wiliot.wiliotcore.model.BridgeStatus
import com.wiliot.wiliotcore.model.BridgeWrapper
import com.wiliot.wiliotcore.utils.Reporter
import com.wiliot.wiliotcore.utils.logTag
import com.wiliot.wiliotresolveedge.WiliotEdgeResolver
import com.wiliot.wiliotresolveedge.domain.repository.BridgeRepoMsg.Companion.RESOLVE_RETRY_DELAY
import com.wiliot.wiliotresolveedge.domain.repository.BridgeRepoMsg.Companion.SYNC_PERIOD
import com.wiliot.wiliotresolveedge.domain.repository.BridgeRepoMsg.Companion.syncJob
import com.wiliot.wiliotresolveedge.domain.repository.BridgeRepoMsg.Companion.syncScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

sealed class BridgeRepoMsg {
    companion object {
        const val RESOLVE_RETRY_DELAY = 30_000L

        const val SYNC_PERIOD = 2000L
        var syncJob: Job? = null
        val syncScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    }
}

class AskForBridgeInfo(
    val packet: BridgeStatus,
) : BridgeRepoMsg()

class AskForBridgeInfoIfNeeded(
    val payload: List<BridgeStatus>,
) : BridgeRepoMsg()

class FlagBridge(
    val bridgeId: String,
) : BridgeRepoMsg()

class UpdatePresence(
    val bridgesIds: List<String>,
    val bridgesRssi: Map<String, Int>?
) : BridgeRepoMsg()

class UpdateFirmwareVersionInfo(
    val bridgeId: String,
    val newVersion: String,
) : BridgeRepoMsg()

data object CheckUnresolvedBridges : BridgeRepoMsg()

data object ClearBridges : BridgeRepoMsg()

class BridgesAdded(
    val bridges: List<BridgeStatus>,
) : BridgeRepoMsg()

@OptIn(ObsoleteCoroutinesApi::class, ExperimentalCoroutinesApi::class)
fun CoroutineScope.bridgesRepoActor() = actor<BridgeRepoMsg> {
    Reporter.log("Bridges actor created", logTag())
    val bridgesInfo = HashSet<Bridge>()
    val unresolvedBridges = hashSetOf<BridgeStatus>()
    val unresolvedTimeStamps = hashMapOf<String, Long>()
    syncJob = syncScope.launch {
        Reporter.log("syncJob launched", logTag())
        do {
            runCatching {
                syncJob?.ensureActive()
                delay(SYNC_PERIOD)
                this@actor.channel.send(CheckUnresolvedBridges)
            }
        } while (true == syncJob?.isActive)
        Reporter.log("mapJob not active anymore", logTag())
    }.apply {
        invokeOnCompletion {
            Reporter.log("syncJob completed", logTag())
        }
    }

    suspend fun askForBridgeInfo(msg: AskForBridgeInfo) = with(msg) {
        WiliotEdgeResolver.askForBridge(this.packet.id).let { wrapper ->
            when (wrapper.result) {
                BridgeWrapper.Result.OK -> {
                    val newInfo = wrapper.bridge!!.copy(currentRssi = this.packet.packet?.scanRssi)
                    bridgesInfo.removeIf { bi -> bi.id.equals(newInfo.id, ignoreCase = true) }
                    bridgesInfo.add(newInfo)
                    unresolvedBridges.removeIf {
                        it.id.contentEquals(newInfo.id, ignoreCase = true)
                    }
                    unresolvedTimeStamps.remove(packet.id)
                    EdgeResolveDataRepository.updateBridgesState(bridgesInfo.toList())
                }
                BridgeWrapper.Result.UNAVAILABLE -> {
                    val newInfo = wrapper.bridge!!.copy(currentRssi = this.packet.packet?.scanRssi)
                    bridgesInfo.removeIf { bi -> bi.id.equals(newInfo.id, ignoreCase = true) }
                    bridgesInfo.add(newInfo)
                    unresolvedBridges.removeIf {
                        it.id.contentEquals(newInfo.id, ignoreCase = true)
                    }
                    unresolvedTimeStamps.remove(packet.id)
                    EdgeResolveDataRepository.updateBridgesState(bridgesInfo.toList())
                }
                BridgeWrapper.Result.UNKNOWN -> {
                    val newInfo = wrapper.bridge!!.copy(currentRssi = this.packet.packet?.scanRssi)
                    unresolvedBridges.removeIf {
                        it.id.contentEquals(msg.packet.id, ignoreCase = true)
                    }
                    unresolvedBridges.add(msg.packet)
                    unresolvedTimeStamps[msg.packet.id] = System.currentTimeMillis()
                    bridgesInfo.removeIf { bi -> bi.id.equals(newInfo.id, ignoreCase = true) }
                    bridgesInfo.add(newInfo)
                }
                BridgeWrapper.Result.ERROR -> {
                    Reporter.exception(
                        message = "Error resolving Bridge info",
                        exception = wrapper.throwable,
                        where = logTag()
                    )
                    unresolvedBridges.removeIf {
                        it.id.contentEquals(msg.packet.id, ignoreCase = true)
                    }
                    unresolvedBridges.add(msg.packet)
                    unresolvedTimeStamps[msg.packet.id] = System.currentTimeMillis()
                }
            }
        }
    }

    fun askForBridgeInfoIfNeeded(msg: AskForBridgeInfoIfNeeded) = with(msg) {
        payload.filter {
            unresolvedBridges.containsBridge(it)
        }.forEach {
            EdgeResolveDataRepository.sendAskForBridgeInfo(it)
        }
    }

    fun checkUnresolved() {
        unresolvedBridges.filter {
            System.currentTimeMillis() - unresolvedTimeStamps.getOrDefault(
                it.id,
                System.currentTimeMillis()
            ) > RESOLVE_RETRY_DELAY
        }.forEach {
            EdgeResolveDataRepository.sendAskForBridgeInfo(it)
            unresolvedTimeStamps.remove(it.id)
        }
    }

    fun flagBridge(msg: FlagBridge) = with(msg) {
        bridgesInfo.firstOrNull {
            it.id.equals(bridgeId, ignoreCase = true)
        }?.let {
            val cpy = it.copy(flagged = true)
            bridgesInfo.remove(it)
            bridgesInfo.add(cpy)
        }

        EdgeResolveDataRepository.updateBridgesState(bridgesInfo.toList())
        EdgeResolveDataRepository.sendAskForBridgeInfo(
            BridgeStatus(
                id = msg.bridgeId,
                formationType = BridgeStatus.FormationType.SYNTHETIC
            )
        )
    }

    fun updatePresence(msg: UpdatePresence) = with(msg) {
        bridgesInfo.filter {
            bridgesIds.containsBridgeId(it)
        }.map {
            val cpy = if (bridgesRssi?.containsBridge(it) == true) {
                val lastRssi = it.currentRssi
                it.copy(
                    lastPresenceTimestamp = System.currentTimeMillis(),
                    currentRssi = bridgesRssi.forBridge(it),
                    lastRssi = lastRssi
                )
            } else {
                it.copy(lastPresenceTimestamp = System.currentTimeMillis())
            }
            bridgesInfo.removeIf { bi -> bi.id.equals(it.id, ignoreCase = true) }
            cpy
        }.let {
            bridgesInfo.addAll(it)
        }

        EdgeResolveDataRepository.updateBridgesState(bridgesInfo.toList())
    }

    fun updateFirmwareVersionInfo(msg: UpdateFirmwareVersionInfo) = with(msg) {
        bridgesInfo.firstOrNull {
            it.id.equals(bridgeId, ignoreCase = true)
        }?.let {
            val cpy = it.copy(fwVersion = newVersion)
            bridgesInfo.remove(it)
            bridgesInfo.add(cpy)
        }

        EdgeResolveDataRepository.updateBridgesState(bridgesInfo.toList())
    }

    fun clearBridges() {
        bridgesInfo.clear()
        unresolvedBridges.clear()
        unresolvedTimeStamps.clear()
    }

    fun onBridgesAdded(msg: BridgesAdded) = with(msg) {
        this.bridges.forEach {
            if (bridgesInfo.firstOrNull { i -> i.id == it.id } == null) {
                // we do not have resolved data about this bridge, but we need to add it anyway
                bridgesInfo.add(
                    Bridge(
                        id = it.id,
                        name = null,
                        claimed = false,
                        owned = false,
                        fwVersion = null,
                        pacingRate = null,
                        energizingRate = null,
                        resolved = false,
                        processedButNotResolved = false,
                        zone = null,
                        location = null,
                        connections = null,
                        lastPresenceTimestamp = System.currentTimeMillis(),
                        currentRssi = it.packet?.scanRssi,
                        boardType = null
                    )
                )
                unresolvedBridges.add(it)
                unresolvedTimeStamps[it.id] = System.currentTimeMillis()
                EdgeResolveDataRepository.updateBridgesState(bridgesInfo.toList())
            }
            EdgeResolveDataRepository.sendAskForBridgeInfo(it)
        }
    }

    for (msg in channel) {
        when (msg) {
            is FlagBridge -> flagBridge(msg)
            is UpdatePresence -> updatePresence(msg)
            is UpdateFirmwareVersionInfo -> updateFirmwareVersionInfo(msg)
            is AskForBridgeInfo -> askForBridgeInfo(msg)
            is AskForBridgeInfoIfNeeded -> askForBridgeInfoIfNeeded(msg)
            is ClearBridges -> clearBridges()
            is CheckUnresolvedBridges -> checkUnresolved()
            is BridgesAdded -> onBridgesAdded(msg)
        }
    }
}

//==============================================================================================
// *** Utils ***
//==============================================================================================

// region [Utils]

private fun HashSet<BridgeStatus>.containsBridge(bridge: BridgeStatus): Boolean {
    return find { ub -> ub.id == bridge.id } != null
}

private fun List<String>.containsBridgeId(bridge: Bridge): Boolean {
    return find { bId -> bId.equals(bridge.id, ignoreCase = true) } != null
}

private fun Map<String, Int>?.containsBridge(bridge: Bridge): Boolean {
    if (this == null) return false
    return keys.find { bId -> bId.equals(bridge.id, ignoreCase = true) } != null
}

private fun Map<String, Int>?.forBridge(bridge: Bridge): Int? {
    if (this == null) return null
    return this.entries.firstOrNull { it.key.equals(bridge.id, ignoreCase = true) }?.value
}

// endregion

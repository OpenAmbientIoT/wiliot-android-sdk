package com.wiliot.wiliotcore.contracts

import com.wiliot.wiliotcore.model.BridgeStatus

/**
 * Contract that allows Upstream to pass filtered Edge-related data for further processing to the
 * resolve module.
 */
interface UpstreamExtraEdgeProcessingContract {

    /**
     * Stop internal processor Actor; called along with Upstream stopping
     */
    fun stop()

    /**
     * Start internal processor Actor; called along with Upstream starting
     */
    fun start()

    /**
     * Notify resolve module with newly discovered Bridge devices (Bridge packets)
     */
    fun bridgesAdded(bridges: List<BridgeStatus>)

    /**
     * Notify with fact about Bridge presence (to keep Nearby state up-to-date)
     */
    fun notifyBridgesPresence(bridgesIds: List<String>, rssiMap: Map<String, Int>)

    /**
     * Perform additional meta request on all Bridges that have not been resolved during last attempt
     */
    fun checkUnresolvedBridges()

    /**
     * Perform additional meta request on exact Bridges that have not been resolved during last attempt
     */
    fun askForBridgesIfNeeded(payload: List<BridgeStatus>)

}
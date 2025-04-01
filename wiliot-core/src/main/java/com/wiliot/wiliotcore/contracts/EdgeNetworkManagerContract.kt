package com.wiliot.wiliotcore.contracts

import com.wiliot.wiliotcore.model.*

interface EdgeNetworkManagerContract {
    /**
     * Used to obtain Bridge info stored in Cloud by Bridge ID
     *
     * @return [BridgeWrapper] that contain requested information. For more details look at [BridgeWrapper]
     */
    suspend fun askForBridge(bridgeId: String): BridgeWrapper
}
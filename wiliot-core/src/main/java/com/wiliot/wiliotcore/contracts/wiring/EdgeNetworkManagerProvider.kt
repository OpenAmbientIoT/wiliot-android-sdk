package com.wiliot.wiliotcore.contracts.wiring

import com.wiliot.wiliotcore.contracts.EdgeNetworkManagerContract

interface EdgeNetworkManagerProvider {
    fun provideEdgeNetworkManager(): EdgeNetworkManagerContract
}
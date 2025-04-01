package com.wiliot.wiliotcore.contracts.wiring

import com.wiliot.wiliotcore.contracts.MetaNetworkManagerContract

interface MetaNetworkManagerProvider {
    fun provideMetaNetworkManager(): MetaNetworkManagerContract
}
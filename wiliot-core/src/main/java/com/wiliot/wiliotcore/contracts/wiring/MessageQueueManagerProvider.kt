package com.wiliot.wiliotcore.contracts.wiring

import com.wiliot.wiliotcore.contracts.MessageQueueManagerContract

interface MessageQueueManagerProvider {
    fun provideMessageQueueManager(): MessageQueueManagerContract
}
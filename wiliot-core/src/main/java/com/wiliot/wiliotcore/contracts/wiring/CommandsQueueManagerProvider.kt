package com.wiliot.wiliotcore.contracts.wiring

import com.wiliot.wiliotcore.contracts.CommandsQueueManagerContract

interface CommandsQueueManagerProvider {
    fun provideCommandsQueueManager(): CommandsQueueManagerContract
}
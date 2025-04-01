package com.wiliot.wiliotedge

import com.wiliot.wiliotcore.contracts.EdgeJob
import com.wiliot.wiliotcore.contracts.EdgeJobExecutorContract

abstract class EdgeJobExecutor<T : EdgeJob> constructor(
    val edgeJob: T
) : EdgeJobExecutorContract<T>
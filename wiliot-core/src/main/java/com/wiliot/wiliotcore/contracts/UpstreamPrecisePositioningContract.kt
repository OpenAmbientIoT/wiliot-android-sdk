package com.wiliot.wiliotcore.contracts

import com.wiliot.wiliotcore.model.PrecisePosition
import kotlinx.coroutines.flow.StateFlow

interface UpstreamPrecisePositioningContract {
    fun getLastDetectedPosition(): StateFlow<PrecisePosition>
    fun reset()
}
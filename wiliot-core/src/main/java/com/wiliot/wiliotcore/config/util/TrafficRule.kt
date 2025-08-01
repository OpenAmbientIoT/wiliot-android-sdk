package com.wiliot.wiliotcore.config.util

import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.model.AdditionalGatewayConfig

object TrafficRule {

    val shouldUploadDirectDataTraffic: Boolean
        get() {
            val p0 = shouldListenToDataTraffic
            val p1 = Wiliot.configuration.dataOutputTrafficFilter != AdditionalGatewayConfig.DataOutputTrafficFilter.BRIDGES_ONLY
            return p0 && p1
        }

    val shouldUploadRetransmittedDataTraffic: Boolean
        get() {
            val p0 = shouldListenToDataTraffic
            val p1 = Wiliot.configuration.dataOutputTrafficFilter != AdditionalGatewayConfig.DataOutputTrafficFilter.PIXELS_ONLY
            return p0 && p1
        }

    val shouldUploadAnyDataTraffic: Boolean
        get() {
            return shouldUploadDirectDataTraffic && shouldUploadRetransmittedDataTraffic
        }

    val shouldListenToDataTraffic: Boolean
        get() {
            return Wiliot.configuration.enableDataTraffic
        }

    val shouldListenToEdgeTraffic: Boolean
        get() {
            return Wiliot.configuration.enableEdgeTraffic
        }

    val shouldEitherListenToDataOrEdgeTraffic: Boolean
        get() {
            return shouldListenToDataTraffic || shouldListenToEdgeTraffic
        }

}
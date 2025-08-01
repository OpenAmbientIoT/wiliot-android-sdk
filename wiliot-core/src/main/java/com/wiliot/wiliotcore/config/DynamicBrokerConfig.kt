package com.wiliot.wiliotcore.config

import com.wiliot.wiliotcore.model.DownlinkCustomBrokerMessage

data class DynamicBrokerConfig(
    val customConfig: CustomDynamicBrokerConfig? = null
) {
    val isDynamicCustomBroker: Boolean
        get() = customConfig != null && customConfig.customBroker

    val broker: String
        get() = if (isDynamicCustomBroker) buildBrokerUrl() else throw IllegalStateException("No custom dynamic broker configured")

    val ownerId: String
        get() = if (isDynamicCustomBroker) extractOwnerId() else throw IllegalStateException("No custom dynamic broker configured")

    private fun extractOwnerId(): String {
        if (isDynamicCustomBroker.not()) throw IllegalStateException("No custom dynamic broker configured")
        return customConfig!!.dataTopic.split("/")[1]
    }

    private fun buildBrokerUrl(): String {
        if (isDynamicCustomBroker.not()) throw IllegalStateException("No custom dynamic broker configured")
        return "${customConfig!!.brokerUrl.convertToSslUrl()}:${customConfig.port}"
    }

    private fun String.convertToSslUrl(): String {
        val regex = Regex("^(mqtts|mqtt|tcp|ssl|ws|wss)://", RegexOption.IGNORE_CASE)
        return this.replace(regex, "ssl://")
    }

}

typealias CustomDynamicBrokerConfig = DownlinkCustomBrokerMessage
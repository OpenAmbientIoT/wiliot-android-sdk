package com.wiliot.wiliotcore.config

import com.wiliot.wiliotcore.model.DownlinkCustomBrokerMessage

data class BrokerConfig(
    val customConfig: CustomBrokerConfig? = null
) {
    val isCustomBroker: Boolean
        get() = customConfig != null && customConfig.customBroker

    val broker: String
        get() = if (isCustomBroker) buildBrokerUrl() else throw IllegalStateException("No custom broker configured")

    val ownerId: String
        get() = if (isCustomBroker) extractOwnerId() else throw IllegalStateException("No custom broker configured")

    private fun extractOwnerId(): String {
        if (isCustomBroker.not()) throw IllegalStateException("No custom broker configured")
        return customConfig!!.dataTopic.split("/")[1]
    }

    private fun buildBrokerUrl(): String {
        if (isCustomBroker.not()) throw IllegalStateException("No custom broker configured")
        return "${customConfig!!.brokerUrl.convertToSslUrl()}:${customConfig.port}"
    }

    private fun String.convertToSslUrl(): String {
        val regex = Regex("^(mqtts|mqtt|tcp|ssl|ws|wss)://", RegexOption.IGNORE_CASE)
        return this.replace(regex, "ssl://")
    }

}

typealias CustomBrokerConfig = DownlinkCustomBrokerMessage
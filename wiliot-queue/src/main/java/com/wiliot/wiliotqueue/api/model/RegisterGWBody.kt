package com.wiliot.wiliotqueue.api.model

import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.config.Configuration

data class RegisterGWBody(
    val gatewayType: String = if (Wiliot.configuration.cloudManaged) Configuration.MDK_GATEWAY_TYPE else Configuration.SOFTWARE_GATEWAY_TYPE,
)
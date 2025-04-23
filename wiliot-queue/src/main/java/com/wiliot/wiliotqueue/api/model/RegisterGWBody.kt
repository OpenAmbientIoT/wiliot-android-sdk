package com.wiliot.wiliotqueue.api.model

import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.config.Configuration

data class RegisterGWBody(
    val gatewayType: String = Configuration.SDK_GATEWAY_TYPE
)
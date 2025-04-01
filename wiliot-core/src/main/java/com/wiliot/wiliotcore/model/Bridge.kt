package com.wiliot.wiliotcore.model

data class Bridge(
    val id: String,
    val name: String?,
    val claimed: Boolean,
    val owned: Boolean,
    val fwVersion: String?,
    val boardType: String?,
    val pacingRate: Int?,
    val energizingRate: Int?,
    val resolved: Boolean,
    val processedButNotResolved: Boolean,
    val connections: List<BridgeConnection>?,
    val zone: CZone?,
    val location: CLocation?,
    val flagged: Boolean = false,
    val rawReportedConfig: BridgeConfig? = null,
    val rawDesiredConfig: BridgeConfig? = null,
    val modules: BridgeConfigModules? = null,
    val lastPresenceTimestamp: Long = System.currentTimeMillis(),
    val lastRssi: Int? = null,
    val currentRssi: Int? = null
) {

    fun pacingRate(): Int? {
        return pacingRate ?: modules?.datapath?.config?.pacerInterval
    }

    fun desiredPacingRate(): Int? {
        return rawDesiredConfig?.pacerInterval ?: modules?.datapath?.desiredConfig?.pacerInterval
    }

    fun energizingRate(): Int? {
        return energizingRate ?: modules?.energy2400?.config?.energyPattern
    }

    fun desiredEnergizingRate(): Int? {
        return rawDesiredConfig?.energyPattern ?: modules?.energy2400?.desiredConfig?.energyPattern
    }

    fun applySort(o2: Bridge): Int {
        if (averageRssi() == null) return 1
        if (o2.averageRssi() == null) return 0
        return if (o2.averageRssi()!! > averageRssi()!!) 1 else 0
    }


    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun equals(other: Any?): Boolean =
        when (other) {
            is Bridge -> {
                id == other.id
                        && name == other.name
                        && claimed == other.claimed
                        && fwVersion == other.fwVersion
                        && flagged == other.flagged
                        && lastPresenceTimestamp == other.lastPresenceTimestamp
                        && currentRssi == other.currentRssi
                        && lastRssi == other.lastRssi
            }
            else -> false
        }

}

fun Bridge.averageRssi(): Int? {
    if (lastRssi == null) return currentRssi
    if (currentRssi != null) return (lastRssi + currentRssi) / 2
    return null
}

data class BridgeConfig(
    val pacingMode: Int?,
    val txPeriodMs: Int?,
    val rxTxPeriodMs: Int?,
    val txRepetition: Int?,
    val energyPattern: Int?,
    val pacerInterval: Int?,
    val txProbability: Int?,
    val sub1GhzFrequency: Int?,
    val _2_4GhzOutputPower: Int?,
    val globalPacingGroup: Int?,
    val otaUpgradeEnabled: Int?,
    val sub1GhzOutputPower: Int?
) {

    fun differsFrom(another: BridgeConfig): Boolean {
        if (this.pacingMode != another.pacingMode) return true
        if (this.txPeriodMs != another.txPeriodMs) return true
        if (this.rxTxPeriodMs != another.rxTxPeriodMs) return true
        if (this.txRepetition != another.txRepetition) return true
        if (this.energyPattern != another.energyPattern) return true
        if (this.pacerInterval != another.pacerInterval) return true
        if (this.txProbability != another.txProbability) return true
        if (this.sub1GhzFrequency != another.sub1GhzFrequency) return true
        if (this._2_4GhzOutputPower != another._2_4GhzOutputPower) return true
        if (this.globalPacingGroup != another.globalPacingGroup) return true
        if (this.otaUpgradeEnabled != another.otaUpgradeEnabled) return true
        if (this.sub1GhzOutputPower != another.sub1GhzOutputPower) return true
        return false
    }

}

data class BridgeConnection(
    val gatewayId: String,
    val connected: Boolean,
    val connectionUpdatedAt: Long
)

data class BridgeWrapper(
    /**
     * Result status for requested Bridge.
     * Could contain [Result.OK], [Result.UNAVAILABLE], [Result.UNKNOWN], [Result.ERROR]
     */
    val result: Result,

    /**
     * Bridge object; could be null in case [result] is not [Result.OK]
     */
    val bridge: Bridge?,

    /**
     * Root cause of error in case request returned error
     */
    val throwable: Throwable? = null,
) {
    enum class Result {
        /**
         * Bridge information successfully retrieved
         */
        OK,

        /**
         * Bridge information is not accessible for current user; means that requested Bridge belongs
         * to another account
         */
        UNAVAILABLE,

        /**
         * Requested Bridge is unknown. It means that such Bridge was not registered in the Cloud yet.
         * Basically it is normal state for out-of-the-box Bridges. Bridge information could be
         * re-requested again in a while.
         */
        UNKNOWN,

        /**
         * Error occurred (random reason)
         */
        ERROR
    }
}

data class BridgeConfigModules(
    val energy2400: Bridge2400Module?,
    val datapath: BridgeDataPathModule?
)

data class BridgeDataPathModule(
    val config: BridgeDataPathModuleConfig?,
    val desiredConfig: BridgeDataPathModuleConfig?,
)

data class BridgeDataPathModuleConfig(
    val pacerInterval: Int?
) {
    fun differsFrom(another: BridgeDataPathModuleConfig?): Boolean {
        if (another == null) return false
        if (another.pacerInterval != pacerInterval) return true
        return false
    }
}

data class Bridge2400Module(
    val config: Bridge2400ModuleConfig?,
    val desiredConfig: Bridge2400ModuleConfig?
)

data class Bridge2400ModuleConfig(
    val energyPattern: Int?
) {
    fun differsFrom(another: Bridge2400ModuleConfig?): Boolean {
        if (another == null) return false
        if (another.energyPattern != energyPattern) return true
        return false
    }
}

fun Bridge.pendingUpdate(): Boolean {
    val oldRawConfigsComparison = if (rawDesiredConfig == null || rawReportedConfig == null) {
        false
    } else {
        rawDesiredConfig.differsFrom(rawReportedConfig)
    }

    val energy2400moduleComparison = if (modules == null) {
        false
    } else {
        modules.energy2400?.let {
            it.config?.differsFrom(it.desiredConfig)
        } ?: false
    }

    val dataPathComparison = if (modules == null) {
        false
    } else {
        modules.datapath?.let {
            it.config?.differsFrom(it.desiredConfig)
        } ?: false
    }

    return oldRawConfigsComparison || energy2400moduleComparison || dataPathComparison
}

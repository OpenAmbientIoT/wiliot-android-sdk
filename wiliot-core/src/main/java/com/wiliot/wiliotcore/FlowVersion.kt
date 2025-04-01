package com.wiliot.wiliotcore

enum class FlowVersion(val serialName: String) {
    V1("v1"),
    V2("v2"),
    INVALID("invalid");

    fun isOneOf(vararg v: FlowVersion): Boolean {
        return this in v
    }

    companion object {
        fun findBySerial(serialName: String?): FlowVersion {
            return (values().find { it.serialName == serialName } ?: INVALID).let {
                it.takeIf {
                    it != INVALID
                } ?: return if (BuildConfig.DEBUG) V1 else it
            }
        }

        fun m2m(): FlowVersion = V2
    }
}
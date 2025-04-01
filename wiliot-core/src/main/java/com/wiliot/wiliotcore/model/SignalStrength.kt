package com.wiliot.wiliotcore.model

enum class SignalStrength(val value: Int) {
    NO_SIGNAL(0),
    POOR(1),
    FAIR(2),
    GOOD(3),
    EXCELLENT(4)
}

fun SignalStrength.atLeast(s: SignalStrength): Boolean {
    return this.value >= s.value
}
package com.wiliot.wiliotcore.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

fun <T> throttle(
    delayMillis: Long = 300L,
    scope: CoroutineScope,
    action: (T) -> Unit
): (T) -> Unit {
    var debounceJob: Job? = null
    return { param: T ->
        if (debounceJob == null) {
            debounceJob = scope.launch {
                action(param)
                delay(delayMillis)
                debounceJob = null
            }
        }
    }
}

fun CoroutineScope.every(millis: Long, initialDelay: Long? = null, block: suspend CoroutineScope.() -> Unit): Job {
    return launch {
        if (initialDelay != null) delay(initialDelay)
        while (isActive) {
            block()
            delay(millis)
        }
    }
}
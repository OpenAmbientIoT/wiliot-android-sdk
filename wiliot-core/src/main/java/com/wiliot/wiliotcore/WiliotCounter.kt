package com.wiliot.wiliotcore

import java.util.concurrent.atomic.AtomicLong

object WiliotCounter {

    private var mCounter = AtomicLong(1)

    val value: Long
        get() = mCounter.get()

    fun inc() {
        if (mCounter.get() < Long.MAX_VALUE) mCounter.incrementAndGet() else reset()
    }

    fun reset() {
        mCounter.set(1)
    }

}
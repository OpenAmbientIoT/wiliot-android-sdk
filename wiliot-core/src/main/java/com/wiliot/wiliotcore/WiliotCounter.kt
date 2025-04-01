package com.wiliot.wiliotcore

object WiliotCounter {

    private var mCounter: Long = 1

    val value: Long
        get() = mCounter

    fun inc() {
        if (mCounter < Long.MAX_VALUE) mCounter++ else reset()
    }

    fun reset() {
        mCounter = 1
    }

}
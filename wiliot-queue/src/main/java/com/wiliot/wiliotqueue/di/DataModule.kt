package com.wiliot.wiliotqueue.di

import android.content.Context
import android.content.SharedPreferences
import com.wiliot.wiliotcore.getWithApplicationContext

private object DataModule {

    private const val TOKEN_PREFS_FILE_NAME = "wiliot_sdk_tokens"

    fun tokenPrefs(): Lazy<SharedPreferences> {
        return lazy { getWithApplicationContext { getSharedPreferences(TOKEN_PREFS_FILE_NAME, Context.MODE_PRIVATE) }!! }
    }

}

fun tokenPrefs(): Lazy<SharedPreferences> = DataModule.tokenPrefs()
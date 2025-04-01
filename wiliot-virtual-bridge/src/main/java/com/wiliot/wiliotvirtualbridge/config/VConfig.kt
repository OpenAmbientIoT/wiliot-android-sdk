package com.wiliot.wiliotvirtualbridge.config

import android.annotation.SuppressLint
import android.content.Context
import com.google.gson.Gson
import com.wiliot.wiliotcore.config.VirtualBridgeConfig
import com.wiliot.wiliotcore.getWithApplicationContext
import com.wiliot.wiliotcore.utils.Reporter
import com.wiliot.wiliotcore.utils.logTag

object VConfig {

    private val logTag = logTag()

    private var mConfig: VirtualBridgeConfig = VirtualBridgeConfig(15_000)

    var config: VirtualBridgeConfig
        get() = mConfig
        set(value) {
            mConfig = value
            getWithApplicationContext(::save)
        }

    fun hash(): Int = mConfig.hashCode().inv() + 1

    private const val PREFS_FILE_NAME = "vMEL_CONFIG"
    private const val CONFIG_KEY = "cfg"

    internal fun load(context: Context) {
        Reporter.log("load", logTag)
        context
            .getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
            .getString(CONFIG_KEY, null)
            ?.let {
                try {
                    Gson().fromJson(it, VirtualBridgeConfig::class.java)?.let { vCfg ->
                        this@VConfig.mConfig = vCfg
                    }
                } catch (ex: Exception) { null }
            } ?: run { save(context) }
    }

    @SuppressLint("ApplySharedPref")
    internal fun save(context: Context) {
        Reporter.log("save", logTag)
        context
            .getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(
                CONFIG_KEY,
                Gson().toJson(this@VConfig.mConfig)
            )
            .commit()
    }

}
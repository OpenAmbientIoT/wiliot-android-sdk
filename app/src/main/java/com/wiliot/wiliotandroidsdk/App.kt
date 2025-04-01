package com.wiliot.wiliotandroidsdk

import android.app.Application
import android.util.Log
import com.wiliot.wiliotandroidsdk.utils.LocationManagerImpl
import com.wiliot.wiliotandroidsdk.utils.getAppVersion
import com.wiliot.wiliotcore.FrameworkDelegate
import com.wiliot.wiliotcore.Wiliot
import com.wiliot.wiliotcore.contextProviderBy
import com.wiliot.wiliotcore.frameworkDelegateBy
import com.wiliot.wiliotcore.initializationCallbackBy
import com.wiliot.wiliotcore.locationManagerBy
import com.wiliot.wiliotcore.setApiKey
import com.wiliot.wiliotcore.utils.helper.WiliotAppConfigurationSource
import com.wiliot.wiliotdownstream.feature.initDownstream
import com.wiliot.wiliotedge.initEdge
import com.wiliot.wiliotqueue.initQueue
import com.wiliot.wiliotupstream.feature.initUpstream
import com.wiliot.wiliotvirtualbridge.feature.initVirtualBridge
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class App : Application(), Wiliot.ContextInitializationProvider {

    override fun onCreate() {
        super.onCreate()
        initWiliot()
    }

    private fun initWiliot() {
        WiliotAppConfigurationSource.initialize(
            object : WiliotAppConfigurationSource.DefaultSdkPreferenceSource() {
                override fun ownerId(): String = "wiliot" // specify your real ownerId here

                // optional
                override fun btPacketsCounterEnabled(): Boolean = true

                // optional (force restarts in case of failure)
                override fun isServicePhoenixEnabled(): Boolean = true

                // recommended to set it to 'true' to be able to manage GW settings from Platform
                override fun isRunningInCloudManagedMode() = true
            }
        )

        Wiliot.init {
            this contextProviderBy this@App

            setApiKey("OTNiNDlmNGItOGY0OC00OTE3LTk5ZDUtMWUzNjhmMzEyNDI5OlI0RUNCN1Z1WmZBVWZSWFZxQjZwZzd2ZzNINDVVVVZvNEN1WjZKYzlsQ1k=")

            this frameworkDelegateBy object : FrameworkDelegate() {
                override fun applicationVersion(): Int {
                    return getAppVersion().first
                }

                override fun applicationVersionName(): String {
                    return getAppVersion().second
                }
            }

            this locationManagerBy LocationManagerImpl

            this initializationCallbackBy object : Wiliot.InitializationCallback {
                override fun onInitializationFinished() {
                    Log.i("App", "Wiliot initialization finished")
                }
            }

            initQueue()
            initEdge()
            initVirtualBridge()
            initUpstream()
            initDownstream()
        }
    }

    override fun provideContext(): Application = this

}
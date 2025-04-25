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
                // Note, that you should also specify the `applicationPackage` and `launcherActivity`
                override fun isServicePhoenixEnabled(): Boolean = true
            }
        )

        Wiliot.applicationPackage = "com.wiliot.wiliotandroidsdk"
        Wiliot.launcherActivity = "com.wiliot.wiliotandroidsdk.MainActivity"

        Wiliot.init {
            this contextProviderBy this@App

            setApiKey(BuildConfig.WILIOT_API_KEY)

            this frameworkDelegateBy object : FrameworkDelegate() {
                override fun applicationName(): String {
                    return "Wiliot SDK Sample App"
                }

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
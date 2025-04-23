package com.wiliot.wiliotcore

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.location.Location
import androidx.core.app.NotificationManagerCompat
import com.wiliot.wiliotcore.config.BrokerConfig
import com.wiliot.wiliotcore.config.Configuration
import com.wiliot.wiliotcore.contracts.PrimaryTokenExpirationCallback
import com.wiliot.wiliotcore.contracts.WiliotModule
import com.wiliot.wiliotcore.contracts.WiliotVirtualBridgeModule
import com.wiliot.wiliotcore.embedded.auth.PrimaryTokenInjectionConsumer
import com.wiliot.wiliotcore.embedded.auth.WiliotSdkAuthPoint
import com.wiliot.wiliotcore.gw.gwId
import com.wiliot.wiliotcore.location.LocationManagerContract
import com.wiliot.wiliotcore.sensors.WiliotSensorManager
import com.wiliot.wiliotcore.utils.Reporter
import com.wiliot.wiliotcore.utils.logTag
import com.wiliot.wiliotcore.utils.network.WiliotHeadersSystemInfo
import com.wiliot.wiliotcore.utils.service.WltServiceNotification
import com.wiliot.wiliotcore.utils.weak
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.Collections

object Wiliot {

    val sdkVersion: String
        get() = BuildConfig.BOM_VERSION

    internal val tokenInjectionConsumers: MutableSet<PrimaryTokenInjectionConsumer> = mutableSetOf()

    internal val modules: MutableSet<WiliotModule> = mutableSetOf()

    interface ContextInitializationProvider {
        fun provideContext(): Application
    }

    interface InitializationCallback {
        fun onInitializationFinished()
    }

    object WiliotInitializationScope

    internal var initCallback: InitializationCallback? = null

    var lastObtainedBatteryTemp: Float = 0f

    private lateinit var uniqueDeviceId: String

    internal var applicationPackageName: String = "com.wiliot.app" // just a default value
    internal var applicationMainActivity: String = "com.wiliot.app.MainActivity" // just a default value

    var applicationPackage: String
        get() = applicationPackageName
        set(value) {
            applicationPackageName = value
        }
    var launcherActivity: String
        get() = applicationMainActivity
        set(value) {
            applicationMainActivity = value
        }

    internal var initialized: Boolean = false
    val isInitialized: Boolean
        get() = initialized

    internal var appContext: WeakReference<ContextInitializationProvider>? = null
    val accelerometer by lazy { WiliotSensorManager }
    val extraGatewayInfoSynchronized: MutableMap<String, String> = Collections.synchronizedMap(HashMap<String, String>())

    var positioningLastDetectedFloor: Int = 1

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    internal var apiKey: String? = null

    internal var mTokenExpirationCallback: PrimaryTokenExpirationCallback = WiliotSdkAuthPoint
    val tokenExpirationCallback: PrimaryTokenExpirationCallback
        get() = mTokenExpirationCallback

    val virtualBridgeId: String?
        get() {
            return modules.firstOrNull { it is WiliotVirtualBridgeModule }?.let {
                (it as? WiliotVirtualBridgeModule)?.bridgeId
            }
        }

    internal var mLocationManager: LocationManagerContract = object : LocationManagerContract {

        override fun startObserveLocation(context: Context) {
            // Nothing
        }

        override fun stopLocationUpdates(context: Context?) {
            // Nothing
        }

        override fun getLastLocation(): Location? {
            return null
        }
    }
    val locationManager: LocationManagerContract
        get() = mLocationManager

    fun init(scope: WiliotInitializationScope.() -> Unit) {
        scope.invoke(WiliotInitializationScope)

        if (appContext == null) throw RuntimeException(
            "ContextInitializationProvider is not initialized. Initialize it during Wiliot.init()"
        )
        if (tokenExpirationCallback is WiliotSdkAuthPoint && apiKey.isNullOrBlank()) throw RuntimeException(
            "API Key is not provided. Provide it during Wiliot.init()"
        )

        uniqueDeviceId = getWithApplicationContext { gwId() }!!

        WiliotHeadersSystemInfo.apply {
            appName = delegate.applicationName()
            appVersion = delegate.applicationVersionName()
            appVersionCode = delegate.applicationVersion().toString()
            gwId = uniqueDeviceId
        }

        initCallback?.onInitializationFinished()
    }

    @SuppressLint("MissingPermission")
    fun notifyServicesStarting(immediate: Boolean = false) {
        Reporter.log("notifyServicesStarting", logTag())
        coroutineScope.launch {
            if (immediate.not()) delay(1500)
            withApplicationContext {
                WltServiceNotification.getWltMainNotification(ContextWrapper(this))?.also { notification ->
                    NotificationManagerCompat.from(this).run {
                        notify(WltServiceNotification.WLT_MAIN_NOTIFICATION_ID, notification)
                    }
                }
            }
        }
    }

    fun notifyServicesStopping() {
        Reporter.log("notifyServicesStopping", logTag())
        withApplicationContext {
            NotificationManagerCompat.from(this).run {
                cancel(77) // MQTT NOTIFICATION ID
                cancel(WltServiceNotification.WLT_MAIN_NOTIFICATION_ID)
            }
        }
    }

    var configuration: Configuration = Configuration()
    var delegate: FrameworkDelegate = object : FrameworkDelegate() {}

    var brokerConfig: BrokerConfig = BrokerConfig()

    fun getFullGWId(): String = if (initialized) uniqueDeviceId else throw IllegalStateException(
        "Wiliot SDK not initialized. You should call Wiliot.initialize(Context) before accessing SDK features"
    )

    @Suppress("unused")
    fun getShortGwId(): String = getFullGWId().takeLast(6)

    fun withApplicationContext(block: Context.() -> Unit) = appContext?.get()?.provideContext()?.let(block)

}

inline fun <reified T> getWithApplicationContext(crossinline block: Context.() -> T): T? {
    var t: T? = null
    Wiliot.withApplicationContext { t = block.invoke(this) }
    return t
}

infix fun Wiliot.WiliotInitializationScope.primaryTokenExpirationCallbackBy(primaryTokenExpirationCallback: PrimaryTokenExpirationCallback) {
    Wiliot.mTokenExpirationCallback = primaryTokenExpirationCallback
}

@SuppressLint("QueryPermissionsNeeded")
infix fun Wiliot.WiliotInitializationScope.contextProviderBy(provider: Wiliot.ContextInitializationProvider) {
    Wiliot.appContext = provider.weak()
    with (provider.provideContext()) {
        Wiliot.applicationPackageName = packageName
        val intent = packageManager.getLaunchIntentForPackage(packageName)!!
        Wiliot.applicationMainActivity = packageManager.queryIntentActivities(intent, 0)[0].activityInfo.name
    }
    Reporter.log(
        "Wiliot initialized with ${Wiliot.applicationPackageName} with main LAUNCHER ${Wiliot.applicationMainActivity}",
        Wiliot.logTag()
    )

    Wiliot.initialized = true
}

infix fun Wiliot.WiliotInitializationScope.frameworkDelegateBy(delegate: FrameworkDelegate) {
    Wiliot.delegate = delegate
}

@Suppress("UnusedReceiverParameter")
fun Wiliot.WiliotInitializationScope.setConfiguration(configuration: Configuration) {
    Wiliot.configuration = configuration
}

infix fun Wiliot.WiliotInitializationScope.locationManagerBy(manager: LocationManagerContract) {
    Wiliot.mLocationManager = manager
}

infix fun Wiliot.WiliotInitializationScope.initializationCallbackBy(callback: Wiliot.InitializationCallback) {
    Wiliot.initCallback = callback
}

fun Wiliot.addToTokenInjectionRegistry(consumer: PrimaryTokenInjectionConsumer) {
    tokenInjectionConsumers.add(consumer)
}

@Suppress("UnusedReceiverParameter")
fun Wiliot.WiliotInitializationScope.setApiKey(apiKey: String) {
    Wiliot.apiKey = apiKey
}

@Suppress("UnusedReceiverParameter")
fun Wiliot.WiliotInitializationScope.setOwnerId(owner: String) {
    Wiliot.configuration = Wiliot.configuration.copy(ownerId = owner)
}

@Suppress("UnusedReceiverParameter")
fun Wiliot.resetTokens() {
    WiliotTokenKeeper.setNewToken(null)
}

fun Wiliot.registerModule(module: WiliotModule) {
    modules.add(module)
    Reporter.log("REGISTERED MODULE: $module", logTag())
}

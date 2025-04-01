package com.wiliot.wiliotandroidsdk.di

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import com.wiliot.wiliotcore.utils.bluetoothManager
import com.wiliot.wiliotcore.utils.locationManager
import com.wiliot.wiliotcore.utils.powerManager
import com.wiliot.wiliotcore.utils.wifiManager
import com.wiliot.wiliotandroidsdk.permissions.contract.ApplicationPermissionsContract
import com.wiliot.wiliotandroidsdk.permissions.contract.ApplicationPermissionsContract.*
import com.wiliot.wiliotandroidsdk.permissions.contract.ApplicationPermissionsContract.Result.*
import com.wiliot.wiliotandroidsdk.permissions.contract.PermissionResultCallback
import com.wiliot.wiliotandroidsdk.utils.NetworkUtils
import com.wiliot.wiliotandroidsdk.utils.log
import com.wiliot.wiliotandroidsdk.utils.logTag
import com.wiliot.wiliotandroidsdk.utils.weak
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

internal class ApplicationPermissionsDelegate @Inject constructor(
    @ApplicationContext val context: Context,
) : ApplicationPermissionsContract {

    private val logTag = logTag()

    //==============================================================================================
    // *** Host Activity ***
    //==============================================================================================

    // region [Host Activity]

    private var hostActivity: WeakReference<ComponentActivity>? = null

    /**
     * Should be called before onResume of Activity
     */
    fun setupHostActivity(activity: ComponentActivity) {
        hostActivity = activity.weak()

        bleRequest = withHostActivity {
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                permissionRequestCallback?.invoke(
                    Permission.BLUETOOTH_ENABLE,
                    isBluetoothModuleEnabled.asGrantResult()
                )
            }
        }

        bleRequestV31 = withHostActivity {
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { p ->
                permissionRequestCallback?.invoke(
                    Permission.BLUETOOTH_NEARBY,
                    (p.values.reduce { acc, b -> acc && b }).asGrantResult()
                )
            }
        }

        notificationRequest = withHostActivity {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {
                permissionRequestCallback?.invoke(
                    Permission.NOTIFICATIONS_33,
                    isNotificationsGranted.asGrantResult()
                )
            }
        }

        locationRequest = withHostActivity {
            registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { p ->
                permissionRequestCallback?.invoke(
                    Permission.LOCATION,
                    (p.values.reduce { acc, b -> acc && b }).asGrantResult()
                )
            }
        }

        backgroundLocationRequest = withHostActivity {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {
                permissionRequestCallback?.invoke(
                    Permission.BACKGROUND_LOCATION,
                    it.asGrantResult()
                )
            }
        }

        internetAdapterRequest = withHostActivity {
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                permissionRequestCallback?.invoke(
                    Permission.INTERNET_ADAPTER_ENABLE,
                    isInternetAdapterEnabled.asGrantResult()
                )
            }
        }

        batteryOptimisationRequest = withHostActivity {
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                permissionRequestCallback?.invoke(
                    Permission.OPTIMISATION_DISABLE,
                    isBatteryOptimisationDisabled.asGrantResult()
                )
            }
        }

    }

    private inline fun <R> withHostActivity(
        block: ComponentActivity.() -> R,
    ): R? {
        with(hostActivity?.get()) {
            takeIf { it != null }?.let { return block.invoke(it) }
        }
        return null
    }

    // endregion

    //==============================================================================================
    // *** Permissions: Domain ***
    //==============================================================================================

    // region [Permissions: Domain]

    // region [[ - Common - ]]

    private var permissionRequestCallback: PermissionResultCallback? = null

    private fun showAppPermissionSettings() {
        withHostActivity {
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                "package:${this.packageName}".toUri()
            ).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK and Intent.FLAG_ACTIVITY_NO_HISTORY and Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                startActivity(this)
            }
        }
    }

    private fun showGpsSystemSettings() {
        withHostActivity {
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
    }

    private fun requestBluetoothPermission(resultCallback: PermissionResultCallback) {
        log("requestBluetoothPermission", logTag)
        permissionRequestCallback = resultCallback
        checkOrRequestBluetoothPermissions()
    }

    private fun requestBluetoothEnabling(resultCallback: PermissionResultCallback) {
        log("requestBluetoothEnabling", logTag)
        permissionRequestCallback = resultCallback
        checkOrRequestBluetoothEnabled()
    }

    private fun requestNotificationsPermission(resultCallback: PermissionResultCallback) {
        log("requestNotificationsPermission", logTag)
        permissionRequestCallback = resultCallback
        checkOrRequestNotificationsPermission()
    }

    private fun requestLocationPermission(resultCallback: PermissionResultCallback) {
        log("requestLocationPermission", logTag)
        permissionRequestCallback = resultCallback
        checkOrRequestLocationPermissions()
    }

    private fun requestBackgroundLocationPermission(resultCallback: PermissionResultCallback) {
        log("requestBackgroundLocationPermission", logTag)
        permissionRequestCallback = resultCallback
        checkOrRequestBackgroundLocation()
    }

    private fun requestLocationEnabling(resultCallback: PermissionResultCallback) {
        log("requestLocationEnabled", logTag)
        permissionRequestCallback = resultCallback
        checkOrRequestGpsEnabling()
    }

    private fun requestInternetEnabling(resultCallback: PermissionResultCallback) {
        log("requestInternetEnabling", logTag)
        permissionRequestCallback = resultCallback
        checkOrRequestInternetEnabled()
    }

    private fun requestOptimisationDisabling(resultCallback: PermissionResultCallback) {
        log("requestOptimisationDisabling", logTag)
        permissionRequestCallback = resultCallback
        checkOrRequestOptimisationDisabled()
    }

    private fun requestCameraPermission(resultCallback: PermissionResultCallback) {
        log("requestCameraPermission", logTag)
        permissionRequestCallback = resultCallback
        checkOrRequestCameraPermission()
    }

    private fun requestWriteExternalStoragePermission(resultCallback: PermissionResultCallback) {
        log("requestWriteExternalStoragePermission", logTag)
        permissionRequestCallback = resultCallback
        checkOrRequestWriteExternalStoragePermission()
    }

    // endregion

    // region [[ - Bluetooth - ]]

    private val isBluetoothAccessGranted
        get() = if (Build.VERSION.SDK_INT <= 30) {
            true
        } else {
            val connect =
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            val scan =
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            val advertise =
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
            connect && scan && advertise
        }

    private val isBluetoothModuleEnabled
        get() = try {
            context.bluetoothManager.adapter.isEnabled
        } catch (t: Throwable) {
            Log.e(logTag, "Error getting bluetooth state", t)
            false
        }

    private var bleRequest: ActivityResultLauncher<Intent>? = null
    private var bleRequestV31: ActivityResultLauncher<Array<String>>? = null

    private fun checkOrRequestBluetoothEnabled() {
        with(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) {
            bleRequest?.launch(this)
        }
    }

    private fun checkOrRequestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            bleRequestV31?.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE
                )
            )
        } else {
            permissionRequestCallback?.invoke(
                Permission.BLUETOOTH_NEARBY,
                GRANTED
            )
        }
    }

    // endregion

    // region [[ - Notifications - ]]

    private val isNotificationsGranted: Boolean
        get() {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        }

    private var notificationRequest: ActivityResultLauncher<String>? = null

    private fun checkOrRequestNotificationsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationRequest?.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissionRequestCallback?.invoke(Permission.NOTIFICATIONS_33, GRANTED)
        }
    }

    // endregion

    // region [[ - Location - ]]

    private val isLocationAccessGranted: Boolean
        get() {
            val coarse =
                context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val fine =
                context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            return coarse && fine
        }

    private val isBackgroundLocationAccessGranted
        @SuppressLint("ObsoleteSdkInt")
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    private val isLocationEnabled: Boolean
        get() {
            return context.locationManager.getProviders(true).isNotEmpty()
        }

    private var locationRequest: ActivityResultLauncher<Array<String>>? = null
    private var backgroundLocationRequest: ActivityResultLauncher<String>? = null

    private fun checkOrRequestLocationPermissions() {
        locationRequest?.launch(
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        )
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun checkOrRequestBackgroundLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            backgroundLocationRequest?.launch(
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
        } else {
            permissionRequestCallback?.invoke(
                Permission.BACKGROUND_LOCATION,
                GRANTED
            )
        }
    }

    private fun checkOrRequestGpsEnabling() {
        showGpsSystemSettings()
    }

    // endregion

    // region [[ - Camera - ]]

    private val isCameraGranted: Boolean
        get() = context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private var cameraRequest: ActivityResultLauncher<String>? = null

    private fun checkOrRequestCameraPermission() {
        cameraRequest?.launch(
            Manifest.permission.CAMERA
        )
    }

    // endregion

    // region [[ - Write External Storage - ]]

    private val isWriteExternalStorageGranted: Boolean
        get() = context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private var writeExternalStorageRequest: ActivityResultLauncher<String>? = null

    private fun checkOrRequestWriteExternalStoragePermission() {
        writeExternalStorageRequest?.launch(
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    // endregion

    // region [[ - Internet Adapter - ]]

    private val isInternetAdapterEnabled
        get() = NetworkUtils.isInternetConnected(context)

    private var internetAdapterRequest: ActivityResultLauncher<Intent>? = null

    @SuppressLint("ObsoleteSdkInt")
    private fun checkOrRequestInternetEnabled() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            with(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)) {
                internetAdapterRequest?.launch(this)
            }
        } else {
            try {
                @Suppress("DEPRECATION")
                context.wifiManager.isWifiEnabled = true
                permissionRequestCallback?.invoke(
                    Permission.INTERNET_ADAPTER_ENABLE,
                    true.asGrantResult()
                )
            } catch (t: Throwable) {
                log(
                    "Can not enable internet adapter",
                    logTag
                )
            }
        }
    }

    // endregion

    // region [[ - Optimisation - ]]

    private val isBatteryOptimisationDisabled
        get() = try {
            context.powerManager.isIgnoringBatteryOptimizations(hostActivity?.get()?.packageName)
        } catch (t: Throwable) {
            log(
                "Can not get optimisation state",
                logTag
            )
            false
        }

    private var batteryOptimisationRequest: ActivityResultLauncher<Intent>? = null

    @SuppressLint("BatteryLife")
    private fun checkOrRequestOptimisationDisabled() {
        withHostActivity {
            with(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    "package:${this.packageName}".toUri()
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK and Intent.FLAG_ACTIVITY_NO_HISTORY and Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                }
            ) {
                batteryOptimisationRequest?.launch(this)
            }
        }
    }

    // endregion

    // endregion

    //==============================================================================================
    // *** Utils ***
    //==============================================================================================

    // region [Utils]

    private fun Boolean.asGrantResult(): Result {
        return if (this)
            GRANTED
        else
            REJECTED
    }

    // endregion

    //==============================================================================================
    // *** Contract: Checks ***
    //==============================================================================================

    // region [Checks]

    override fun isPermissionGranted(permission: Permission): Boolean {
        return when (permission) {
            Permission.BLUETOOTH_NEARBY -> isBluetoothAccessGranted
            Permission.BLUETOOTH_ENABLE -> isBluetoothModuleEnabled
            Permission.LOCATION -> isLocationAccessGranted
            Permission.BACKGROUND_LOCATION -> isBackgroundLocationAccessGranted
            Permission.LOCATION_ENABLE -> isLocationEnabled
            Permission.INTERNET_ADAPTER_ENABLE -> isInternetAdapterEnabled
            Permission.OPTIMISATION_DISABLE -> isBatteryOptimisationDisabled
            /*Permission.CAMERA -> isCameraGranted
            Permission.WRITE_EXTERNAL_STORAGE -> isWriteExternalStorageGranted*/
            Permission.NOTIFICATIONS_33 -> isNotificationsGranted
        }
    }

    override fun allPermissionsGranted(bundle: PermissionsBundle): Boolean {
        return bundle.permissionsList.fold(0) { acc, p ->
            acc + if (isPermissionGranted(p)) 1 else 0
        } == bundle.permissionsList.size
    }

    // endregion

    //==============================================================================================
    // *** Contract: Requests ***
    //==============================================================================================

    // region [Requests]

    override fun requestPermission(
        permission: Permission,
        resultCallback: PermissionResultCallback,
    ) {
        when (permission) {
            Permission.BLUETOOTH_NEARBY -> requestBluetoothPermission(resultCallback)
            Permission.BLUETOOTH_ENABLE -> requestBluetoothEnabling(resultCallback)
            Permission.LOCATION -> requestLocationPermission(resultCallback)
            Permission.BACKGROUND_LOCATION -> requestBackgroundLocationPermission(resultCallback)
            Permission.LOCATION_ENABLE -> requestLocationEnabling(resultCallback)
            Permission.INTERNET_ADAPTER_ENABLE -> requestInternetEnabling(resultCallback)
            Permission.OPTIMISATION_DISABLE -> requestOptimisationDisabling(resultCallback)
            /*Permission.CAMERA -> requestCameraPermission(resultCallback)
            Permission.WRITE_EXTERNAL_STORAGE -> requestWriteExternalStoragePermission(
                resultCallback
            )*/
            Permission.NOTIFICATIONS_33 -> requestNotificationsPermission(resultCallback)
        }
    }

    override fun requestApplicationSettings() {
        log("requestApplicationSettings", logTag)
        showAppPermissionSettings()
    }

    override fun requestGpsSettings() {
        log("requestGpsSettings", logTag)
        showGpsSystemSettings()
    }

    // endregion

}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    internal abstract fun bindPermissionsDelegate(provider: ApplicationPermissionsDelegate): ApplicationPermissionsContract

}
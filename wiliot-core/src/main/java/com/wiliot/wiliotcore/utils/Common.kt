package com.wiliot.wiliotcore.utils

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.AlarmManager
import android.bluetooth.BluetoothManager
import android.content.ClipboardManager
import android.content.Context
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.HardwarePropertiesManager
import android.os.Parcel
import android.os.ParcelUuid
import android.os.Parcelable
import android.os.PowerManager
import android.util.Log
import android.util.SparseArray
import com.wiliot.wiliotcore.BuildConfig
import com.wiliot.wiliotcore.utils.jwt.JWT
import com.wiliot.wiliotcore.utils.jwt.JwtDecodeException
import com.wiliot.wiliotcore.utils.jwt.JwtUtils
import com.wiliot.wiliotcore.utils.jwt.getClaim
import com.wiliot.wiliotcore.utils.jwt.getExpiresAt
import com.wiliot.wiliotcore.utils.jwt.getIssuedAt
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.ref.WeakReference
import java.util.*
import java.util.stream.Collectors
import kotlin.math.floor
import kotlin.math.pow

//==============================================================================================
// *** Misc ***
//==============================================================================================

// region [Misc]

fun Any.logTag(): String {
    return this::class.java.simpleName
}

fun <T> T.weak() = WeakReference(this)

inline fun <T, R> withValidReference(
    weakReference: WeakReference<T>?,
    block: T.() -> R,
): R? {
    with(weakReference?.get()) {
        takeIf { it != null }?.let { return block.invoke(it) }
    }
    return null
}

// endregion

//==============================================================================================
// *** System services ***
//==============================================================================================

// region [System services]

val Context.activityManager: ActivityManager
    get() = systemService(Context.ACTIVITY_SERVICE)

val Context.clipboardManager: ClipboardManager
    get() = systemService(Context.CLIPBOARD_SERVICE)

val Context.powerManager: PowerManager
    get() = systemService(Context.POWER_SERVICE)

val Context.alarmManager: AlarmManager
    get() = systemService(Context.ALARM_SERVICE)

val Context.bluetoothManager: BluetoothManager
    get() = systemService(Context.BLUETOOTH_SERVICE)

val Context.locationManager: LocationManager
    get() = systemService(Context.LOCATION_SERVICE)

val Context.wifiManager: WifiManager
    get() = systemService(Context.WIFI_SERVICE)

val Context.connectivityManager: ConnectivityManager
    get() = systemService(Context.CONNECTIVITY_SERVICE)

val Context.cameraManager: CameraManager
    get() = systemService(Context.CAMERA_SERVICE)

val Context.batteryManager: BatteryManager
    get() = systemService(Context.BATTERY_SERVICE)

val Context.hardwareManager: HardwarePropertiesManager
    get() = systemService(Context.HARDWARE_PROPERTIES_SERVICE)

@Suppress("UNCHECKED_CAST")
private fun <T> Context.systemService(name: String): T {
    return this.getSystemService(name) as T
}

// endregion

//==============================================================================================
// *** Converters ***
//==============================================================================================

// region [Converters]

/**
 * Converts [ByteArray] to HEX String
 */
fun ByteArray?.toHexString(): String {
    this?.let {

        val hexArray = "0123456789ABCDEF".toCharArray()

        val hexChars = CharArray(it.size * 2)
        for (j in it.indices) {
            val v = get(j).toInt() and 0xFF

            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars).lowercase(Locale.ROOT)
    }
    return ""
}

/**
 * Decodes HEX String to [ByteArray]
 */
fun String.decodeHexToByteArray(): ByteArray {
    check(length % 2 == 0) { "Must have an even length" }

    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}

fun batteryStatusString(status: Int) = when (status) {
    BatteryManager.BATTERY_STATUS_CHARGING -> {
        "CHARGING"
    }

    BatteryManager.BATTERY_STATUS_DISCHARGING -> {
        "DISCHARGING"
    }

    BatteryManager.BATTERY_STATUS_FULL -> {
        "FULL"
    }

    BatteryManager.BATTERY_STATUS_NOT_CHARGING -> {
        "NOT_CHARGING"
    }

    else -> {
        "UNKNOWN"
    }
}

/**
 * @return String containing information about device temperature sensors. Note, that this information
 * could not be retrieved on some devices.
 */
fun hardwareStatusString(): String = try {

    with(Runtime.getRuntime()) {
        val thermalItems = exec("ls /sys/devices/virtual/thermal").let { process ->
            process.waitFor()
            val r = BufferedReader(InputStreamReader(process.inputStream))
            val result = r.lines().collect(Collectors.toList())
            r.close()
            result
        }.filter {
            it.contains("thermal_zone")
        }

        val thermalTypesMap = mutableMapOf<String, String>()

        thermalItems.filter { thermalItem ->
            exec("cat /sys/devices/virtual/thermal/$thermalItem/type").let { process ->
                process.waitFor()
                val r = BufferedReader(InputStreamReader(process.inputStream))
                val type = r.readLine()
                val result = type.let { resultLine ->
                    resultLine?.startsWith("cpuss") == true || resultLine?.startsWith("battery") == true || resultLine?.startsWith(
                        "ddr-usr"
                    ) == true
                }
                r.close()
                result.also {
                    if (it) thermalTypesMap[thermalItem] = type
                }
            }
        }.map { thermalItem ->
            exec("cat /sys/devices/virtual/thermal/$thermalItem/temp").let { process ->
                process.waitFor()
                val r = BufferedReader(InputStreamReader(process.inputStream))
                val temp = r.readLine()
                r.close()
                Pair(thermalTypesMap[thermalItem], temp)
            }
        }.joinToString(", ") {
            "${it.first}=${it.second.toInt().toFloat() / 1000f}"
        }
    }

} catch (ex: Exception) {
    if (BuildConfig.DEBUG) {
        Reporter.exception("hardwareStatusString error", ex, "CommonUtils")
    }
    "Not Available"
}

/**
 * Converts binary number to decimal UInt
 * E.g. 1110L -> 14u
 */
fun bitMask(num: Long): UInt {
    var n = num
    var decimalNumber = 0
    var i = 0
    var remainder: Long

    while (n.toInt() != 0) {
        remainder = n % 10
        n /= 10
        decimalNumber += (remainder * 2.0.pow(i.toDouble())).toInt()
        ++i
    }
    return decimalNumber.toUInt()
}

fun Int.twoComplement(): Int {
    return (this + 40).inv() + 1
}

// endregion

//==============================================================================================
// *** JWT ***
//==============================================================================================

// region [JWT]

@Throws(JwtDecodeException::class)
fun String.asJWT() = JwtUtils.parseJwt(this)

fun JWT?.isValidJwt(checkExpiration: Boolean = true): Boolean {
    if (this == null) return false
    if (checkExpiration.not()) return true
    return if (WiliotLowDebugConfig.shortGatewayTokenLifetime) {
        val fakeExpiresAt: Long =
            (this.getExpiresAt()?.time ?: 0) - ((11 * 60 * 60 * 1000) + (59 * 60 * 1000) + (30 * 1000))
        val now = System.currentTimeMillis()
        val fakeDiff = (fakeExpiresAt - now)
        fakeDiff > 0
    } else {
        val leeway: Long = Math.subtractExact(getExpiresAt()?.time ?: 0, getIssuedAt()?.time ?: 0) / 12
        val todayTime = (floor((Date().time / 1000L).toDouble()) * 1000.0).toLong()
        val futureToday = Date(todayTime + leeway)
        this.getExpiresAt()?.after(futureToday) ?: false
    }
}

fun String?.isValidJwt(tag: String? = null, checkExpiration: Boolean = true): Boolean {
    val logsEnabled = BuildConfig.DEBUG
    val extLogTag = "JWT_VALIDATION"
    if (this.isNullOrBlank()) return false.also {
        if (logsEnabled) Reporter.log("Token string is null; debugTag: $tag", extLogTag)
    }
    val jwt = try {
        this.asJWT()
    } catch (e: Exception) {
        if (logsEnabled) {
            Reporter.log("Can not parse JWT from string! debugTag: $tag", extLogTag)
            Log.e(extLogTag, "Error parsing jwt; debugTag: $tag", e)
        }
        null
    } ?: return false
    return jwt.isValidJwt(checkExpiration).also {
        if (logsEnabled) {
            Reporter.log(
                "Given token string is valid: $it; (checkExpiration = $checkExpiration) debugTag: $tag",
                extLogTag
            )
        }
    }
}

fun JWT.getOwnerId() = getClaim("ownerId").asString().let { jwtOwnerId ->
    if (jwtOwnerId.isNullOrBlank())
        getOwnerIds().let { ids ->
            if (ids.isEmpty())
                null
            else
                ids[0]
        }
    else
        jwtOwnerId
}

fun JWT.getOwnerIds(): List<String> =
    getClaim("owners").asObject(com.google.gson.JsonObject::class.java)?.keySet()
        ?.filter { it != null && it != "devkit" } ?: listOf()

fun JWT.getUsername() = getClaim("username").asString()

// endregion

@SuppressLint("MissingPermission")
data class ScanResultInternal(
    val rssi: Int,
    val device: Device,
    val isConnectable: Boolean
) {
    data class Device(
        val name: String?,
        val address: String
    ) : Parcelable {
        constructor(parcel: Parcel) : this(
            parcel.readString() ?: "ScanResultInternal.Device.name.${Math.random().toInt()}",
            parcel.readString() ?: "ScanResultInternal.Device.address.${Math.random().toInt()}"
        ) {
        }

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeString(name)
            parcel.writeString(address)
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : Parcelable.Creator<Device> {
            override fun createFromParcel(parcel: Parcel): Device {
                return Device(parcel)
            }

            override fun newArray(size: Int): Array<Device?> {
                return arrayOfNulls(size)
            }
        }
    }

    data class ScanRecord(
        var serviceData: Map<ParcelUuid, ByteArray>? = null,
        var manufacturerSpecificData: SparseArray<ByteArray>? = null,
        val deviceName: String?
    ) : Parcelable {

        var raw: String? = null

        constructor(parcel: Parcel) : this(
            null,
            null,
            parcel.readString()
        )

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeString(deviceName)
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : Parcelable.Creator<ScanRecord> {
            override fun createFromParcel(parcel: Parcel): ScanRecord {
                return ScanRecord(parcel)
            }

            override fun newArray(size: Int): Array<ScanRecord?> {
                return arrayOfNulls(size)
            }
        }
    }

    var scanRecord: ScanRecord? = null
}

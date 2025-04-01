package com.wiliot.wiliotandroidsdk.utils

import android.content.Context
import android.util.Log
import java.lang.ref.WeakReference

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

fun log(msg: String, where: String) {
    Log.i(where, msg)
}

// endregion

//==============================================================================================
// *** App ***
//==============================================================================================

// region [App]

fun Context.getAppVersion(): Pair<Int, String> {
    val packageInfo = packageManager.getPackageInfo(packageName, 0)
    val versionCode = packageInfo.longVersionCode // API 28+
    return versionCode.toInt() to (packageInfo.versionName ?: "UNKNOWN")
}

// endregion
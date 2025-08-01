package com.wiliot.wiliotqueue.repository

import android.annotation.SuppressLint
import android.content.SharedPreferences
import com.wiliot.wiliotcore.env.EnvironmentWiliot
import com.wiliot.wiliotcore.utils.logTag
import com.wiliot.wiliotqueue.di.tokenPrefs

class TokenStorageSource private constructor(
    private val mSharedPreferences: Lazy<@JvmSuppressWildcards SharedPreferences>
) {

    private val logTag = logTag()

    companion object {
        // sensitive section
        private const val GW_REFRESH_TOKEN_ALIAS = "GW_refreshToken"

        private var INSTANCE : TokenStorageSource? = null

        fun getInstance(): TokenStorageSource {
            if (INSTANCE == null) INSTANCE = TokenStorageSource(tokenPrefs())
            return INSTANCE!!
        }
    }

    @SuppressLint("ApplySharedPref")
    fun saveGwRefreshToken(token: String?, environmentWiliot: EnvironmentWiliot, ownerId: String) {
        mSharedPreferences.value
            .edit()
            .putString(
                environmentWiliot.generateKeyFor(GW_REFRESH_TOKEN_ALIAS, ownerId),
                token
            )
            .commit()
    }

    fun getGwRefreshToken(environmentWiliot: EnvironmentWiliot, ownerId: String): String? {
        return mSharedPreferences.value.getString(
            environmentWiliot.generateKeyFor(GW_REFRESH_TOKEN_ALIAS, ownerId),
            null
        )
    }

    fun clearAllTokens(environmentWiliot: EnvironmentWiliot) {
        mSharedPreferences.value.all.filter {
            it.key.contains("_${environmentWiliot.envName}_")
        }.map {
            it.key
        }.let { keysToRemove ->
            val edit = mSharedPreferences.value.edit()
            keysToRemove.forEach {
                edit.remove(it)
            }
            edit.apply()
        }
    }

    //==============================================================================================
    // *** Utils ***
    //==============================================================================================

    // region [Utils]

    private fun EnvironmentWiliot.generateKeyFor(key: String, ownerId: String) = "${key}_${this.envName}_$ownerId"

    // endregion

}

fun tokenStorageSource(): TokenStorageSource = TokenStorageSource.getInstance()
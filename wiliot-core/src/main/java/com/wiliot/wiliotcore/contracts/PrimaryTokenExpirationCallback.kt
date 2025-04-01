package com.wiliot.wiliotcore.contracts

/**
 * Callback used to notify App that primary token (user token) was expired. SDK uses GW tokens to
 * establish MQTT connection and to access Cloud resources. GW token could only be obtained using
 * primary user token. In case SDK trying to get new GW token and primary token already expired,
 * it triggers [PrimaryTokenExpirationCallback.onPrimaryTokenExpired]. App should refresh primary
 * token. Next time SDK will try to get new GW token it already will have valid primary token.
 */
interface PrimaryTokenExpirationCallback {
    fun onPrimaryTokenExpired()
}
package com.wiliot.wiliotcore

object WiliotTokenKeeper {

    private var mToken: String? = null
    internal val token: String?
        get() = mToken

    /**
     * Set new Primary Access Token. This token will be used by all Wiliot modules.
     * Use it only in case of custom auth implementation.
     * In regular (non-custom) case [Wiliot.WiliotInitializationScope.setApiKey] is enough.
     */
    fun setNewToken(tkn: String?) {
        mToken = tkn
        Wiliot.tokenInjectionConsumers.forEach { it.injectToken(mToken) }
    }

}
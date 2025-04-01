package com.wiliot.wiliotcore.embedded.auth

interface PrimaryTokenInjectionConsumer {
    fun injectToken(token: String?)
}
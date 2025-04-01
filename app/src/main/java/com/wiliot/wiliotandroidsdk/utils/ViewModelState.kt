package com.wiliot.wiliotandroidsdk.utils

import androidx.compose.runtime.MutableState
import kotlinx.coroutines.flow.MutableStateFlow

interface ViewModelState

inline fun <reified T : ViewModelState> MutableStateFlow<T>.upd(block: (T.() -> T)) {
    val oldState = this.value
    this.value = block.invoke(oldState)
}

inline fun <reified T : ViewModelState> MutableState<T>.upd(block: (T.() -> T)) {
    val oldState = this.value
    this.value = block.invoke(oldState)
}
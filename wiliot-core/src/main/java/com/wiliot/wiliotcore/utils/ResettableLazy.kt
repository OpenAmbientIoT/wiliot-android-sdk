package com.wiliot.wiliotcore.utils


class ResettableLazy<T> private constructor(
    private val initializer: () -> T,
    mode: LazyThreadSafetyMode
) : Lazy<T> {

    private val lock = Any()
    @Volatile
    private var _lazy: Lazy<T> = createLazy(mode)

    override val value: T
        get() = _lazy.value

    override fun isInitialized(): Boolean = _lazy.isInitialized()

    fun reset(mode: LazyThreadSafetyMode = LazyThreadSafetyMode.SYNCHRONIZED) {
        synchronized(lock) {
            _lazy = createLazy(mode)
        }
    }

    private fun createLazy(mode: LazyThreadSafetyMode): Lazy<T> {
        return when (mode) {
            LazyThreadSafetyMode.SYNCHRONIZED -> lazy(mode) { initializer() }
            LazyThreadSafetyMode.PUBLICATION -> lazy(mode) { initializer() }
            LazyThreadSafetyMode.NONE -> lazy(mode) { initializer() }
        }
    }

    companion object {
        fun <T> of(
            mode: LazyThreadSafetyMode = LazyThreadSafetyMode.SYNCHRONIZED,
            initializer: () -> T
        ): ResettableLazy<T> = ResettableLazy(initializer, mode)
    }
}

fun <T> resettableLazy(
    mode: LazyThreadSafetyMode = LazyThreadSafetyMode.SYNCHRONIZED,
    initializer: () -> T
): ResettableLazy<T> = ResettableLazy.of(mode, initializer)

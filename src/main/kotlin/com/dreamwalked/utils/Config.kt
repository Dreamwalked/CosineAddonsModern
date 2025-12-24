package com.dreamwalked.utils

object Config {
    @Volatile
    var noJumpDelay: Boolean = false

    @Volatile
    var noPlaceDelay: Boolean = false

    @Volatile
    var noBreakDelay: Boolean = false

    @JvmStatic
    fun isNoJumpDelay(): Boolean = noJumpDelay

    @JvmStatic
    fun isNoPlaceDelay(): Boolean = noPlaceDelay

    @JvmStatic
    fun isNoBreakDelay(): Boolean = noBreakDelay
}

package com.dreamwalked.utils

object Config {
    @Volatile
    var noJumpDelay: Boolean = false

    @Volatile
    var noPlaceDelay: Boolean = false

    @Volatile
    var noBreakDelay: Boolean = false

    @Volatile
    var cosineStatus: Boolean = false

    @Volatile
    var showDistance: Boolean = true

    @Volatile
    var showPosition: Boolean = false

    @Volatile
    var showArmor: Boolean = false

    @Volatile
    var customCape: String = "default"

    @JvmStatic
    fun isNoJumpDelay(): Boolean = noJumpDelay

    @JvmStatic
    fun isNoPlaceDelay(): Boolean = noPlaceDelay

    @JvmStatic
    fun isNoBreakDelay(): Boolean = noBreakDelay

    @JvmStatic
    fun isCosineStatus(): Boolean = cosineStatus
}

package com.dreamwalked.utils

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object ChatUtils {
    const val PREFIX : String = "§a[§bCosineAddons§a]§f "

    fun modMessage(message: String) {
        Minecraft.getInstance().player?.sendSystemMessage(Component.literal(PREFIX + message))
    }
}

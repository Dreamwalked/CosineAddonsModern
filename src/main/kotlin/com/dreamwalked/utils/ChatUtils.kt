package com.dreamwalked.utils

import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text

object ChatUtils {
    const val PREFIX : String = "§a[§bCosineAddons§a]§f "

    fun modMessage(message: String) {
        MinecraftClient.getInstance().player?.sendMessage(Text.literal(PREFIX + message), false)
    }
}

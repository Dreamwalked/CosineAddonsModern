package com.dreamwalked.features

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.item.BlockItem
import com.dreamwalked.mixin.MinecraftClientAccessor
import com.dreamwalked.utils.Config

object NoPlaceDelay {

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (!Config.isNoPlaceDelay()) return@register
            val player = client.player ?: return@register

            val stack = player.mainHandStack
            if (stack.item !is BlockItem) return@register

            val accessor = client as MinecraftClientAccessor
            if (accessor.itemUseCooldown > 1) {
                accessor.itemUseCooldown = 1
            }
        }
    }
}

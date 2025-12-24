package com.dreamwalked.features

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import com.dreamwalked.mixin.LivingEntityAccessor
import com.dreamwalked.utils.Config

object NoJumpDelay {
    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (Config.isNoJumpDelay()) {
                val player = client.player ?: return@register
                (player as LivingEntityAccessor).setJumpingCooldown(0)
            }
        }
    }
}

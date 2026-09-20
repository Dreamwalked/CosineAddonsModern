package com.dreamwalked.features.flipper

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import net.minecraft.util.CommonColors

object FlipperTimer {
    @Volatile
    private var endsAt = 0L

    fun init() {
        if (endsAt <= 0L) start(60.0)
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("cosine-addons-modern", "flip_timer")
        ) { graphics, _ ->
            if (!FlipperConfig.flipTimer) return@addLast
            val now = System.currentTimeMillis()
            if (endsAt <= now) endsAt = now + 60_000L
            val remaining = endsAt - now

            val seconds = remaining / 1_000.0
            graphics.text(
                Minecraft.getInstance().font,
                "§a%.1f".format(seconds),
                FlipperConfig.flipTimerX,
                FlipperConfig.flipTimerY,
                CommonColors.WHITE,
                true
            )
        }
    }

    fun start(seconds: Double = 10.0) {
        endsAt = System.currentTimeMillis() + (seconds * 1_000).toLong()
    }
}

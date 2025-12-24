package com.dreamwalked

import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

object CosineAddonsModern : ModInitializer {
    private val logger = LoggerFactory.getLogger("cosine-addons-modern")

    override fun onInitialize() {
        logger.info("Cosine Addons initialized.")
        // Server-side initialization only. Client commands are registered in the client entrypoint.
    }
}
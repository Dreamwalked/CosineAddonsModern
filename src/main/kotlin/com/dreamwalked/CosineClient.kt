package com.dreamwalked

import com.dreamwalked.features.NoJumpDelay
import com.dreamwalked.features.NoPlaceDelay
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import com.mojang.brigadier.CommandDispatcher
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource

object CosineClient : ClientModInitializer {
    override fun onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher: CommandDispatcher<FabricClientCommandSource>, _ ->
            CosineCommands.register(dispatcher)
        }
        NoJumpDelay.register()
        NoPlaceDelay.init()
    }
}

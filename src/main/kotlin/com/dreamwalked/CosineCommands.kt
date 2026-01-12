package com.dreamwalked

import com.dreamwalked.utils.ChatUtils
import com.dreamwalked.utils.Config
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource

object CosineCommands {
    fun register(dispatcher: CommandDispatcher<FabricClientCommandSource>) {
        fun registerFlag(
            root: LiteralArgumentBuilder<FabricClientCommandSource>,
            name: String,
            getter: () -> Boolean,
            setter: (Boolean) -> Unit
        ) {
            val node = literal(name)
                .then(argument("value", BoolArgumentType.bool()).executes { ctx ->
                    val v = BoolArgumentType.getBool(ctx, "value")
                    setter(v)
                    ChatUtils.modMessage("$name set to $v")
                    1
                })
                .executes {
                    val new = !getter()
                    setter(new)
                    ChatUtils.modMessage("$name toggled to $new")
                    1
                }
            root.then(node)
        }

        val rootCosine = literal("cosineaddons")
        registerFlag(rootCosine, "nojumpdelay", { Config.noJumpDelay }, { Config.noJumpDelay = it })
        registerFlag(rootCosine, "noplacedelay", { Config.noPlaceDelay }, { Config.noPlaceDelay = it })
        registerFlag(rootCosine, "nobreakdelay", { Config.noBreakDelay }, { Config.noBreakDelay = it })
        dispatcher.register(rootCosine)

        val rootCa = literal("ca")
        registerFlag(rootCa, "nojumpdelay", { Config.noJumpDelay }, { Config.noJumpDelay = it })
        registerFlag(rootCa, "noplacedelay", { Config.noPlaceDelay }, { Config.noPlaceDelay = it })
        registerFlag(rootCa, "nobreakdelay", { Config.noBreakDelay }, { Config.noBreakDelay = it })
        dispatcher.register(rootCa)
    }
}

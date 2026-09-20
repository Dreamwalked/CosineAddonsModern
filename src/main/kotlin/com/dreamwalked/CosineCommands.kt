package com.dreamwalked

import com.dreamwalked.utils.ChatUtils
import com.dreamwalked.utils.Config
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource

object CosineCommands {
    fun register(dispatcher: CommandDispatcher<FabricClientCommandSource>) {
        fun registerFlag(
            name: String,
            getter: () -> Boolean,
            setter: (Boolean) -> Unit
        ): LiteralArgumentBuilder<FabricClientCommandSource> {
            return literal(name)
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
        }

        val rootCa = literal("ca")

        // Standard flags
        rootCa.then(registerFlag("nojumpdelay", { Config.noJumpDelay }, { Config.noJumpDelay = it }))
        rootCa.then(registerFlag("noplacedelay", { Config.noPlaceDelay }, { Config.noPlaceDelay = it }))
        rootCa.then(registerFlag("nobreakdelay", { Config.noBreakDelay }, { Config.noBreakDelay = it }))

        val capeNode = literal("cape")
            .executes {
                ChatUtils.modMessage("Selected cape: ${Config.customCape}")
                1
            }

        listOf("default", "off", "evermore", "taylorswift", "wot").forEach { cape ->
            capeNode.then(literal(cape).executes {
                Config.customCape = cape
                ChatUtils.modMessage("Cape set to $cape")
                1
            })
        }
        rootCa.then(capeNode)

        // Status Command with Sub-Arguments
        val statusNode = registerFlag("cosineStatus", { Config.cosineStatus }, { Config.cosineStatus = it })

        // Add sub-values to cosineStatus
        statusNode.then(registerFlag("showDistance", { Config.showDistance }, { Config.showDistance = it }))
        statusNode.then(registerFlag("showPosition", { Config.showPosition }, { Config.showPosition = it }))
        statusNode.then(registerFlag("showArmor", { Config.showArmor }, { Config.showArmor = it }))

        rootCa.then(statusNode)

        val caNode = dispatcher.register(rootCa)
        dispatcher.register(literal("cosineaddons").redirect(caNode))
    }
}

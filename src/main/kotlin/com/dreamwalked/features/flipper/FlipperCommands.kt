package com.dreamwalked.features.flipper

import com.dreamwalked.utils.ChatUtils
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource

object FlipperCommands {
    fun register(dispatcher: CommandDispatcher<FabricClientCommandSource>) {
        val root = literal("cf")
            .executes { FlipperService.status(); 1 }
            .then(literal("help").executes {
                ChatUtils.modMessage("/cf status|config|ping|connect|reconnect|disconnect")
                ChatUtils.modMessage("/cf websocket <ws:// or wss:// URL>")
                ChatUtils.modMessage("/cf autoopen|autobuy|bed|safety|presniper|relist|fliptimer [true|false]")
                ChatUtils.modMessage("/cf minprofit1|minprofit2 <coins> <percent>")
                ChatUtils.modMessage("/cf maxautoopencost <coins>; /cf delay|bedspamdelay <ms>")
                ChatUtils.modMessage("/cf timerx|timery <pixels>")
                ChatUtils.modMessage("/cf flipsound <pling|levelup|orb|chime|allay|off>")
                1
            })
            .then(literal("status").executes { FlipperService.status(); 1 })
            .then(literal("config").executes { FlipperService.listConfig(); 1 })
            .then(literal("ping").executes { FlipperService.ping(); 1 })
            .then(websocketSetting("websocket"))
            .then(websocketSetting("websocketurl"))
            .then(literal("flipsound")
                .executes {
                    ChatUtils.modMessage("Flip sound is ${FlipperConfig.flipSound}. Options: pling, levelup, orb, chime, allay, off")
                    1
                }
                .then(argument("sound", StringArgumentType.word()).executes { context ->
                    FlipperService.setFlipSound(StringArgumentType.getString(context, "sound"))
                    1
                }))
            .then(literal("connect").executes { FlipperService.connect(); 1 })
            .then(literal("reconnect").executes { FlipperService.reconnect(); 1 })
            .then(literal("disconnect").executes { FlipperService.disconnect(); 1 })
            .then(booleanSetting("autoopen", FlipperConfig.autoOpen, FlipperService::setAutoOpen))
            .then(booleanSetting("autobuy", FlipperConfig.autoBuy, FlipperService::setAutoBuy))
            .then(booleanSetting("bed", FlipperConfig.bedTiming, FlipperService::setBedTiming))
            .then(booleanSetting("safety", FlipperConfig.safety, FlipperService::setSafety))
            .then(booleanSetting("presniper", FlipperConfig.preSniper, FlipperService::setPreSniper))
            .then(booleanSetting("relist", FlipperConfig.relistPricing, FlipperService::setRelistPricing))
            .then(booleanSetting("fliptimer", FlipperConfig.flipTimer) { value ->
                FlipperConfig.flipTimer = value
                FlipperConfig.save()
                ChatUtils.modMessage("Flip timer ${if (value) "§aenabled" else "§cdisabled"}")
            })
            .then(literal("delay").then(argument("milliseconds", LongArgumentType.longArg(0, 5_000)).executes { context ->
                FlipperConfig.buyDelayMs = LongArgumentType.getLong(context, "milliseconds")
                FlipperConfig.save()
                ChatUtils.modMessage("Buy delay set to ${FlipperConfig.buyDelayMs}ms")
                1
            }))
            .then(literal("bedspamdelay")
                .executes {
                    ChatUtils.modMessage("Bed spam delay is ${FlipperConfig.bedSpamDelayMs}ms")
                    1
                }
                .then(argument("milliseconds", LongArgumentType.longArg(1, 5_000)).executes { context ->
                    FlipperConfig.bedSpamDelayMs = LongArgumentType.getLong(context, "milliseconds")
                    FlipperConfig.save()
                    ChatUtils.modMessage("Bed spam delay set to ${FlipperConfig.bedSpamDelayMs}ms")
                    1
                }))
            .then(positionSetting("timerx", true))
            .then(positionSetting("timery", false))
            .then(profitSetting("minprofit1", true))
            .then(profitSetting("minprofit2", false))
            .then(literal("maxautoopencost")
                .executes {
                    val value = FlipperConfig.maxAutoOpenCost
                    val display = if (value == Double.MAX_VALUE) "unlimited" else "${FlipperUtils.formatNumber(value)} coins"
                    ChatUtils.modMessage("Maximum auto-open cost: $display")
                    1
                }
                .then(argument("coins", DoubleArgumentType.doubleArg(0.0)).executes { context ->
                    FlipperConfig.maxAutoOpenCost = DoubleArgumentType.getDouble(context, "coins")
                    FlipperConfig.save()
                    ChatUtils.modMessage(
                        "Maximum auto-open cost set to ${FlipperUtils.formatNumber(FlipperConfig.maxAutoOpenCost)} coins"
                    )
                    1
                })
                .then(literal("unlimited").executes {
                    FlipperConfig.maxAutoOpenCost = Double.MAX_VALUE
                    FlipperConfig.save()
                    ChatUtils.modMessage("Maximum auto-open cost set to unlimited")
                    1
                }))
            .then(literal("send").then(argument("message", StringArgumentType.greedyString()).executes { context ->
                FlipperService.send(StringArgumentType.getString(context, "message"))
                1
            }))
            .then(argument("message", StringArgumentType.greedyString()).executes { context ->
                FlipperService.send(StringArgumentType.getString(context, "message"))
                1
            })

        val node = dispatcher.register(root)
        dispatcher.register(literal("cosineflipper").redirect(node))
    }

    private fun booleanSetting(name: String, initial: Boolean, setter: (Boolean) -> Unit) =
        literal(name)
            .executes { setter(!currentValue(name, initial)); 1 }
            .then(argument("value", BoolArgumentType.bool()).executes { context ->
                setter(BoolArgumentType.getBool(context, "value"))
                1
            })

    private fun currentValue(name: String, fallback: Boolean): Boolean = when (name) {
        "autoopen" -> FlipperConfig.autoOpen
        "autobuy" -> FlipperConfig.autoBuy
        "bed" -> FlipperConfig.bedTiming
        "safety" -> FlipperConfig.safety
        "presniper" -> FlipperConfig.preSniper
        "relist" -> FlipperConfig.relistPricing
        "fliptimer" -> FlipperConfig.flipTimer
        else -> fallback
    }

    private fun positionSetting(name: String, xAxis: Boolean) = literal(name)
        .executes {
            val value = if (xAxis) FlipperConfig.flipTimerX else FlipperConfig.flipTimerY
            ChatUtils.modMessage("$name is $value")
            1
        }
        .then(argument("pixels", LongArgumentType.longArg(0, 100_000)).executes { context ->
            val value = LongArgumentType.getLong(context, "pixels").toInt()
            if (xAxis) FlipperConfig.flipTimerX = value else FlipperConfig.flipTimerY = value
            FlipperConfig.save()
            ChatUtils.modMessage("$name set to $value")
            1
        })

    private fun websocketSetting(name: String) = literal(name)
        .executes {
            ChatUtils.modMessage("Websocket URL: ${FlipperConfig.websocketUrl}")
            1
        }
        .then(argument("url", StringArgumentType.string()).executes { context ->
            FlipperService.setWebsocketUrl(StringArgumentType.getString(context, "url"))
            1
        })

    private fun profitSetting(name: String, first: Boolean) = literal(name)
        .then(argument("coins", DoubleArgumentType.doubleArg(0.0))
            .then(argument("percent", DoubleArgumentType.doubleArg(0.0)).executes { context ->
                val coins = DoubleArgumentType.getDouble(context, "coins")
                val percent = DoubleArgumentType.getDouble(context, "percent")
                if (first) {
                    FlipperConfig.minProfit1 = coins
                    FlipperConfig.minProfitPercent1 = percent
                } else {
                    FlipperConfig.minProfit2 = coins
                    FlipperConfig.minProfitPercent2 = percent
                }
                FlipperConfig.save()
                ChatUtils.modMessage("$name set to ${FlipperUtils.formatNumber(coins)} coins at $percent%")
                1
            }))
}

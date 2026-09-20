package com.dreamwalked.features.flipper

import com.dreamwalked.utils.ChatUtils
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.BedBlock
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicBoolean

object FlipperService {
    private val httpClient = HttpClient.newBuilder().build()
    private val connecting = AtomicBoolean(false)
    private val bedSpamActive = AtomicBoolean(false)
    private val seenAuctions = LinkedHashSet<String>()
    private var socket: WebSocket? = null
    private var shouldReconnect = false
    private var pendingFlip: Flip? = null
    private var pendingSince = 0L
    private var lastContainerId = -1
    private var lastClickAt = 0L
    private var inspectedContainerId = -1
    private var pricedContainerId = -1
    private var lastPriceTarget: Double? = null
    private var activeScreen: AbstractContainerScreen<*>? = null
    private var pingStartedAt = 0L

    val connected: Boolean
        get() = socket != null

    fun init() {
        FlipperConfig.load()
        FlipperTimer.init()
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen is AbstractContainerScreen<*>) {
                activeScreen = screen
                ScreenEvents.remove(screen).register {
                    if (activeScreen === screen) {
                        activeScreen = null
                        bedSpamActive.set(false)
                    }
                }
            }
        }
        if (FlipperConfig.autoConnect) connect()
    }

    fun connect() {
        if (socket != null || !connecting.compareAndSet(false, true)) return
        shouldReconnect = true
        runCatching { URI.create(FlipperConfig.websocketUrl) }
            .onFailure {
                connecting.set(false)
                ChatUtils.modMessage("§cInvalid flipper websocket URL.")
            }
            .onSuccess { uri ->
                httpClient.newWebSocketBuilder().buildAsync(uri, Listener()).whenComplete { _, error ->
                    if (error != null) {
                        connecting.set(false)
                        Minecraft.getInstance().execute {
                            ChatUtils.modMessage("§cFlipper connection failed: ${error.message ?: error.javaClass.simpleName}")
                        }
                        scheduleReconnect()
                    }
                }
            }
    }

    fun disconnect() {
        shouldReconnect = false
        connecting.set(false)
        socket?.sendClose(WebSocket.NORMAL_CLOSURE, "Client disconnect")
        socket = null
        ChatUtils.modMessage("Flipper disconnected.")
    }

    fun reconnect() {
        shouldReconnect = true
        socket?.abort()
        socket = null
        connecting.set(false)
        connect()
    }

    fun send(message: String) {
        val playerName = Minecraft.getInstance().player?.name?.string ?: "unknown"
        val current = socket
        if (current == null) {
            ChatUtils.modMessage("§cFlipper is not connected.")
            return
        }
        current.sendText("$playerName: $message", true)
    }

    fun ping() {
        if (!connected) {
            ChatUtils.modMessage("§cFlipper is not connected.")
            return
        }
        pingStartedAt = System.currentTimeMillis()
        send("ping")
    }

    fun setWebsocketUrl(value: String) {
        val uri = runCatching { URI.create(value) }.getOrNull()
        if (uri == null || uri.scheme !in setOf("ws", "wss") || uri.host.isNullOrBlank()) {
            ChatUtils.modMessage("§cWebsocket URL must be a valid ws:// or wss:// address.")
            return
        }
        FlipperConfig.websocketUrl = uri.toString()
        FlipperConfig.save()
        ChatUtils.modMessage("Websocket URL set to §e${FlipperConfig.websocketUrl}§f. Reconnecting…")
        reconnect()
    }

    fun status() {
        ChatUtils.modMessage("Flipper: ${if (connected) "§aconnected" else "§cdisconnected"}§f, " +
            "auto-open ${onOff(FlipperConfig.autoOpen)}, auto-buy ${onOff(FlipperConfig.autoBuy)}")
        ChatUtils.modMessage("Filters: ${FlipperUtils.formatNumber(FlipperConfig.minProfit1)} @ ${FlipperConfig.minProfitPercent1}% " +
            "or ${FlipperUtils.formatNumber(FlipperConfig.minProfit2)} @ ${FlipperConfig.minProfitPercent2}%")
        val maxCost = if (FlipperConfig.maxAutoOpenCost == Double.MAX_VALUE) {
            "unlimited"
        } else {
            "${FlipperUtils.formatNumber(FlipperConfig.maxAutoOpenCost)} coins"
        }
        ChatUtils.modMessage("Maximum auto-open cost: §e$maxCost")
    }

    fun listConfig() {
        ChatUtils.modMessage("§bFlipper configuration:")
        ChatUtils.modMessage("autoConnect=${FlipperConfig.autoConnect}, autoOpen=${FlipperConfig.autoOpen}, autoBuy=${FlipperConfig.autoBuy}")
        ChatUtils.modMessage("bedTiming=${FlipperConfig.bedTiming}, bedSpamDelay=${FlipperConfig.bedSpamDelayMs}ms")
        ChatUtils.modMessage("flipTimer=${FlipperConfig.flipTimer}, timerPosition=${FlipperConfig.flipTimerX},${FlipperConfig.flipTimerY}")
        ChatUtils.modMessage("safety=${FlipperConfig.safety}, preSniper=${FlipperConfig.preSniper}, relistPricing=${FlipperConfig.relistPricing}")
        ChatUtils.modMessage("minProfit1=${FlipperUtils.formatNumber(FlipperConfig.minProfit1)} @ ${FlipperConfig.minProfitPercent1}%")
        ChatUtils.modMessage("minProfit2=${FlipperUtils.formatNumber(FlipperConfig.minProfit2)} @ ${FlipperConfig.minProfitPercent2}%")
        val maxCost = if (FlipperConfig.maxAutoOpenCost == Double.MAX_VALUE) {
            "unlimited"
        } else {
            FlipperUtils.formatNumber(FlipperConfig.maxAutoOpenCost)
        }
        ChatUtils.modMessage("maxAutoOpenCost=$maxCost, buyDelay=${FlipperConfig.buyDelayMs}ms")
        ChatUtils.modMessage("websocketUrl=${FlipperConfig.websocketUrl}")
    }

    fun setAutoOpen(value: Boolean) {
        FlipperConfig.autoOpen = value
        FlipperConfig.save()
        ChatUtils.modMessage("Auto-open ${onOff(value)}")
    }

    fun setAutoBuy(value: Boolean) {
        FlipperConfig.autoBuy = value
        FlipperConfig.save()
        ChatUtils.modMessage("Auto-buy ${onOff(value)}")
    }

    fun setBedTiming(value: Boolean) {
        FlipperConfig.bedTiming = value
        FlipperConfig.save()
        ChatUtils.modMessage("Bed timing ${onOff(value)}")
    }

    fun setSafety(value: Boolean) {
        FlipperConfig.safety = value
        FlipperConfig.save()
        ChatUtils.modMessage("Auto-buy safety ${onOff(value)}")
    }

    fun setPreSniper(value: Boolean) {
        FlipperConfig.preSniper = value
        FlipperConfig.save()
        ChatUtils.modMessage("Pre-sniper info ${onOff(value)}")
    }

    fun setRelistPricing(value: Boolean) {
        FlipperConfig.relistPricing = value
        FlipperConfig.save()
        ChatUtils.modMessage("Relist pricing ${onOff(value)}")
    }

    private fun onOff(value: Boolean) = if (value) "§aenabled§f" else "§cdisabled§f"

    private fun processMessage(raw: String) {
        if (raw.startsWith("{\"flips\"")) {
            val root = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull() ?: return
            val flips = root.getAsJsonArray("flips") ?: return
            flips.mapNotNull { element -> parseFlip(element.asJsonObject) }
                .sortedByDescending(Flip::target)
                .forEach(::handleFlip)
            return
        }

        val message = raw.replace(Regex("^.+?: "), "")
        if (message == "Flips in 10 seconds!") {
            FlipperTimer.start(10.0)
        }
        if (message.startsWith("priceditem")) {
            val json = message.removePrefix("priceditem").trim()
            lastPriceTarget = runCatching {
                JsonParser.parseString(json).asJsonObject.get("target")?.asDouble
            }.getOrNull()
            return
        }
        if (message.contains("server ping", ignoreCase = true)) {
            val startedAt = pingStartedAt
            if (startedAt > 0L) {
                ChatUtils.modMessage("Ping is §e${System.currentTimeMillis() - startedAt}ms")
                pingStartedAt = 0L
            } else {
                ChatUtils.modMessage(message)
            }
        } else if (message.isNotBlank() && listOf("checkplayer", "priceitem", "sendbought", "sellsend").none(message::contains)) {
            ChatUtils.modMessage(message.replace('&', '§'))
        }
    }

    private fun parseFlip(json: JsonObject): Flip? = runCatching {
        Flip(
            id = json.get("id").asString,
            itemName = json.get("itemName").asString,
            rarity = json.get("rarity")?.asString ?: "common",
            startingBid = json.get("startingBid").asDouble,
            target = json.get("target").asDouble,
            purchaseAt = json.get("purchaseAt")?.asLong ?: 0L,
            additionalValue = json.get("additionalValue")?.asDouble ?: 0.0,
            key = json.get("key")?.toString() ?: ""
        )
    }.getOrNull()

    private fun handleFlip(flip: Flip) {
        if (flip.id in seenAuctions || FlipperUtils.isBlacklisted(flip)) return
        rememberAuction(flip.id)
        if (!FlipperUtils.qualifies(flip.profit, flip.profitPercent)) return

        val client = Minecraft.getInstance()
        val bedSeconds = ((flip.purchaseAt - System.currentTimeMillis()) / 1000.0).coerceAtLeast(0.0)
        val message = Component.literal("CosineAddons: ")
            .append(Component.literal(flip.itemName).withStyle(FlipperUtils.rarityColor(flip.rarity)))
            .append(Component.literal(" §b${FlipperUtils.formatNumber(flip.startingBid)} §f→ §b${FlipperUtils.formatNumber(flip.target)}"))
            .append(Component.literal(" §7(${FlipperUtils.formatNumber(flip.profit)} profit, ${"%.1f".format(flip.profitPercent)}%, bed ${"%.1f".format(bedSeconds)}s)"))
            .withStyle { style ->
                style.withClickEvent(ClickEvent.RunCommand("/viewauction ${flip.id}"))
                    .withHoverEvent(HoverEvent.ShowText(Component.literal("Open this auction")))
            }
        client.player?.sendSystemMessage(message)

        if (FlipperConfig.autoOpen && pendingFlip == null && flip.startingBid <= FlipperConfig.maxAutoOpenCost) {
            pendingFlip = flip
            pendingSince = System.currentTimeMillis()
            client.player?.connection?.sendCommand("viewauction ${flip.id}")
        } else if (FlipperConfig.autoOpen && flip.startingBid > FlipperConfig.maxAutoOpenCost) {
            ChatUtils.modMessage(
                "§eSkipped auto-opening ${flip.itemName}: ${FlipperUtils.formatNumber(flip.startingBid)} exceeds " +
                    "the ${FlipperUtils.formatNumber(FlipperConfig.maxAutoOpenCost)} coin limit."
            )
        }
    }

    private fun rememberAuction(id: String) {
        seenAuctions += id
        if (seenAuctions.size > 512) seenAuctions.remove(seenAuctions.first())
    }

    private fun tick(client: Minecraft) {
        if (pendingFlip != null && System.currentTimeMillis() - pendingSince > 10_000) {
            pendingFlip = null
        }
        val screen = activeScreen ?: run {
            lastContainerId = -1
            inspectedContainerId = -1
            return
        }
        val title = screen.title.string
        val menu = screen.menu

        if (FlipperConfig.safety && FlipperConfig.autoBuy &&
            (title.contains("Auctions Browser") || title.startsWith("Auctions: "))) {
            setAutoBuy(false)
            ChatUtils.modMessage("§eAuto-buy was disabled by safety because the auction browser opened.")
        }

        if (title == "BIN Auction View") {
            inspectAuction(menu.containerId, menu.slots.getOrNull(13)?.item)
        }
        handleRelistPricing(title, menu.containerId, menu.slots.getOrNull(13)?.item)

        if (!FlipperConfig.autoBuy || System.currentTimeMillis() - lastClickAt < 125) return
        when (title) {
            "BIN Auction View" -> {
                val buyButton = menu.slots.getOrNull(31)?.item ?: return
                if (buyButton.isEmpty) return
                if (isBed(buyButton) && bedSpamActive.compareAndSet(false, true)) {
                    startBedSpam(client, screen, menu.containerId)
                } else if (buyButton.`is`(Items.GOLD_NUGGET) && lastContainerId != menu.containerId) {
                    clickSlot(client, menu.containerId, 31)
                    lastContainerId = menu.containerId
                }
            }
            "Confirm Purchase" -> if (lastContainerId != menu.containerId) {
                clickSlot(client, menu.containerId, 11)
                lastContainerId = menu.containerId
                pendingFlip = null
            }
        }
    }

    private fun clickSlot(client: Minecraft, containerId: Int, slot: Int) {
        val player = client.player ?: return
        client.gameMode?.handleContainerInput(containerId, slot, 2, ContainerInput.CLONE, player)
        lastClickAt = System.currentTimeMillis()
    }

    private fun isBed(stack: net.minecraft.world.item.ItemStack): Boolean {
        val item = stack.item
        return item is BlockItem && item.block is BedBlock
    }

    private fun startBedSpam(client: Minecraft, screen: AbstractContainerScreen<*>, containerId: Int) {
        Thread.ofVirtual().name("cosine-bed-spam").start {
            while (bedSpamActive.get()) {
                client.execute {
                    val current = activeScreen
                    val stillValid = FlipperConfig.autoBuy && current === screen &&
                        current.title.string == "BIN Auction View" && current.menu.containerId == containerId &&
                        current.menu.slots.getOrNull(31)?.item?.let(::isBed) == true
                    if (stillValid) {
                        clickSlot(client, containerId, 31)
                    } else {
                        bedSpamActive.set(false)
                    }
                }
                Thread.sleep(FlipperConfig.bedSpamDelayMs.coerceAtLeast(1))
            }
        }
    }

    private fun inspectAuction(containerId: Int, item: net.minecraft.world.item.ItemStack?) {
        if (!FlipperConfig.preSniper || inspectedContainerId == containerId || item == null || item.isEmpty) return
        inspectedContainerId = containerId
        val lore = itemLore(item).joinToString(" ")
        fun find(label: String): String? = Regex("$label: (?:\\[[^]]+] )?(\\w+)").find(lore)?.groupValues?.get(1)
        find("Seller")?.let { sendAuctionParty("§2Seller's AH: §a$it", it) }
        find("Buyer")?.let { sendAuctionParty("§eAuction bought by $it", it) }
    }

    private fun sendAuctionParty(label: String, player: String) {
        Minecraft.getInstance().player?.sendSystemMessage(
            Component.literal(label).withStyle { style ->
                style.withClickEvent(ClickEvent.RunCommand("/ah $player"))
                    .withHoverEvent(HoverEvent.ShowText(Component.literal("Open $player's auctions")))
            }
        )
    }

    private fun handleRelistPricing(title: String, containerId: Int, item: net.minecraft.world.item.ItemStack?) {
        if (!FlipperConfig.relistPricing || item == null || item.isEmpty ||
            (title != "Create BIN Auction" && !title.endsWith("Auction View"))) return

        if (pricedContainerId != containerId) {
            pricedContainerId = containerId
            lastPriceTarget = null
            val lore = itemLore(item).joinToString(", ")
            send("priceitem ${item.hoverName.string}|||||[$lore]")
        }
        val target = lastPriceTarget ?: return
        val oldLore = item.get(DataComponents.LORE) ?: return
        if (oldLore.lines().none { it.string.startsWith("Estimated Value:") }) {
            item.set(
                DataComponents.LORE,
                oldLore.withLineAdded(
                    Component.literal("Estimated Value: ").withStyle(ChatFormatting.AQUA)
                        .append(Component.literal(FlipperUtils.formatNumber(target)).withStyle(ChatFormatting.GREEN))
                )
            )
        }
    }

    private fun itemLore(item: net.minecraft.world.item.ItemStack): List<String> =
        item.get(DataComponents.LORE)?.lines()?.map { it.string } ?: emptyList()

    private fun scheduleReconnect() {
        if (!shouldReconnect) return
        Thread.ofVirtual().start {
            Thread.sleep(2_000)
            if (shouldReconnect && socket == null) connect()
        }
    }

    private class Listener : WebSocket.Listener {
        private val text = StringBuilder()

        override fun onOpen(webSocket: WebSocket) {
            socket = webSocket
            connecting.set(false)
            webSocket.request(1)
            Minecraft.getInstance().execute {
                ChatUtils.modMessage("§aFlipper connected.§f Auto-open ${onOff(FlipperConfig.autoOpen)}, auto-buy ${onOff(FlipperConfig.autoBuy)}")
            }
        }

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            text.append(data)
            if (last) {
                val complete = text.toString()
                text.setLength(0)
                Minecraft.getInstance().execute { processMessage(complete) }
            }
            webSocket.request(1)
            return null
        }

        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
            socket = null
            connecting.set(false)
            if (shouldReconnect) Minecraft.getInstance().execute { ChatUtils.modMessage("§eFlipper disconnected; reconnecting…") }
            scheduleReconnect()
            return null
        }

        override fun onError(webSocket: WebSocket, error: Throwable) {
            socket = null
            connecting.set(false)
            Minecraft.getInstance().execute { ChatUtils.modMessage("§cFlipper error: ${error.message ?: error.javaClass.simpleName}") }
            scheduleReconnect()
        }
    }
}

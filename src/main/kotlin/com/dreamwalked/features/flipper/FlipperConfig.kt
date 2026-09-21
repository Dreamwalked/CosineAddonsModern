package com.dreamwalked.features.flipper

import net.minecraft.client.Minecraft
import java.nio.file.Files
import java.util.Properties

object FlipperConfig {
    var autoConnect = true
    var autoOpen = false
    var autoBuy = false
    var bedTiming = false
    var safety = true
    var preSniper = true
    var relistPricing = true
    var minProfit1 = 7_000_000.0
    var minProfitPercent1 = 1.0
    var minProfit2 = 5_000_000.0
    var minProfitPercent2 = 50.0
    var maxAutoOpenCost = Double.MAX_VALUE
    var bedSpamDelayMs = 100L
    var flipTimer = true
    var flipTimerX = 75
    var flipTimerY = 175
    var flipSound = "pling"
    var websocketUrl = "ws://localhost:8080"

    private val path
        get() = Minecraft.getInstance().gameDirectory.toPath()
            .resolve("config/cosine-addons-modern-flipper.properties")

    fun load() {
        val properties = Properties()
        if (Files.exists(path)) {
            runCatching { Files.newInputStream(path).use(properties::load) }
        }
        autoConnect = properties.getProperty("autoConnect")?.toBooleanStrictOrNull() ?: autoConnect
        autoOpen = properties.getProperty("autoOpen")?.toBooleanStrictOrNull() ?: autoOpen
        autoBuy = properties.getProperty("autoBuy")?.toBooleanStrictOrNull() ?: autoBuy
        bedTiming = properties.getProperty("bedTiming")?.toBooleanStrictOrNull() ?: bedTiming
        safety = properties.getProperty("safety")?.toBooleanStrictOrNull() ?: safety
        preSniper = properties.getProperty("preSniper")?.toBooleanStrictOrNull() ?: preSniper
        relistPricing = properties.getProperty("relistPricing")?.toBooleanStrictOrNull() ?: relistPricing
        minProfit1 = properties.getProperty("minProfit1")?.toDoubleOrNull() ?: minProfit1
        minProfitPercent1 = properties.getProperty("minProfitPercent1")?.toDoubleOrNull() ?: minProfitPercent1
        minProfit2 = properties.getProperty("minProfit2")?.toDoubleOrNull() ?: minProfit2
        minProfitPercent2 = properties.getProperty("minProfitPercent2")?.toDoubleOrNull() ?: minProfitPercent2
        maxAutoOpenCost = properties.getProperty("maxAutoOpenCost")?.toDoubleOrNull()?.coerceAtLeast(0.0)
            ?: maxAutoOpenCost
        bedSpamDelayMs = properties.getProperty("bedSpamDelayMs")?.toLongOrNull()?.coerceIn(1, 5_000)
            ?: bedSpamDelayMs
        flipTimer = properties.getProperty("flipTimer")?.toBooleanStrictOrNull() ?: flipTimer
        flipTimerX = properties.getProperty("flipTimerX")?.toIntOrNull()?.coerceAtLeast(0) ?: flipTimerX
        flipTimerY = properties.getProperty("flipTimerY")?.toIntOrNull()?.coerceAtLeast(0) ?: flipTimerY
        flipSound = properties.getProperty("flipSound")?.lowercase() ?: flipSound
        websocketUrl = properties.getProperty("websocketUrl")?.takeIf { it.startsWith("ws://") || it.startsWith("wss://") }
            ?: websocketUrl
    }

    fun save() {
        val properties = Properties().apply {
            setProperty("autoConnect", autoConnect.toString())
            setProperty("autoOpen", autoOpen.toString())
            setProperty("autoBuy", autoBuy.toString())
            setProperty("bedTiming", bedTiming.toString())
            setProperty("safety", safety.toString())
            setProperty("preSniper", preSniper.toString())
            setProperty("relistPricing", relistPricing.toString())
            setProperty("minProfit1", minProfit1.toString())
            setProperty("minProfitPercent1", minProfitPercent1.toString())
            setProperty("minProfit2", minProfit2.toString())
            setProperty("minProfitPercent2", minProfitPercent2.toString())
            setProperty("maxAutoOpenCost", maxAutoOpenCost.toString())
            setProperty("bedSpamDelayMs", bedSpamDelayMs.toString())
            setProperty("flipTimer", flipTimer.toString())
            setProperty("flipTimerX", flipTimerX.toString())
            setProperty("flipTimerY", flipTimerY.toString())
            setProperty("flipSound", flipSound)
            setProperty("websocketUrl", websocketUrl)
        }
        runCatching {
            Files.createDirectories(path.parent)
            Files.newOutputStream(path).use { properties.store(it, "Cosine Addons Modern flipper settings") }
        }
    }
}

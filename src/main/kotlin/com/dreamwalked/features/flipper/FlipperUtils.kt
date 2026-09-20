package com.dreamwalked.features.flipper

import net.minecraft.ChatFormatting
import java.text.NumberFormat
import java.util.Locale

object FlipperUtils {
    private const val MILLION = 1_000_000.0
    private val formatter = NumberFormat.getIntegerInstance(Locale.US)

    fun formatNumber(number: Number): String = formatter.format(number.toLong())

    fun rarityColor(rarity: String): ChatFormatting = when (rarity.lowercase()) {
        "uncommon" -> ChatFormatting.GREEN
        "rare" -> ChatFormatting.BLUE
        "epic" -> ChatFormatting.DARK_PURPLE
        "legendary" -> ChatFormatting.GOLD
        "mythic" -> ChatFormatting.LIGHT_PURPLE
        "supreme" -> ChatFormatting.DARK_RED
        "special", "very_special" -> ChatFormatting.RED
        "divine" -> ChatFormatting.AQUA
        else -> ChatFormatting.WHITE
    }

    fun qualifies(profit: Double, percent: Double): Boolean =
        (profit >= FlipperConfig.minProfit1 && percent >= FlipperConfig.minProfitPercent1) ||
            (profit >= FlipperConfig.minProfit2 && percent >= FlipperConfig.minProfitPercent2) ||
            profit >= 10 * MILLION

    fun isBlacklisted(flip: Flip): Boolean {
        val item = flip.itemName
        val price = flip.startingBid
        val key = flip.key.lowercase()
        val basePrice = price - flip.additionalValue
        val rarity = flip.rarity.lowercase()
        val attributes = listOf("Necklace", "Belt", "Gauntlet", "Glove", "Cloak", "Bracelet", "Terror", "Crimson", "Fervor", "Aurora")

        return when {
            item.contains("Rabbit") && price > 100 * MILLION -> true
            item.contains("Hegemony") && price > 1_700 * MILLION -> true
            item.contains("Magic 8 Ball") && price > 410 * MILLION -> true
            item.contains("Subzero Wisp") && price > 390 * MILLION -> true
            item.contains("Ender Artifact") && price > 310 * MILLION -> true
            item.contains("Ender Dragon") && rarity == "epic" && price > 420 * MILLION -> true
            item.contains("Midas Staff") && !item.contains("⚚") && price > 260 * MILLION -> true
            item.contains("Brick Red Dye") && price > 350 * MILLION -> true
            item.contains("Midas' Sword") && !item.contains("⚚") && price > 130 * MILLION -> true
            item.contains("Soul Whip") && price > 30 * MILLION -> true
            item.contains("Four-Eyed Fish") && price > 70 * MILLION -> true
            item.contains("Wither Relic") && price > 120 * MILLION -> true
            item.contains("Nether Artifact") && price > 75 * MILLION -> true
            item.contains("Relic of Coins") && price > 330 * MILLION -> true
            item.contains("Pulse Ring") && key.contains("recombed=true&rarity=legendary") && price > 64 * MILLION -> true
            item.contains("Dye") && price > 5 * MILLION -> true
            item.contains("test", true) && (item.contains("Skin") || item.contains("Rune")) -> true
            item.contains("Elephant") && basePrice > 30 * MILLION -> true
            item.contains("Bat") || item.contains("Rat") || item.contains("Rock") || item.contains("Owl") -> true
            item.contains("Flower of Truth") && basePrice > 10 * MILLION -> true
            item.contains("Giant's Sword") && basePrice > 225 * MILLION -> true
            item.contains("Baby Yeti") && basePrice > 50 * MILLION -> true
            item.contains("Livid Dagger") && basePrice > 25 * MILLION -> true
            item.contains("Black Cat") && basePrice > 90 * MILLION && !item.contains("100") -> true
            item.contains("Armadillo") && !key.contains("quick") && basePrice > 7 * MILLION -> true
            item.contains("Pulse Ring") && basePrice > 60 * MILLION && rarity != "mythic" -> true
            item.contains("Overflux Capacitor") && basePrice > 60 * MILLION -> true
            item.contains("Daedalus Axe") && basePrice > 20 * MILLION -> true
            item.contains("Reaper Falchion") && basePrice > 10 * MILLION -> true
            item.contains("Cosmic Blue Whale") && price > 50 * MILLION -> true
            item.contains("Great Shark Magma Lord Skin") && price > 65 * MILLION -> true
            item.contains("Ocelot") && !item.contains("100") && price > 15 * MILLION -> true
            item.contains("Blaze") && rarity == "epic" && basePrice > 25 * MILLION -> true
            item.contains("White Wooly") || item.contains("Power Scroll") -> true
            item.contains("Skin") && flip.profit < 100 * MILLION -> true
            attributes.any(item::contains) && item.contains("✪") && price > 5 * MILLION -> true
            else -> false
        }
    }
}

data class Flip(
    val id: String,
    val itemName: String,
    val rarity: String,
    val startingBid: Double,
    val target: Double,
    val purchaseAt: Long,
    val additionalValue: Double,
    val key: String
) {
    val profit: Double get() = target - startingBid - target * 0.035
    val profitPercent: Double get() = if (startingBid <= 0) 0.0 else profit / startingBid * 100.0
}

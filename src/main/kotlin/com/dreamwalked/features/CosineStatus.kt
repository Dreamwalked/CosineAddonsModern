package com.dreamwalked.features

import com.dreamwalked.utils.Config
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.resources.Identifier
import net.minecraft.util.CommonColors
import net.minecraft.world.phys.AABB
import kotlin.math.roundToInt

@Environment(EnvType.CLIENT)
object CosineStatus {
    private const val RIGHT_PADDING = 8
    private const val TOP_PADDING = 7
    private const val LINE_SPACING = 10

    private var targetBox: AABB? = null

    @Volatile
    private var activeLines: List<String> = emptyList()

    fun init() {
        // Logic Tick
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (client.level != null) tick(client)
        }

        // HUD elements now extract their draw commands into the 26.2 GUI render state.
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("cosine-addons-modern", "hud")) { graphics, _ ->
            if (!Config.cosineStatus) return@addLast
            renderHud(graphics)
        }

        // Gizmos must be submitted during the level extraction phase in 26.2.
        LevelExtractionEvents.END_EXTRACTION.register { context ->
            if (Config.cosineStatus) {
                renderESP(context)
            }
        }
    }

    private fun tick(client: Minecraft) {
        val level = client.level ?: return
        val localPlayer = client.player ?: return
        val target = level.players().find { it.name.string == "xFormulaCosine" }

        val newLines = mutableListOf<String>()

        if (target != null) {
            targetBox = target.boundingBox

            val hp = target.health.roundToInt()
            newLines.add("§bxFormulaCosine§f: §c$hp HP")

            if (Config.showDistance) {
                val distance = localPlayer.distanceTo(target).roundToInt()
                newLines.add("§7$distance blocks away")
            }

            if (Config.showPosition) {
                targetBox?.let { box ->
                    newLines.add("§8X: ${box.minX.toInt()} Y: ${box.minY.toInt()} Z: ${box.minZ.toInt()}")
                }
            }

            if (Config.showArmor) {
                newLines.add("§7Armor: §f${target.armorValue}")
            }
        } else {
            targetBox = null
            newLines.add("§8Target: §7Not Found")
            newLines.add("§8(Searching...)")
        }

        activeLines = newLines
    }

    private fun renderHud(graphics: GuiGraphicsExtractor) {
        val client = Minecraft.getInstance()
        if (activeLines.isEmpty()) return

        val font = client.font
        val screenWidth = graphics.guiWidth()

        activeLines.forEachIndexed { index, text ->
            val x = screenWidth - font.width(text) - RIGHT_PADDING
            val y = TOP_PADDING + (index * LINE_SPACING)
            graphics.text(font, text, x, y, CommonColors.WHITE, true)
        }
    }

    private fun renderESP(context: LevelExtractionContext) {
        val target = context.level().players().find { it.name.string == "xFormulaCosine" } ?: return
        val partialTick = context.deltaTracker().getGameTimeDeltaPartialTick(true)
        val interpolatedPosition = target.getPosition(partialTick)
        val interpolatedBox = target.boundingBox.move(interpolatedPosition.subtract(target.position()))

        Gizmos.cuboid(
            interpolatedBox,
            GizmoStyle.stroke(CYAN, 2.0f)
        ).setAlwaysOnTop()
    }

    private const val CYAN: Int = -0xff0001
}

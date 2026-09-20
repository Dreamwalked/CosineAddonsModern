package com.dreamwalked.features

import com.dreamwalked.utils.Config
import net.minecraft.client.Minecraft
import net.minecraft.core.ClientAsset
import net.minecraft.resources.Identifier

object CustomCapes {
    private const val MOD_ID = "cosine-addons-modern"

    private val capes = mapOf(
        "evermore" to capeTexture("evermore"),
        "taylorswift" to capeTexture("taylorswift"),
        "wot" to capeTexture("wot")
    )

    private val legacyAssignments = mapOf(
        "Dreamwalked" to "wot",
        "xFormulaCosine" to "taylorswift",
        "Avendoraldera" to "wot",
        "Illistandrista_" to "wot",
        "zayn242ha" to "wot"
    )

    @JvmStatic
    fun getCapeTexture(playerName: String): ClientAsset.ResourceTexture? {
        val localPlayerName = Minecraft.getInstance().player?.gameProfile?.name

        if (playerName == localPlayerName && Config.customCape != "default") {
            return capes[Config.customCape]
        }

        return legacyAssignments[playerName]?.let(capes::get)
    }

    private fun capeTexture(name: String): ClientAsset.ResourceTexture {
        val assetId = Identifier.fromNamespaceAndPath(MOD_ID, "capes/$name")
        val texturePath = Identifier.fromNamespaceAndPath(MOD_ID, "textures/capes/$name.png")
        return ClientAsset.ResourceTexture(assetId, texturePath)
    }
}

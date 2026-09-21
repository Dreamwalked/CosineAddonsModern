package com.dreamwalked.mixin;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.impl.command.client.ClientCommandInternals;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientPacketListener.class, priority = 500)
public abstract class ClientPacketListenerCommandMixin {
    @Shadow
    private CommandDispatcher<SharedSuggestionProvider> commands;

    @Shadow
    @Final
    private ClientSuggestionProvider suggestionsProvider;

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Inject(method = "handleCommands", at = @At("RETURN"))
    private void cosine$overrideCfSuggestions(ClientboundCommandsPacket packet, CallbackInfo ci) {
        CommandDispatcher<FabricClientCommandSource> clientCommands = ClientCommandInternals.getActiveDispatcher();
        if (clientCommands == null || clientCommands.getRoot().getChild("cf") == null) {
            return;
        }

        CommandNode<?> root = commands.getRoot();
        CommandNodeAccessor accessor = (CommandNodeAccessor) root;
        accessor.cosine$getChildrenMap().remove("cf");
        accessor.cosine$getLiteralsMap().remove("cf");
        accessor.cosine$getArgumentsMap().remove("cf");

        ClientCommandInternals.addCommands(
            (CommandDispatcher) commands,
            (FabricClientCommandSource) suggestionsProvider
        );
    }
}

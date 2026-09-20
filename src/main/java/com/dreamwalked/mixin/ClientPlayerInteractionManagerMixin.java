package com.dreamwalked.mixin;

import com.dreamwalked.utils.Config;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(MultiPlayerGameMode.class)
public class ClientPlayerInteractionManagerMixin {
    @ModifyConstant(method = "continueDestroyBlock", constant = @Constant(intValue = 5))
    private int MiningCooldownFix(int value) {
        return Config.isNoBreakDelay() ? 0 : value;
    }
}

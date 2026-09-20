package com.dreamwalked.mixin;

import com.dreamwalked.features.CustomCapes;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void cosineAddons$applyCustomCape(CallbackInfoReturnable<PlayerSkin> cir) {
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        ClientAsset.ResourceTexture cape = CustomCapes.getCapeTexture(player.getGameProfile().name());

        if (cape == null) {
            return;
        }

        PlayerSkin.Patch patch = new PlayerSkin.Patch(
            Optional.empty(),
            Optional.of(cape),
            Optional.empty(),
            Optional.empty()
        );
        cir.setReturnValue(cir.getReturnValue().with(patch));
    }
}

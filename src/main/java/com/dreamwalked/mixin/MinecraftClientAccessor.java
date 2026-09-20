package com.dreamwalked.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftClientAccessor {

    @Accessor("rightClickDelay")
    int getItemUseCooldown();

    @Accessor("rightClickDelay")
    void setItemUseCooldown(int value);
}

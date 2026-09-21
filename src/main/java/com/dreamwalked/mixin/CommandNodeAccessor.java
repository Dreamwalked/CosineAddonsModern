package com.dreamwalked.mixin;

import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(CommandNode.class)
public interface CommandNodeAccessor {
    @Accessor("children")
    Map<String, CommandNode<?>> cosine$getChildrenMap();

    @Accessor("literals")
    Map<String, LiteralCommandNode<?>> cosine$getLiteralsMap();

    @Accessor("arguments")
    Map<String, ArgumentCommandNode<?, ?>> cosine$getArgumentsMap();
}

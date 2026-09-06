package com.goodbird.cnpcgeckoaddon.mixin.impl;

import com.goodbird.cnpcgeckoaddon.world.TemporaryFluidStore;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.FluidInteractionRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = FluidInteractionRegistry.class, remap = false)
public abstract class MixinFluidInteractionRegistry {
    @Redirect(method = "canInteract", at = @At(value = "INVOKE", target =
            "Lnet/neoforged/neoforge/fluids/FluidInteractionRegistry$HasFluidInteraction;test(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/material/FluidState;)Z"))
    private static boolean cnpcgeckoaddon$freezeInteraction(FluidInteractionRegistry.HasFluidInteraction predicate,
                                                           Level level, BlockPos pos, BlockPos neighbor,
                                                           FluidState state) {
        return !TemporaryFluidStore.isFrozen(level, pos)
                && !TemporaryFluidStore.isFrozen(level, neighbor)
                && predicate.test(level, pos, neighbor, state);
    }
}

package com.goodbird.cnpcgeckoaddon.mixin.impl;

import com.goodbird.cnpcgeckoaddon.world.TemporaryFluidStore;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.FluidInteractionRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = FluidInteractionRegistry.class, remap = false)
public abstract class MixinFluidInteractionRegistry {

    @WrapOperation(method = "canInteract", at = @At(value = "INVOKE", target =
            "Lnet/neoforged/neoforge/fluids/FluidInteractionRegistry$HasFluidInteraction;test(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/material/FluidState;)Z"))
    private static boolean cnpcgeckoaddon$freezeInteraction(
            FluidInteractionRegistry.HasFluidInteraction predicate, Level level, BlockPos pos,
            BlockPos neighbor, FluidState state, Operation<Boolean> original) {
        return !TemporaryFluidStore.isFrozen(level, pos)
                && !TemporaryFluidStore.isFrozen(level, neighbor)
                && original.call(predicate, level, pos, neighbor, state);
    }
}

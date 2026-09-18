package com.goodbird.cnpcgeckoaddon.mixin.impl;

import com.goodbird.cnpcgeckoaddon.utils.CrashGuard;
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
        // Only the addon's own question is guarded - the predicate is somebody else's, and what
        // it throws is theirs to answer for. A lookup that fails counts as "not frozen".
        boolean frozen;
        try {
            frozen = TemporaryFluidStore.isFrozen(level, pos) || TemporaryFluidStore.isFrozen(level, neighbor);
        } catch (Throwable error) {
            CrashGuard.caught("mixin.fluid.interaction", error);
            frozen = false;
        }
        return !frozen && original.call(predicate, level, pos, neighbor, state);
    }
}

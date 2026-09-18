package com.goodbird.cnpcgeckoaddon.mixin.impl;

import com.goodbird.cnpcgeckoaddon.utils.CrashGuard;
import com.goodbird.cnpcgeckoaddon.world.TemporaryFluidStore;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FluidState.class)
public abstract class MixinFluidState {
    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void cnpcgeckoaddon$freezeRandomTick(Level level, BlockPos pos, RandomSource random,
                                                CallbackInfo ci) {
        // A try written out, not a lambda: this is every random fluid tick in the world.
        try {
            if (TemporaryFluidStore.isFrozen(level, pos)) {
                ci.cancel();
            }
        } catch (Throwable error) {
            CrashGuard.caught("mixin.fluid.random_tick", error);
        }
    }
}

package com.goodbird.cnpcgeckoaddon.mixin.impl;

import com.goodbird.cnpcgeckoaddon.world.TemporaryFluidStore;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freezes the fluid blocks placed by {@link TemporaryFluidStore}.
 *
 * <p>A boss that spits a puddle of lava must not flood the arena: the puddle has to stay
 * exactly where it landed so the recorded terrain can be restored afterwards. Cancelling
 * the fluid tick stops both spreading and level decay, which turns the placed block into
 * a static one for as long as it lives.</p>
 */
@Mixin(FlowingFluid.class)
public abstract class MixinFlowingFluid {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void cnpcgeckoaddon$freezeTemporaryFluid(Level level, BlockPos pos, FluidState state, CallbackInfo ci) {
        if (TemporaryFluidStore.isFrozen(level, pos)) {
            ci.cancel();
        }
    }
}

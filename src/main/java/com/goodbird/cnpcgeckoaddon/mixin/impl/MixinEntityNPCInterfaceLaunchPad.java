package com.goodbird.cnpcgeckoaddon.mixin.impl;

import com.goodbird.cnpcgeckoaddon.world.NpcLaunchPadManager;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityNPCInterface.class)
public abstract class MixinEntityNPCInterfaceLaunchPad extends PathfinderMob {

    protected MixinEntityNPCInterfaceLaunchPad(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    /** At the tail, so a pad throws from wherever this tick's own movement left it. */
    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void cnpcgeckoaddon$tickLaunchPad(CallbackInfo ci) {
        if (level().isClientSide) {
            return;
        }
        NpcLaunchPadManager.tick((EntityNPCInterface) (Object) this);
    }
}

package com.goodbird.cnpcgeckoaddon.mixin.impl;

import com.goodbird.cnpcgeckoaddon.ai.TeleportPathController;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityNPCInterface.class)
public abstract class MixinEntityNPCInterfaceTeleportPath extends PathfinderMob {

    @Unique
    private TeleportPathController cnpcgeckoaddon$teleportPathController;

    protected MixinEntityNPCInterfaceTeleportPath(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void cnpcgeckoaddon$tickTeleportPath(CallbackInfo ci) {
        if (!level().isClientSide) {
            if (cnpcgeckoaddon$teleportPathController == null) {
                cnpcgeckoaddon$teleportPathController =
                        new TeleportPathController((EntityNPCInterface) (Object) this);
            }
            cnpcgeckoaddon$teleportPathController.tick();
        }
    }
}

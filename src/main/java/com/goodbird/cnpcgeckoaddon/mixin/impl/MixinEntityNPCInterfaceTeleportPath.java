package com.goodbird.cnpcgeckoaddon.mixin.impl;

import com.goodbird.cnpcgeckoaddon.ai.TeleportPathController;
import com.goodbird.cnpcgeckoaddon.mixin.IBossController;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
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
public abstract class MixinEntityNPCInterfaceTeleportPath extends PathfinderMob implements IBossController {

    @Unique
    private TeleportPathController cnpcgeckoaddon$teleportPathController;

    protected MixinEntityNPCInterfaceTeleportPath(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    @Unique
    public TeleportPathController cnpcgeckoaddon$getTeleportPathController() {
        return cnpcgeckoaddon$teleportPathController;
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

    @Inject(method = "stopSeenByPlayer", at = @At("HEAD"), remap = false)
    private void cnpcgeckoaddon$stopBossBarTracking(ServerPlayer player, CallbackInfo ci) {
        if (cnpcgeckoaddon$teleportPathController != null) {
            cnpcgeckoaddon$teleportPathController.removeBossBarPlayer(player);
        }
    }

    @Inject(method = "remove", at = @At("HEAD"), remap = false)
    private void cnpcgeckoaddon$shutdownBossBar(Entity.RemovalReason reason, CallbackInfo ci) {
        if (cnpcgeckoaddon$teleportPathController != null) {
            if (reason == Entity.RemovalReason.KILLED) {
                cnpcgeckoaddon$teleportPathController.stopBossBar();
            } else {
                cnpcgeckoaddon$teleportPathController.shutdown();
            }
        }
    }
}

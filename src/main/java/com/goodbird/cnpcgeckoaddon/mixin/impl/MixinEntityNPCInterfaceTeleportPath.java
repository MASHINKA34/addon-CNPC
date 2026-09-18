package com.goodbird.cnpcgeckoaddon.mixin.impl;

import com.goodbird.cnpcgeckoaddon.ai.TeleportPathController;
import com.goodbird.cnpcgeckoaddon.mixin.IBossController;
import com.goodbird.cnpcgeckoaddon.mixin.ITeleportPathData;
import com.goodbird.cnpcgeckoaddon.utils.CrashGuard;
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

@Mixin(value = EntityNPCInterface.class, priority = 1100)
public abstract class MixinEntityNPCInterfaceTeleportPath extends PathfinderMob implements IBossController {

    @Unique
    private TeleportPathController cnpcgeckoaddon$teleportPathController;

    /** Set by a controller that gave up; never saved, so a reload builds one again. */
    @Unique
    private boolean cnpcgeckoaddon$bossControllerDisabled;

    protected MixinEntityNPCInterfaceTeleportPath(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    @Unique
    public TeleportPathController cnpcgeckoaddon$getTeleportPathController() {
        return cnpcgeckoaddon$teleportPathController;
    }

    @Override
    @Unique
    public void cnpcgeckoaddon$clearTeleportPathController() {
        cnpcgeckoaddon$teleportPathController = null;
    }

    @Override
    @Unique
    public void cnpcgeckoaddon$disableBossController() {
        cnpcgeckoaddon$teleportPathController = null;
        cnpcgeckoaddon$bossControllerDisabled = true;
    }

    /**
     * The controller guards its own tick; the try here is for what comes before it - the
     * settings being read and a controller being built - which runs inside the npc's tick
     * just the same. Written out rather than a lambda: this is every npc, every tick.
     */
    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void cnpcgeckoaddon$tickTeleportPath(CallbackInfo ci) {
        if (level().isClientSide || cnpcgeckoaddon$bossControllerDisabled) {
            return;
        }
        try {
            EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
            if (cnpcgeckoaddon$teleportPathController == null) {
                if (!((ITeleportPathData) npc.ais).cnpcgeckoaddon$getTeleportPathData().isEnabled()) {
                    return;
                }
                cnpcgeckoaddon$teleportPathController = new TeleportPathController(npc);
            }
            cnpcgeckoaddon$teleportPathController.tick();
        } catch (Throwable error) {
            CrashGuard.caught("mixin.npc.boss_tick", error);
        }
    }

    @Inject(method = "stopSeenByPlayer", at = @At("HEAD"), remap = false)
    private void cnpcgeckoaddon$stopBossBarTracking(ServerPlayer player, CallbackInfo ci) {
        try {
            if (cnpcgeckoaddon$teleportPathController != null) {
                cnpcgeckoaddon$teleportPathController.removeBossBarPlayer(player);
            }
        } catch (Throwable error) {
            CrashGuard.caught("mixin.npc.boss_bar_untrack", error);
        }
    }

    @Inject(method = "remove", at = @At("HEAD"), remap = false)
    private void cnpcgeckoaddon$shutdownBossBar(Entity.RemovalReason reason, CallbackInfo ci) {
        // What escapes here escapes into the entity being removed, and leaves it half removed.
        try {
            if (cnpcgeckoaddon$teleportPathController != null) {
                if (reason == Entity.RemovalReason.KILLED) {
                    cnpcgeckoaddon$teleportPathController.stopBossBar();
                } else {
                    cnpcgeckoaddon$teleportPathController.shutdown();
                }
            }
        } catch (Throwable error) {
            CrashGuard.caught("mixin.npc.boss_remove", error);
        }
    }
}

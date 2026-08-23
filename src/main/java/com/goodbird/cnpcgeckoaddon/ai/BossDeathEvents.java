package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.data.BossEffectSet;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.entity.EntityFluidSpit;
import com.goodbird.cnpcgeckoaddon.mixin.IBossController;
import com.goodbird.cnpcgeckoaddon.mixin.ITeleportPathData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import noppes.npcs.entity.EntityNPCInterface;

/**
 * Everything that happens the moment a boss dies: clearing its minions and arming its
 * death explosion.
 *
 * <p>The controller also notices the death on its next tick, but CustomNPCs is free to
 * discard the NPC right away depending on its respawn settings - in that case there is no
 * next tick and the minions would be orphaned. Listening for the death itself closes that
 * hole; running the cleanup twice is harmless because the second pass finds nothing.</p>
 */
@EventBusSubscriber(modid = CNPCGeckoAddon.MODID)
public class BossDeathEvents {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBossDeath(final LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TeleportPathController.removePlayerFromBossBars(player);
        }
        if (!(event.getEntity() instanceof EntityNPCInterface npc)
                || !(npc.level() instanceof ServerLevel level)) {
            return;
        }
        if (npc instanceof IBossController holder) {
            TeleportPathController controller = holder.cnpcgeckoaddon$getTeleportPathController();
            if (controller != null) {
                controller.stopBossBar();
            }
        }
        TeleportPathData data = ((ITeleportPathData) npc.ais).cnpcgeckoaddon$getTeleportPathData();
        if (!data.isEnabled()) {
            return;
        }
        if (data.isClearMinionsOnDeath()) {
            BossMinionUtil.clear(level, npc, data.getMinionRemovalMode());
        }
        if (data.isExplosionEnabled()) {
            BossExplosionScheduler.schedule(level, npc, data);
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(final LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof EntityNPCInterface npc
                && event.getSource().getEntity() instanceof ServerPlayer player) {
            trackParticipant(npc, player);
        } else if (event.getEntity() instanceof ServerPlayer player
                && event.getSource().getEntity() instanceof EntityNPCInterface npc) {
            trackParticipant(npc, player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(final PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TeleportPathController.removePlayerFromBossBars(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(final PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TeleportPathController.removePlayerFromBossBars(player);
        }
    }

    private static void trackParticipant(EntityNPCInterface npc, ServerPlayer player) {
        if (npc instanceof IBossController holder) {
            TeleportPathController controller = holder.cnpcgeckoaddon$getTeleportPathController();
            if (controller != null) {
                controller.trackBossBarPlayer(player);
            }
        }
    }

    /**
     * Hangs the configured potion effects on whoever a boss projectile hits.
     *
     * <p>Ranged attacks and fluid spits land several ticks after they are fired, so the
     * effect cannot be applied when the ability executes - by then the victim may well have
     * dodged. Reading the boss' current phase at impact keeps the effect tied to the attack
     * that actually connected.</p>
     */
    @SubscribeEvent
    public static void onProjectileImpact(final ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        Projectile projectile = event.getProjectile();
        if (projectile.level().isClientSide
                || !(projectile.getOwner() instanceof EntityNPCInterface npc)
                || !(npc instanceof IBossController holder)) {
            return;
        }
        TeleportPathController controller = holder.cnpcgeckoaddon$getTeleportPathController();
        BossPhaseData phase = controller == null ? null : controller.activePhase();
        if (phase == null) {
            return;
        }
        BossEffectSet effects = projectile instanceof EntityFluidSpit
                ? phase.getFluidSpitEffects()
                : phase.getRangedAttackEffects();
        effects.applyAll(victim, npc);
    }

    @SubscribeEvent
    public static void onLevelTick(final LevelTickEvent.Post event) {
        if (BossExplosionScheduler.hasPending() && event.getLevel() instanceof ServerLevel level) {
            BossExplosionScheduler.tick(level);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(final LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            BossExplosionScheduler.clear(level);
            TeleportPathController.shutdownLevel(level);
        }
    }
}

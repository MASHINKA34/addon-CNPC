package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.data.BossEffectSet;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.entity.EntityFluidSpit;
import com.goodbird.cnpcgeckoaddon.mixin.IBossController;
import com.goodbird.cnpcgeckoaddon.mixin.ITeleportPathData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
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
public final class BossDeathEvents {
    private BossDeathEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBossDeath(final LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BossCaptureManager.releasePlayer(player);
            TeleportPathController.removePlayerFromEncounters(player);
        }
        if (BossTotemUtil.isTotem(event.getEntity())) {
            TeleportPathController.onTotemDeath(event.getEntity());
            BossCloneRespawnGuard.retire(event.getEntity());
            return;
        }
        if (BossMinionUtil.isMinion(event.getEntity())) {
            // No early return: a summoned clone can be a boss in its own right, and its own
            // death handling below still has to run.
            BossCloneRespawnGuard.retire(event.getEntity());
        }
        if (!(event.getEntity() instanceof EntityNPCInterface npc)
                || !(npc.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos encounterHome = arenaHome(npc);
        if (npc instanceof IBossController holder) {
            TeleportPathController controller = holder.cnpcgeckoaddon$getTeleportPathController();
            if (controller != null) {
                // Capture the arena first: clearing the runtime encounter intentionally
                // makes getArenaHome() return null after death.
                controller.onDeath();
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
        if (data.isChestEnabled()) {
            BossChestScheduler.schedule(level, npc, data, event.getSource().getEntity(), encounterHome);
        }
    }

    /**
     * Swallows every hit aimed at a boss protected by a phase or full-immunity totems.
     *
     * <p>This fires before any mitigation is calculated, so the hit is dropped whole rather
     * than reduced to zero - nothing downstream sees a damage number at all.</p>
     */
    @SubscribeEvent
    public static void onBossIncomingDamage(final LivingIncomingDamageEvent event) {
        if (BossTotemUtil.isTotem(event.getSource().getEntity())) {
            // The clone still ticks, animates, takes damage and runs death scripts; only its
            // accidental combat AI is prevented from hurting anything.
            event.setCanceled(true);
            return;
        }
        if (!(event.getEntity() instanceof EntityNPCInterface npc)
                || !(npc instanceof IBossController holder)) {
            return;
        }
        // Leaving this tag alone keeps /kill working: a boss nobody can remove while it is
        // immune would be untestable.
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        TeleportPathController controller = holder.cnpcgeckoaddon$getTeleportPathController();
        if (controller == null) {
            return;
        }
        boolean phaseProtection = controller.isInvulnerable();
        boolean totemProtection = controller.isTotemProtected()
                && controller.getTotemProtectionMode() == TeleportPathData.TOTEM_PROTECTION_FULL_IMMUNITY;
        if (!phaseProtection && !totemProtection) {
            return;
        }
        // Cancelling means LivingDamageEvent.Post never runs, so whoever swung still has to
        // be signed up for the boss bar here.
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            trackParticipant(npc, player);
        }
        if (totemProtection) {
            controller.playTotemHitFeedback();
        } else {
            controller.playInvulnerableHitFeedback();
        }
        event.setCanceled(true);
    }

    /**
     * Clamps the fully mitigated health damage, after armor and effects but before HP changes.
     * Absorption is applied later and can only make the guarded result safer.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBossDamagePre(final LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof EntityNPCInterface npc)
                || !(npc instanceof IBossController holder)
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        TeleportPathController controller = holder.cnpcgeckoaddon$getTeleportPathController();
        if (controller == null || !controller.isTotemProtected()
                || controller.getTotemProtectionMode() != TeleportPathData.TOTEM_PROTECTION_LETHAL_GUARD) {
            return;
        }
        float maximumDamage = Math.max(0.0F, npc.getHealth() - 1.0F);
        if (event.getNewDamage() <= maximumDamage) {
            return;
        }
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            trackParticipant(npc, player);
        }
        event.setNewDamage(maximumDamage);
        controller.playTotemHitFeedback();
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

    /**
     * Takes the self-respawn off every boss clone that enters a level, saved ones included.
     *
     * <p>This also fires as a clone is spawned, before the boss has marked it, which is why
     * the spawn paths suppress it themselves; what only this can reach is a totem or a minion
     * that was saved by a world built before the boss started claiming that decision.</p>
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(final EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof EntityNPCInterface npc)) {
            return;
        }
        if (BossTotemUtil.isTotem(npc) || BossMinionUtil.isMinion(npc)) {
            BossCloneRespawnGuard.suppressSelfRespawn(npc);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(final PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BossCaptureManager.releasePlayer(player);
            TeleportPathController.removePlayerFromEncounters(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(final PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BossCaptureManager.releasePlayer(player);
            TeleportPathController.removePlayerFromEncounters(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerStartsTracking(final PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TeleportPathController.syncTotemLinksForTracking(player, event.getTarget());
            BossCaptureManager.syncLinkForTracking(player, event.getTarget());
        }
    }

    /**
     * Where this boss stood when the fight began, for the chest placement that asks for it.
     *
     * @return null when the npc never ticked as a boss or was not in a fight, which sends
     *         the chest back to the spot the boss died on
     */
    private static BlockPos arenaHome(EntityNPCInterface npc) {
        if (npc instanceof IBossController holder) {
            TeleportPathController controller = holder.cnpcgeckoaddon$getTeleportPathController();
            if (controller != null) {
                return controller.getArenaHome();
            }
        }
        return null;
    }

    private static void trackParticipant(EntityNPCInterface npc, ServerPlayer player) {
        if (npc instanceof IBossController holder) {
            TeleportPathController controller = holder.cnpcgeckoaddon$getTeleportPathController();
            if (controller != null) {
                controller.trackParticipant(player);
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
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (BossExplosionScheduler.hasPending()) {
            BossExplosionScheduler.tick(level);
        }
        if (BossChestScheduler.hasPending()) {
            BossChestScheduler.tick(level);
        }
        if (BossAreaVfxScheduler.hasPending()) {
            BossAreaVfxScheduler.tick(level);
        }
        if (BossCloneRespawnGuard.hasPending()) {
            BossCloneRespawnGuard.tick(level);
        }
        BossCaptureManager.tick(level);
    }

    @SubscribeEvent
    public static void onLevelUnload(final LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            BossExplosionScheduler.clear(level);
            BossChestScheduler.clear(level);
            BossAreaVfxScheduler.clear(level);
            BossCloneRespawnGuard.clear(level);
            BossCaptureManager.clearLevel(level);
            TeleportPathController.shutdownLevel(level);
        }
    }
}

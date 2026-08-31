package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossEffectSet;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.NpcDamageResistEntry;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.entity.EntityFluidSpit;
import com.goodbird.cnpcgeckoaddon.mixin.IBossController;
import com.goodbird.cnpcgeckoaddon.mixin.INpcImmunityData;
import com.goodbird.cnpcgeckoaddon.mixin.ITeleportPathData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
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
        // Any captured victim, player or npc, has to be let go before it stops existing.
        BossCaptureManager.releaseVictim(event.getEntity());
        if (event.getEntity() instanceof ServerPlayer player) {
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
     * Turns away everything a totem's slot does not list as able to break it.
     *
     * <p>Registered at the ordinary priority, above the resistance list below: a hit this
     * drops whole never reaches a mere percentage, and whatever it lets by is then handled
     * exactly as any other hit on an npc would be.</p>
     *
     * <p>The list is read off the totem itself rather than out of its owner's settings,
     * because the owner may be standing in an unloaded chunk while its totem is being
     * swung at.</p>
     */
    @SubscribeEvent
    public static void onTotemVulnerability(final LivingIncomingDamageEvent event) {
        // Leaving the bypass tag alone is the escape hatch the protections above also leave:
        // /kill and the void keep working, or a totem with an empty list could never be got
        // rid of again.
        if (!BossTotemUtil.isTotem(event.getEntity())
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        int ability = incomingAbility(event.getSource());
        if (!BossTotemUtil.rejects(event.getEntity(), ability)) {
            return;
        }
        float before = event.getAmount();
        event.setCanceled(true);
        // Reported from here for the same reason the resistances report from their own
        // listener: nothing downstream is told this hit existed, let alone why it stopped.
        NpcDamageInfoManager.reportTotemBlock(event, before, ability);
    }

    /**
     * Which ability is behind one hit, or {@link BossAbilityDamageUtil#NO_ABILITY} for damage
     * that belongs to none.
     *
     * <p>Most abilities say so themselves while they land. The ones that cannot are read off
     * the hit instead: a projectile carries its own damage rather than going through the
     * ability door, and an explosion arrives after the boss that armed it is gone - so, the
     * same compromise the blast immunity makes, listing the death blast lets any explosion
     * break the totem.</p>
     */
    private static int incomingAbility(DamageSource source) {
        int landing = BossAbilityDamageUtil.currentAbility();
        if (landing != BossAbilityDamageUtil.NO_ABILITY) {
            return landing;
        }
        if (source.getDirectEntity() instanceof Projectile projectile
                && projectile.getOwner() instanceof EntityNPCInterface) {
            return projectile instanceof EntityFluidSpit
                    ? BossAbilityKind.FLUID : BossAbilityKind.RANGED;
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            return BossAbilityKind.BLAST;
        }
        return BossAbilityDamageUtil.NO_ABILITY;
    }

    /**
     * Applies the npc's own damage resistance list to whatever still comes in.
     *
     * <p>Registered LOW so the phase and totem protections above have already had their say -
     * a hit they swallowed whole never reaches a mere percentage. The CustomNPCs resistances
     * run later, inside mitigation, so this multiplier stacks on top of them: 50% here and
     * 50% there make 25%.</p>
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onNpcDamageResist(final LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof EntityNPCInterface npc)) {
            return;
        }
        float before = event.getAmount();
        NpcDamageResistEntry resist = null;
        if (npc.ais instanceof INpcImmunityData holder
                // The same escape hatch the phase protection leaves: /kill has to keep working.
                && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            resist = holder.cnpcgeckoaddon$getNpcImmunityData().findResist(event.getSource());
        }
        if (resist != null) {
            if (resist.getPercent() == 0) {
                // Cancelled rather than zeroed: full immunity should leave no knockback, no
                // hurt animation and nothing for on-hit effects to ride in on.
                event.setCanceled(true);
            } else {
                event.setAmount(before * resist.getPercent() / 100.0F);
            }
        }
        // Reported from here rather than its own listener so the breakdown can name the rule
        // that fired and both sides of the multiplication.
        NpcDamageInfoManager.report(event, before, resist);
    }

    /**
     * Lets an npc marked immune to the death blast stand in the crater.
     *
     * <p>The explosion is a vanilla one going off after the boss is gone, so there is no
     * ability call left to filter it from the inside; what it leaves behind is a damage
     * source tagged as an explosion, and that is what this catches. It cannot tell whose
     * explosion it was, which is why the setting is described as covering every blast rather
     * than only the boss one.</p>
     */
    @SubscribeEvent
    public static void onBlastImmuneDamage(final LivingIncomingDamageEvent event) {
        if (event.getSource().is(DamageTypeTags.IS_EXPLOSION)
                && BossAbilityDamageUtil.isImmune(event.getEntity(), BossAbilityKind.BLAST)) {
            event.setCanceled(true);
        }
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

    /**
     * A boss is not hurt by the arc it threw itself along.
     *
     * <p>The controller wipes the fall distance every tick of a leap, so what is left here
     * is at most one tick of drop - but a jump down off a ledge still crosses vanilla's
     * three block threshold, and dying to your own signature move is not a mechanic.</p>
     *
     * <p>Only a leap in flight is covered: a boss that walks off a ledge on its own falls
     * exactly as it always did.</p>
     */
    @SubscribeEvent
    public static void onBossLeapFall(final LivingFallEvent event) {
        if (!(event.getEntity() instanceof EntityNPCInterface npc)
                || !(npc instanceof IBossController holder)) {
            return;
        }
        TeleportPathController controller = holder.cnpcgeckoaddon$getTeleportPathController();
        if (controller != null && controller.isLeaping()) {
            event.setCanceled(true);
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
            BossCaptureManager.releaseVictim(player);
            TeleportPathController.removePlayerFromEncounters(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(final PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BossCaptureManager.releaseVictim(player);
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
        int ability = projectile instanceof EntityFluidSpit
                ? BossAbilityKind.FLUID
                : BossAbilityKind.RANGED;
        if (BossAbilityDamageUtil.isImmune(victim, ability)) {
            // The whole impact is dropped rather than only the potions: a projectile carries
            // its own damage, and an ability that passes an npc by cannot leave that behind.
            // Asked before the phase is read, because immunity does not depend on which part
            // of the fight the boss happens to be in.
            event.setCanceled(true);
            return;
        }
        TeleportPathController controller = holder.cnpcgeckoaddon$getTeleportPathController();
        BossPhaseData phase = controller == null ? null : controller.activePhase();
        if (phase == null) {
            return;
        }
        BossEffectSet effects = ability == BossAbilityKind.FLUID
                ? phase.getFluidSpitEffects()
                : phase.getRangedAttackEffects();
        BossAbilityDamageUtil.applyEffects(victim, ability, npc, effects);
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
        if (BossGeyserScheduler.hasPending()) {
            BossGeyserScheduler.tick(level);
        }
        if (BossBoulderRainScheduler.hasPending()) {
            BossBoulderRainScheduler.tick(level);
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
            BossGeyserScheduler.clear(level);
            BossBoulderRainScheduler.clear(level);
            BossCloneRespawnGuard.clear(level);
            BossCaptureManager.clearLevel(level);
            TeleportPathController.shutdownLevel(level);
        }
    }
}

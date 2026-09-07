package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossEffectSet;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.entity.EntityFluidSpit;
import com.goodbird.cnpcgeckoaddon.mixin.IBossController;
import com.goodbird.cnpcgeckoaddon.mixin.ITeleportPathData;
import com.goodbird.cnpcgeckoaddon.utils.PersistentDataUtil;
import com.goodbird.cnpcgeckoaddon.world.BossMinionCleanupStore;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import noppes.npcs.entity.EntityNPCInterface;

/**
 * What happens as the things a boss owns come into a level and go out of it again: its own
 * death, the clones and totems that join, and the projectiles it throws.
 *
 * <p>The death half exists because the controller also notices the death on its next tick,
 * but CustomNPCs is free to discard the NPC right away depending on its respawn settings -
 * in that case there is no next tick and the minions would be orphaned. Listening for the
 * death itself closes that hole; running the cleanup twice is harmless because the second
 * pass finds nothing.</p>
 */
@EventBusSubscriber(modid = CNPCGeckoAddon.MODID)
public final class BossLifecycleEvents {
    private static final String PROJECTILE_EFFECTS_KEY = "cnpcgeckoaddon:boss_projectile_effects";

    private BossLifecycleEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBossDeath(final LivingDeathEvent event) {
        // Any captured victim, player or npc, has to be let go before it stops existing.
        BossCaptureManager.releaseVictim(event.getEntity());
        BossTetherManager.releaseVictim(event.getEntity());
        BossCocoonManager.releaseVictim(event.getEntity());
        // And a cocoon that died some other way than a hit lets its victim out.
        BossCocoonManager.onShellDeath(event.getEntity());
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
        // The cocoon guards go with the boss whatever the minion setting says, after the
        // clear so the builder's removal mode gets them first when it is on.
        BossCocoonUtil.removeGuards(level, npc);
        if (data.isExplosionEnabled()) {
            BossExplosionScheduler.schedule(level, npc, data);
        }
        if (data.isChestEnabled()) {
            BossChestScheduler.schedule(level, npc, data, event.getSource().getEntity(), encounterHome);
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

    /**
     * Takes the self-respawn off every boss clone that enters a level, saved ones included.
     *
     * <p>This also fires as a clone is spawned, before the boss has marked it, which is why
     * the spawn paths suppress it themselves; what only this can reach is a totem or a minion
     * that was saved by a world built before the boss started claiming that decision.</p>
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(final EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        // An entity that already carried its owner's mark before it got here was never handed
        // to the index, so the answer it is holding is one entity short until it is retaken.
        if (BossOwnedEntityIndex.isOwned(event.getEntity())) {
            BossOwnedEntityIndex.invalidate();
        }
        if (event.loadedFromDisk() && BossMinionUtil.isMinion(event.getEntity())) {
            int removalMode = BossMinionCleanupStore.get(level).pendingRemovalMode(event.getEntity());
            if (removalMode >= 0) {
                if (removalMode == TeleportPathData.MINION_REMOVAL_KILL) {
                    BossMinionUtil.scheduleRemoval(event.getEntity(), removalMode);
                } else {
                    event.setCanceled(true);
                }
                return;
            }
        }
        if (!(event.getEntity() instanceof EntityNPCInterface npc)) {
            return;
        }
        // A cocoon coming back in from a save is a shell with nobody inside: the hold it was
        // part of died with the server. Kept out rather than let in and discarded on the
        // boss' first tick, because its chunk may load long after the boss' did.
        if (event.loadedFromDisk() && BossCocoonUtil.isCocoon(npc)) {
            event.setCanceled(true);
            return;
        }
        if (BossTotemUtil.isTotem(npc) || BossMinionUtil.isMinion(npc)) {
            BossCloneRespawnGuard.suppressSelfRespawn(npc);
        }
    }

    @SubscribeEvent
    public static void onProjectileJoinLevel(final EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || event.loadedFromDisk()
                || !(event.getEntity() instanceof Projectile projectile)
                || PersistentDataUtil.contains(projectile, PROJECTILE_EFFECTS_KEY, Tag.TAG_LIST)
                || !(projectile.getOwner() instanceof EntityNPCInterface npc)
                || !(npc instanceof IBossController holder)) {
            return;
        }
        TeleportPathController controller = holder.cnpcgeckoaddon$getTeleportPathController();
        BossPhaseData phase = controller == null ? null : controller.activePhase();
        BossEffectSet effects = phase == null ? new BossEffectSet()
                : projectile instanceof EntityFluidSpit ? phase.fluidSpit().getEffects() : phase.rangedAttack().getEffects();
        projectile.getPersistentData().put(PROJECTILE_EFFECTS_KEY, effects.writeToNBT());
    }

    @SubscribeEvent
    public static void onProjectileImpact(final ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        Projectile projectile = event.getProjectile();
        if (projectile.level().isClientSide
                || !(projectile.getOwner() instanceof EntityNPCInterface npc)) {
            return;
        }
        int ability = projectile instanceof EntityFluidSpit
                ? BossAbilityKind.FLUID
                : BossAbilityKind.RANGED;
        if (BossAbilityDamageUtil.isImmune(victim, ability)) {
            // The whole impact is dropped rather than only the potions: a projectile carries
            // its own damage, and an ability that passes an npc by cannot leave that behind.
            event.setCanceled(true);
            return;
        }
        BossEffectSet effects = new BossEffectSet();
        effects.readFromNBT(PersistentDataUtil.read(projectile), PROJECTILE_EFFECTS_KEY);
        BossAbilityDamageUtil.applyEffects(victim, ability, npc, effects);
    }
}

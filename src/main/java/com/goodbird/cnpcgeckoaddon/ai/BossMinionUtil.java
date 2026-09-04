package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/** Ownership bookkeeping for the clones a boss summons. */
public final class BossMinionUtil {
    /** Written into the minion's persistent data so it survives save/load and chunk unloads. */
    public static final String MINION_OWNER_KEY = "CNPCGeckoBossOwner";
    public static final String MINION_PHASE_KEY = "CNPCGeckoBossMinionPhase";
    public static final String MINION_SLOT_KEY = "CNPCGeckoBossMinionSlot";

    private BossMinionUtil() {
    }

    public static void markAsMinion(Entity minion, Entity boss) {
        // A clone saved from a totem must become an ordinary minion for caps and cleanup.
        minion.getPersistentData().remove(BossTotemUtil.TOTEM_OWNER_KEY);
        minion.getPersistentData().remove(BossTotemUtil.TOTEM_SLOT_KEY);
        minion.getPersistentData().putString(MINION_OWNER_KEY, boss.getUUID().toString());
        minion.getPersistentData().remove(MINION_PHASE_KEY);
        minion.getPersistentData().remove(MINION_SLOT_KEY);
        // And one saved from a cocoon or its guard: the role would keep it out of the caps.
        BossCocoonUtil.clearRole(minion);
    }

    public static void markAsMinion(Entity minion, Entity boss, int phaseIndex, int pointId) {
        markAsMinion(minion, boss);
        if (phaseIndex >= 0 && pointId > 0) {
            minion.getPersistentData().putInt(MINION_PHASE_KEY, phaseIndex);
            minion.getPersistentData().putInt(MINION_SLOT_KEY, pointId);
        }
    }

    public static boolean isMinionOf(Entity entity, Entity boss) {
        return entity != boss
                && boss.getUUID().toString().equals(entity.getPersistentData().getString(MINION_OWNER_KEY));
    }

    /** Whether some boss summoned this, without caring which one - the owner may be unloaded. */
    public static boolean isMinion(Entity entity) {
        return entity != null && !entity.getPersistentData().getString(MINION_OWNER_KEY).isEmpty();
    }

    public static int countAlive(ServerLevel level, Entity boss) {
        return countAlive(level, boss, Integer.MAX_VALUE);
    }

    /**
     * The same count, cut short at {@code cap}: exact below it, and simply "at least the
     * cap" once it is reached. Every caller only compares against the cap, and a minion can
     * be anywhere in the world - this walk cannot be boxed in, so it is kept short instead.
     *
     * <p>A cocoon and its guard are minions for ownership's sake and nothing else: neither
     * was summoned by the wave the caps limit, so neither takes a seat from it.</p>
     */
    public static int countAlive(ServerLevel level, Entity boss, int cap) {
        int count = 0;
        for (Entity entity : level.getAllEntities()) {
            if (entity.isAlive() && isMinionOf(entity, boss) && !BossCocoonUtil.hasRole(entity)) {
                count++;
                if (count >= cap) {
                    return count;
                }
            }
        }
        return count;
    }

    /** Whether any minion of this boss is alive at all, stopping at the first one found. */
    public static boolean hasAlive(ServerLevel level, Entity boss) {
        return countAlive(level, boss, 1) > 0;
    }

    /** Searches loaded entities only, so checking a slot never loads its chunk. */
    public static boolean isSlotOccupied(ServerLevel level, Entity boss, int phaseIndex, int pointId) {
        for (Entity entity : level.getAllEntities()) {
            if (entity.isAlive() && isMinionOf(entity, boss)
                    && entity.getPersistentData().getInt(MINION_PHASE_KEY) == phaseIndex
                    && entity.getPersistentData().getInt(MINION_SLOT_KEY) == pointId) {
                return true;
            }
        }
        return false;
    }

    /**
     * Removes everything the boss summoned.
     *
     * <p>The matches are collected first: discarding entities while walking the level's
     * entity list would modify it mid-iteration.</p>
     *
     * @param removalMode one of the {@code MINION_REMOVAL_*} constants on {@link TeleportPathData}
     */
    public static void clear(ServerLevel level, Entity boss, int removalMode) {
        List<Entity> minions = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (isMinionOf(entity, boss)) {
                minions.add(entity);
            }
        }
        for (Entity minion : minions) {
            if (removalMode == TeleportPathData.MINION_REMOVAL_KILL
                    && minion instanceof LivingEntity living && living.isAlive()) {
                living.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
                if (!living.isAlive() || living.isRemoved()) {
                    continue;
                }
                // An invulnerable clone shrugged the damage off; it still must not
                // outlive its owner, so fall through to discarding it.
            }
            level.sendParticles(ParticleTypes.POOF,
                    minion.getX(), minion.getY(0.5D), minion.getZ(), 8,
                    minion.getBbWidth() * 0.5D, minion.getBbHeight() * 0.5D,
                    minion.getBbWidth() * 0.5D, 0.02D);
            minion.discard();
        }
    }
}

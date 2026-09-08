package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.utils.PersistentDataUtil;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.world.BossCocoonGuardCleanupStore;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * The marker that tells a cocoon and its guard apart from the minions they are marked as.
 *
 * <p>Both are ordinary boss minions as far as ownership goes - the owner key is what makes
 * a fight's reset, the boss' death and a reload take them away, and what keeps the boss'
 * own abilities off them. The role on top is what keeps them out of the summon's caps and
 * the immune phase's "all minions dead" count, and what lets a cocoon nobody is inside be
 * recognised after a restart. It is written into the same persistent data the owner key
 * lives in, so it survives a save exactly as far as the owner key does.</p>
 */
public final class BossCocoonUtil {
    /** Written beside the minion owner key; its value is one of the two roles below. */
    public static final String ROLE_KEY = "CNPCGeckoBossCocoonRole";
    public static final String ROLE_COCOON = "cocoon";
    public static final String ROLE_GUARD = "guard";

    private BossCocoonUtil() {
    }

    /** Marks a clone as the cocoon closed round a victim: a minion of the boss, with the role on top. */
    public static void markAsCocoon(Entity clone, Entity boss) {
        BossMinionUtil.markAsMinion(clone, boss);
        clone.getPersistentData().putString(ROLE_KEY, ROLE_COCOON);
    }

    /** Marks a clone as the guard posted beside a cocoon: a minion of the boss that outlives the cocoon. */
    public static void markAsGuard(Entity clone, Entity boss) {
        BossMinionUtil.markAsMinion(clone, boss);
        clone.getPersistentData().putString(ROLE_KEY, ROLE_GUARD);
        if (boss.level() instanceof ServerLevel level) {
            clone.getPersistentData().putLong(BossCocoonGuardCleanupStore.GENERATION_KEY,
                    BossCocoonGuardCleanupStore.get(level).generation(boss.getUUID()));
        }
    }

    /** Takes the role off, for a clone that is being made an ordinary minion or a totem. */
    public static void clearRole(Entity clone) {
        clone.getPersistentData().remove(ROLE_KEY);
        clone.getPersistentData().remove(BossCocoonGuardCleanupStore.GENERATION_KEY);
    }

    public static boolean isCocoon(Entity entity) {
        return ROLE_COCOON.equals(PersistentDataUtil.getString(entity, ROLE_KEY));
    }

    public static boolean isGuard(Entity entity) {
        return ROLE_GUARD.equals(PersistentDataUtil.getString(entity, ROLE_KEY));
    }

    /** Whether this minion is a cocoon or a guard: the two the summon's caps leave out. */
    public static boolean hasRole(Entity entity) {
        return !PersistentDataUtil.getString(entity, ROLE_KEY).isEmpty();
    }

    public static boolean isCocoonOf(Entity entity, Entity boss) {
        return isCocoon(entity) && BossMinionUtil.isMinionOf(entity, boss);
    }

    public static boolean isGuardOf(Entity entity, Entity boss) {
        return isGuard(entity) && BossMinionUtil.isMinionOf(entity, boss);
    }

    /**
     * Discards every loaded cocoon of this boss that nobody is being held in.
     *
     * <p>Run on the boss' first tick, the way loaded totems it no longer knows are discarded:
     * a cocoon is an ordinary saved entity, and one that made it through a restart is a shell
     * with nobody inside. Discarded rather than killed, exactly as a cocoon that has done its
     * job is: no drops and no death scripts. Searches loaded entities only and never asks a
     * chunk to load; a cocoon in a chunk that loads later is dropped as it comes in.</p>
     *
     * <p>Read out of {@link BossOwnedEntityIndex} rather than off a walk of the level: both
     * roles are marked as minions of the boss, so they are already in the one walk the index
     * takes per level per tick. A walk of its own here was a second pass over every entity in
     * the world, per boss, on the tick a chunk full of them loaded.</p>
     */
    public static void removeStrayCocoons(ServerLevel level, Entity boss) {
        List<Entity> stray = new ArrayList<>();
        for (Entity entity : BossOwnedEntityIndex.minionsOf(level, boss)) {
            if (isCocoonOf(entity, boss) && !BossCocoonManager.isHolding(entity.getUUID())) {
                stray.add(entity);
            }
        }
        // Collected first: discarding an entity invalidates the index, and emptying the list
        // being walked mid-walk is exactly the crash the index was built to stop.
        for (Entity cocoon : stray) {
            cocoon.discard();
        }
    }

    /**
     * Discards every loaded guard of this boss, for the ways out of a fight that take
     * everything. Reads the shared index for the reason {@link #removeStrayCocoons} does.
     */
    public static void removeGuards(ServerLevel level, Entity boss) {
        BossCocoonGuardCleanupStore.get(level).invalidate(boss.getUUID(), TeleportPathData.MINION_REMOVAL_VANISH);
        List<Entity> guards = new ArrayList<>();
        for (ServerLevel dimension : level.getServer().getAllLevels()) {
            for (Entity entity : BossOwnedEntityIndex.minionsOf(dimension, boss)) {
                if (isGuardOf(entity, boss)) {
                    guards.add(entity);
                }
            }
        }
        for (Entity guard : guards) {
            guard.discard();
        }
    }
}

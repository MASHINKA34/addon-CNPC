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

    private BossMinionUtil() {
    }

    public static void markAsMinion(Entity minion, Entity boss) {
        minion.getPersistentData().putString(MINION_OWNER_KEY, boss.getUUID().toString());
    }

    public static boolean isMinionOf(Entity entity, Entity boss) {
        return entity != boss
                && boss.getUUID().toString().equals(entity.getPersistentData().getString(MINION_OWNER_KEY));
    }

    public static int countAlive(ServerLevel level, Entity boss) {
        int count = 0;
        for (Entity entity : level.getAllEntities()) {
            if (entity.isAlive() && isMinionOf(entity, boss)) {
                count++;
            }
        }
        return count;
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

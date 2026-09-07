package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.utils.PersistentDataUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One walk of a level's entities per tick, shared by everything that asks who a boss owns.
 *
 * <p>A minion, a cocoon or a totem may stand anywhere in the world, so the only honest answer
 * to "how many of these are still alive" is a walk of the level - there is no box to search
 * inside. That is affordable once. It was being paid for separately by the minion cap, the
 * spawn-slot lookup and every totem slot with nobody standing in it, on every boss, several
 * times a tick: on a populated server that walk was most of what a boss cost.</p>
 *
 * <p>So the walk happens at most once per level per tick and everyone reads the same answer
 * out of it. Entities that died after it was taken are filtered out where the lists are read,
 * exactly as they were before, and anything newly marked as owned throws the index away so
 * the next question rebuilds it - a boss summoning a wave still counts what it just spawned.</p>
 *
 * <p>Server thread only, like everything it indexes.</p>
 */
final class BossOwnedEntityIndex {

    private static final Map<String, List<Entity>> MINIONS = new HashMap<>();
    private static final Map<String, List<Entity>> TOTEMS = new HashMap<>();

    private static ResourceKey<Level> builtFor;
    private static long builtAt = Long.MIN_VALUE;

    private BossOwnedEntityIndex() {
    }

    /** Every loaded entity this boss summoned, dead ones included. */
    static List<Entity> minionsOf(ServerLevel level, Entity boss) {
        build(level);
        return MINIONS.getOrDefault(boss.getUUID().toString(), List.of());
    }

    /** Every loaded totem standing for this boss, dead ones included. */
    static List<Entity> totemsOf(ServerLevel level, Entity boss) {
        build(level);
        return TOTEMS.getOrDefault(boss.getUUID().toString(), List.of());
    }

    /**
     * Throws the answer away because the world no longer matches it: something was just
     * marked as owned, or an entity that already carried a marker was loaded in.
     */
    static void invalidate() {
        builtAt = Long.MIN_VALUE;
        builtFor = null;
        MINIONS.clear();
        TOTEMS.clear();
    }

    /** Whether this entity is one the index would have to be rebuilt for. */
    static boolean isOwned(Entity entity) {
        return !PersistentDataUtil.getString(entity, BossMinionUtil.MINION_OWNER_KEY).isEmpty()
                || !PersistentDataUtil.getString(entity, BossTotemUtil.TOTEM_OWNER_KEY).isEmpty();
    }

    private static void build(ServerLevel level) {
        if (builtAt == level.getGameTime() && level.dimension().equals(builtFor)) {
            return;
        }
        builtAt = level.getGameTime();
        builtFor = level.dimension();
        MINIONS.clear();
        TOTEMS.clear();
        for (Entity entity : level.getAllEntities()) {
            String minionOwner = PersistentDataUtil.getString(entity, BossMinionUtil.MINION_OWNER_KEY);
            if (!minionOwner.isEmpty()) {
                MINIONS.computeIfAbsent(minionOwner, ignored -> new ArrayList<>()).add(entity);
            }
            String totemOwner = PersistentDataUtil.getString(entity, BossTotemUtil.TOTEM_OWNER_KEY);
            if (!totemOwner.isEmpty()
                    && PersistentDataUtil.getInt(entity, BossTotemUtil.TOTEM_SLOT_KEY) > 0) {
                TOTEMS.computeIfAbsent(totemOwner, ignored -> new ArrayList<>()).add(entity);
            }
        }
    }
}

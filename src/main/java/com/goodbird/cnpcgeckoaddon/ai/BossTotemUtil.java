package com.goodbird.cnpcgeckoaddon.ai;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Persistent ownership markers kept separate from ordinary summoned minions. */
public final class BossTotemUtil {
    public static final String TOTEM_OWNER_KEY = "CNPCGeckoBossTotemOwner";
    public static final String TOTEM_SLOT_KEY = "CNPCGeckoBossTotemSlot";
    public static final String DEAD_SLOTS_KEY = "CNPCGeckoBossTotemDeadSlots";

    private BossTotemUtil() {
    }

    public static void markAsTotem(Entity totem, Entity boss, int slotId) {
        // A clone can itself have been saved from a summoned NPC. Totems must never enter
        // the minion caps or invulnerable-phase cleanup even when that stale marker exists.
        totem.getPersistentData().remove(BossMinionUtil.MINION_OWNER_KEY);
        totem.getPersistentData().putString(TOTEM_OWNER_KEY, boss.getUUID().toString());
        totem.getPersistentData().putInt(TOTEM_SLOT_KEY, Math.max(1, slotId));
    }

    public static boolean isTotemOf(Entity entity, Entity boss) {
        return entity != boss && isTotem(entity)
                && boss.getUUID().toString().equals(entity.getPersistentData().getString(TOTEM_OWNER_KEY));
    }

    public static boolean isTotem(Entity entity) {
        return entity != null && !entity.getPersistentData().getString(TOTEM_OWNER_KEY).isEmpty()
                && entity.getPersistentData().getInt(TOTEM_SLOT_KEY) > 0;
    }

    public static int slotId(Entity entity) {
        return entity.getPersistentData().getInt(TOTEM_SLOT_KEY);
    }

    /** Searches loaded entities only and never asks the target chunk to load. */
    public static Entity findAlive(ServerLevel level, Entity boss, int slotId) {
        for (Entity entity : level.getAllEntities()) {
            if (entity.isAlive() && slotId(entity) == slotId && isTotemOf(entity, boss)) {
                return entity;
            }
        }
        return null;
    }

    public static List<Entity> findAllLoaded(ServerLevel level, Entity boss) {
        List<Entity> result = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (isTotemOf(entity, boss)) {
                result.add(entity);
            }
        }
        return result;
    }

    /** Discards only this owner's totems, without death drops or clone kill scripts. */
    public static void removeLoaded(ServerLevel level, Entity boss) {
        for (Entity totem : findAllLoaded(level, boss)) {
            totem.discard();
        }
    }

    public static Set<Integer> readDeadSlots(Entity boss) {
        Set<Integer> result = new HashSet<>();
        for (int slotId : boss.getPersistentData().getIntArray(DEAD_SLOTS_KEY)) {
            if (slotId > 0) {
                result.add(slotId);
            }
        }
        return result;
    }

    public static void writeDeadSlots(Entity boss, Set<Integer> deadSlots) {
        int[] sorted = deadSlots.stream().filter(id -> id != null && id > 0)
                .mapToInt(Integer::intValue).sorted().toArray();
        if (sorted.length == 0) {
            boss.getPersistentData().remove(DEAD_SLOTS_KEY);
        } else {
            boss.getPersistentData().putIntArray(DEAD_SLOTS_KEY, sorted);
        }
    }
}

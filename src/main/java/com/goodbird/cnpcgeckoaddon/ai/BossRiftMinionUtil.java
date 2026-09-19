package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.utils.PersistentDataUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.entity.EntityNPCInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The marker that tells a rift's own minions apart from the minions they are marked as, and the
 * way they are stood up on a platform in the rift dimension.
 *
 * <p>A rift minion is an ordinary boss minion as far as ownership goes - the owner key is what a
 * fight's reset and the boss' death take away, in whatever level it stands. The role on top is
 * what keeps it out of the summon's caps and the immune phase's "all minions dead", what the rift
 * counts its own by, and what tells a save being loaded to leave it behind once its rift is shut:
 * the rift keeps its minions by UUID in memory alone.</p>
 */
public final class BossRiftMinionUtil {
    /** Written beside the minion owner key; its value is the one role below. */
    public static final String ROLE_KEY = "CNPCGeckoBossRiftMinion";
    public static final String ROLE_RIFT = "rift";

    private static final Logger LOGGER = LoggerFactory.getLogger(CNPCGeckoAddon.MODID);
    /** Clones already complained about, so a missing one costs one line and not one per rift. */
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private BossRiftMinionUtil() {
    }

    /** Marks a clone as one of the rift's minions: a minion of the boss, with the role on top. */
    static void markAsRiftMinion(Entity minion, Entity boss) {
        BossMinionUtil.markAsMinion(minion, boss);
        minion.getPersistentData().putString(ROLE_KEY, ROLE_RIFT);
    }

    /** Takes the role off, for a clone that is being made an ordinary minion or a totem. */
    public static void clearRole(Entity entity) {
        entity.getPersistentData().remove(ROLE_KEY);
    }

    public static boolean isRiftMinion(Entity entity) {
        return ROLE_RIFT.equals(PersistentDataUtil.getString(entity, ROLE_KEY));
    }

    public static boolean isRiftMinionOf(Entity entity, Entity boss) {
        return isRiftMinion(entity) && BossMinionUtil.isMinionOf(entity, boss);
    }

    /**
     * Stands one minion up in the rift dimension, the summon's way: a CustomNPCs clone out of its
     * tab, marked as the boss' and kept from coming back on its own, set on a victim when given one.
     *
     * @param yaw    the way it faces, or NaN to leave the clone's own
     * @param target who it goes after first, or null
     * @return the minion, in the world; or null when the clone could not be spawned, which is logged once
     */
    static Entity spawn(ServerLevel level, EntityNPCInterface boss, String cloneName, int cloneTab,
                        Vec3 position, float yaw, LivingEntity target) {
        if (cloneName == null || cloneName.isBlank()) {
            return null;
        }
        String cloneKey = cloneTab + ":" + cloneName;
        try {
            IEntity<?> wrapper = NpcAPI.Instance().getClones().spawn(position.x, position.y, position.z,
                    cloneTab, cloneName, NpcAPI.Instance().getIWorld(level));
            if (wrapper == null || wrapper.getMCEntity() == null) {
                warn(cloneKey, boss, "clone returned no entity");
                return null;
            }
            Entity minion = wrapper.getMCEntity();
            markAsRiftMinion(minion, boss);
            BossCloneRespawnGuard.suppressSelfRespawn(minion);
            if (Float.isFinite(yaw)) {
                minion.setYRot(yaw);
                if (minion instanceof Mob mob) {
                    mob.setYHeadRot(yaw);
                    mob.yBodyRot = yaw;
                }
            }
            if (minion instanceof Mob mob && target != null && mob.canAttack(target)) {
                mob.setTarget(target);
            }
            return minion;
        } catch (Throwable error) {
            // CustomNPCs is free to throw from a script hook on the way in: one line, and the rift
            // simply stands fewer minions up.
            warn(cloneKey, boss, error.getMessage());
            return null;
        }
    }

    private static void warn(String cloneKey, EntityNPCInterface boss, String reason) {
        if (REPORTED.add(cloneKey)) {
            LOGGER.warn("Cannot spawn reality rift minion clone {} for boss {}: {}", cloneKey,
                    boss.getName().getString(), reason);
        }
    }
}

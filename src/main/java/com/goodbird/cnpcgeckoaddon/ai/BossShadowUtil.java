package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossShadowSettings;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.mixin.ITeleportPathData;
import com.goodbird.cnpcgeckoaddon.utils.PersistentDataUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.controllers.ServerCloneController;
import noppes.npcs.entity.EntityNPCInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

/**
 * The marker that tells a shadow copy apart from the minions it is marked as, and the way a
 * copy is built out of the boss' own saved data.
 *
 * <p>A copy is an ordinary boss minion as far as ownership goes - the owner key is what keeps
 * the boss' own abilities off it and what a fight's reset and the boss' death take away. The
 * role on top is what keeps it out of the summon's caps, what tells a save being loaded to
 * leave it behind, and what lets the copy's own owner be kept off the copy's target list. It
 * is written into the same persistent data the owner key lives in, and it is the only thing
 * about the copies that is ever saved: the boss keeps its copies by UUID in memory alone.</p>
 */
public final class BossShadowUtil {
    /** Written beside the minion owner key; its value is the one role below. */
    public static final String ROLE_KEY = "CNPCGeckoBossShadow";
    public static final String ROLE_SHADOW = "shadow";

    private static final Logger LOGGER = LoggerFactory.getLogger(CNPCGeckoAddon.MODID);

    /** What the CustomNPCs clone controller strips, for the rare server that has none loaded yet. */
    private static final String[] CLONE_TAGS = {"StartPosNew", "StartPos", "MovingPathNew", "Pos", "Riding", "UUID"};

    private BossShadowUtil() {
    }

    /** Marks a copy as the boss' shadow: a minion of the boss, with the role on top. */
    static void markAsShadow(Entity copy, Entity boss, int phaseIndex) {
        BossMinionUtil.markAsMinion(copy, boss, phaseIndex, 0);
        copy.getPersistentData().putString(ROLE_KEY, ROLE_SHADOW);
    }

    /** Takes the role off, for a clone that is being made an ordinary minion or a totem. */
    public static void clearRole(Entity entity) {
        entity.getPersistentData().remove(ROLE_KEY);
    }

    public static boolean isShadow(Entity entity) {
        return ROLE_SHADOW.equals(PersistentDataUtil.getString(entity, ROLE_KEY));
    }

    public static boolean isShadowOf(Entity entity, Entity boss) {
        return isShadow(entity) && BossMinionUtil.isMinionOf(entity, boss);
    }

    /**
     * Whether {@code other} is on a shadow copy's own side: the boss it was copied from, or
     * anything else that boss owns - another copy, a summoned minion, a totem. False for
     * anything that is not a copy, so an ordinary boss reads this as no filter at all.
     */
    public static boolean isOwnSide(Entity copy, Entity other) {
        if (!isShadow(copy)) {
            return false;
        }
        String owner = PersistentDataUtil.getString(copy, BossMinionUtil.MINION_OWNER_KEY);
        if (owner.isEmpty()) {
            return false;
        }
        return owner.equals(other.getUUID().toString())
                || owner.equals(PersistentDataUtil.getString(other, BossMinionUtil.MINION_OWNER_KEY))
                || owner.equals(PersistentDataUtil.getString(other, BossTotemUtil.TOTEM_OWNER_KEY));
    }

    /**
     * Stands one copy of the boss up on a spot: the boss' own saved data, cut down to what a
     * copy may keep, made into a second npc.
     *
     * <p>Built the way CustomNPCs spawns a clone - the tag cleaned of the things one npc must
     * not share with another, a position put in, the entity created from the tag - so the copy
     * looks, moves and animates exactly as the boss does, the model and the size included. The
     * cutting down happens before the copy is added to the world: its controller is built on
     * its first tick from whatever its settings say then, so nothing of the boss' chest, blast,
     * totems or bar is ever armed on it.</p>
     *
     * @return the copy, in the world; or null when the boss cannot be copied, which is logged
     */
    static EntityNPCInterface spawnCopy(ServerLevel level, EntityNPCInterface boss, BossPhaseData phase,
                                        int phaseIndex, Vec3 position, float yaw, LivingEntity target) {
        String typeId = boss.getEncodeId();
        if (typeId == null) {
            return null;
        }
        try {
            CompoundTag tag = new CompoundTag();
            boss.saveWithoutId(tag);
            cleanTags(tag);
            tag.putString("id", typeId);
            ListTag pos = new ListTag();
            pos.add(DoubleTag.valueOf(position.x));
            pos.add(DoubleTag.valueOf(position.y));
            pos.add(DoubleTag.valueOf(position.z));
            tag.put("Pos", pos);
            Entity created = EntityType.create(tag, level).orElse(null);
            if (!(created instanceof EntityNPCInterface copy)) {
                if (created != null) {
                    created.discard();
                }
                LOGGER.warn("Cannot build a shadow copy of boss {}: its saved data made no npc",
                        boss.getName().getString());
                return null;
            }
            BossShadowSettings shadow = phase.shadow();
            TeleportPathData data = ((ITeleportPathData) copy.ais).cnpcgeckoaddon$getTeleportPathData();
            data.stripToShadow(phase, shadow.getAbilities());
            // The native bar goes the way the styled one did: a second bar is a second boss.
            copy.display.setBossbar(0);
            // No path to blink along; the clone controller dropped the boss' one, and a copy
            // that happened to keep it would blink away from the fight on its own clock.
            copy.ais.setMovingPath(new ArrayList<>());
            copy.ais.setStartPos(copy.blockPosition());
            BossCloneRespawnGuard.suppressSelfRespawn(copy);
            int maxHealth = shadow.copyMaxHealth(boss.getMaxHealth());
            copy.stats.setMaxHealth(maxHealth);
            copy.setHealth(maxHealth);
            // A copy killed drops nothing of the boss' loot and gives nothing of its experience:
            // the maps CustomNPCs rolls the drops from, and the range it rolls the experience in.
            copy.inventory.drops.clear();
            copy.inventory.dropchance.clear();
            copy.inventory.setExp(0, 0);
            markAsShadow(copy, boss, phaseIndex);
            copy.setYRot(yaw);
            copy.yRotO = yaw;
            copy.setYHeadRot(yaw);
            copy.yHeadRotO = yaw;
            copy.yBodyRot = yaw;
            copy.yBodyRotO = yaw;
            if (target != null && copy.canAttack(target)) {
                copy.setTarget(target);
            }
            if (!level.addFreshEntity(copy)) {
                LOGGER.warn("Cannot add a shadow copy of boss {} to the world", boss.getName().getString());
                return null;
            }
            return copy;
        } catch (Throwable error) {
            // CustomNPCs is free to throw from a script hook on the way in. One line, and the
            // cast simply stands fewer copies up.
            LOGGER.warn("Cannot build a shadow copy of boss {}: {}", boss.getName().getString(), error.getMessage());
            return null;
        }
    }

    private static void cleanTags(CompoundTag tag) {
        if (ServerCloneController.Instance != null) {
            ServerCloneController.Instance.cleanTags(tag);
        } else {
            for (String key : CLONE_TAGS) {
                tag.remove(key);
            }
        }
        // Whoever rides the boss is not copied with it: a passenger stood up twice is a bug
        // rather than a likeness, and load() would leave the tag lying about anyway.
        tag.remove("Passengers");
    }
}

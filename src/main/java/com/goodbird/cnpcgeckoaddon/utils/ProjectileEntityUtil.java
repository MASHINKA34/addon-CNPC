package com.goodbird.cnpcgeckoaddon.utils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public class ProjectileEntityUtil {
    private static final Logger LOGGER = LogManager.getLogger("cnpcgeckoaddon");
    private static final Map<EntityType<?>, Boolean> USABLE = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<String> PROJECTILE_NAMES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "minecraft:arrow",
            "minecraft:spectral_arrow",
            "minecraft:trident",
            "minecraft:snowball",
            "minecraft:egg",
            "minecraft:ender_pearl",
            "minecraft:experience_bottle",
            "minecraft:potion",
            "minecraft:firework_rocket",
            "minecraft:llama_spit",
            "minecraft:dragon_fireball",
            "minecraft:fireball",
            "minecraft:small_fireball",
            "minecraft:wither_skull",
            "minecraft:shulker_bullet",
            "minecraft:wind_charge",
            "minecraft:breeze_wind_charge"
    )));
    private static final List<String> PROJECTILE_PATH_MARKERS = Arrays.asList(
            "arrow", "bolt", "bullet", "projectile", "missile", "rocket", "grenade", "bomb",
            "fireball", "snowball", "potion", "trident", "dart", "shuriken", "spear", "javelin",
            "cannonball", "shell", "shot", "beam", "laser", "spell", "orb", "pearl", "skull",
            "charge", "spit", "meteor", "webball", "spineball"
    );

    public static boolean isUsable(EntityType<?> type) {
        return !Boolean.FALSE.equals(USABLE.get(type));
    }

    public static void markUsable(EntityType<?> type) {
        if (type != null) {
            USABLE.put(type, Boolean.TRUE);
        }
    }

    public static void markUnusable(EntityType<?> type, Entity npc, Throwable error) {
        if (Boolean.FALSE.equals(USABLE.put(type, Boolean.FALSE))) {
            return;
        }
        String npcName = npc == null ? "unknown" : npc.getName().getString();
        if (error == null) {
            LOGGER.warn("Entity {} is not a projectile, npc {} falls back to the default projectile", getId(type), npcName);
        } else {
            LOGGER.warn("Entity {} cannot be used as a projectile of npc {} ({}: {}), falling back to the default projectile",
                    getId(type), npcName, error.getClass().getSimpleName(), error.getMessage());
        }
    }

    public static EntityType<?> getType(String id) {
        ResourceLocation location = id == null ? null : ResourceLocation.tryParse(id);
        if (location == null) {
            return null;
        }
        return BuiltInRegistries.ENTITY_TYPE.getOptional(location).orElse(null);
    }

    public static String getId(EntityType<?> type) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return id == null ? "unknown" : id.toString();
    }

    /**
     * Performs a registry-only check that is safe to call from a GUI.
     *
     * EntityType#create must never be used while building the selector: large modpacks can contain
     * hundreds of MISC entity types and some modded factories require server-only context. Creating
     * all of them on the client used to freeze or crash the game when the Select button was pressed.
     */
    public static boolean isProjectile(EntityType<?> type, Level ignoredLevel) {
        if (type == null || type.getCategory() != MobCategory.MISC || !isUsable(type)) {
            return false;
        }
        String id = getId(type);
        if (PROJECTILE_NAMES.contains(id)) {
            return true;
        }
        ResourceLocation location = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (location == null) {
            return false;
        }
        String path = location.getPath();
        for (String marker : PROJECTILE_PATH_MARKERS) {
            if (path.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSelectable(String id, Level level) {
        return isProjectile(getType(id), level);
    }

    public static List<String> getSelectableIds(Level level) {
        List<String> list = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (isProjectile(type, level)) {
                list.add(getId(type));
            }
        }
        Collections.sort(list);
        return list;
    }
}

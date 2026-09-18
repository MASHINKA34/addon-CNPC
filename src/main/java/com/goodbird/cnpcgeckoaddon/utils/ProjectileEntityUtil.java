package com.goodbird.cnpcgeckoaddon.utils;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.data.RangedExtraData;
import com.goodbird.cnpcgeckoaddon.mixin.IRangedData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import noppes.npcs.api.wrapper.ItemStackWrapper;
import noppes.npcs.entity.EntityNPCInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public class ProjectileEntityUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(CNPCGeckoAddon.MODID);
    private static final Map<EntityType<?>, Boolean> USABLE = Collections.synchronizedMap(new WeakHashMap<>());
    /** Ten seconds: an npc with nothing to shoot says so once in a while, not once per attack. */
    private static final long NO_SHOT_WARNING_INTERVAL_TICKS = 200L;
    private static final Map<Entity, Long> NEXT_NO_SHOT_WARNING = Collections.synchronizedMap(new WeakHashMap<>());
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

    /**
     * Whether a ranged attack of this npc would put anything in the air.
     *
     * <p>Says why not when it would not: a boss asks this before it winds up, so for a boss
     * the question is the only place a shot that never happens can be heard of at all.</p>
     */
    public static boolean canShoot(EntityNPCInterface npc) {
        if (chooseShot(npc) != ProjectileShotChoice.NONE) {
            return true;
        }
        warnNoShot(npc);
        return false;
    }

    /** What this npc's next ranged attack is fired with, as things stand right now. */
    public static ProjectileShotChoice chooseShot(EntityNPCInterface npc) {
        RangedExtraData extra = ((IRangedData) npc.stats.ranged).getRangedExtraData();
        String custom = extra.getProjectileEntity();
        String fallback = extra.getFallbackProjectile();
        return ProjectileShotChoice.decide(
                !custom.isEmpty(), isShootable(getType(custom)),
                !hasProjectileItem(npc),
                !fallback.isEmpty(), isShootable(getType(fallback)));
    }

    /**
     * Whether the npc's projectile slot holds an actual item.
     *
     * <p>The wrapper CustomNPCs keeps there is no answer: a slot that was filled and emptied
     * again keeps its wrapper around an empty stack, and CustomNPCs itself only ever asks
     * whether the wrapper is there - which is how it comes to throw a projectile of nothing.</p>
     */
    public static boolean hasProjectileItem(EntityNPCInterface npc) {
        ItemStack stack = ItemStackWrapper.MCItem(npc.inventory.getProjectile());
        return stack != null && !stack.isEmpty();
    }

    /** A type that is registered and has not let an npc down yet. */
    private static boolean isShootable(EntityType<?> type) {
        return type != null && isUsable(type);
    }

    /**
     * Says, at most once in ten seconds per npc, that its ranged attack has nothing to fire.
     */
    public static void warnNoShot(EntityNPCInterface npc) {
        long gameTime = npc.level().getGameTime();
        Long next = NEXT_NO_SHOT_WARNING.get(npc);
        if (next != null && gameTime < next) {
            return;
        }
        NEXT_NO_SHOT_WARNING.put(npc, gameTime + NO_SHOT_WARNING_INTERVAL_TICKS);
        String fallback = ((IRangedData) npc.stats.ranged).getRangedExtraData().getFallbackProjectile();
        LOGGER.warn("Npc {} does not shoot: it has no projectile item, no usable projectile entity and {}. "
                        + "Give it a projectile item or set a fallback projectile in its ranged extras",
                nameOf(npc), fallback.isEmpty() ? "no fallback projectile"
                        : "its fallback projectile " + fallback + " is not a usable projectile");
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
        if (error == null) {
            LOGGER.warn("Entity {} is not a projectile: npc {} shoots its projectile item or its fallback projectile instead",
                    getId(type), nameOf(npc));
        } else {
            LOGGER.warn("Entity {} cannot be used as a projectile of npc {} ({}: {}): it shoots its projectile item "
                            + "or its fallback projectile instead",
                    getId(type), nameOf(npc), error.getClass().getSimpleName(), error.getMessage());
        }
    }

    /** A name for the log, from an npc that may be halfway through loading. */
    private static String nameOf(Entity npc) {
        try {
            return npc == null ? "unknown" : npc.getName().getString();
        } catch (RuntimeException error) {
            return "unknown";
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

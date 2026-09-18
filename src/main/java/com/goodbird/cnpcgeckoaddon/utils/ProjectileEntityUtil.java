package com.goodbird.cnpcgeckoaddon.utils;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.data.RangedExtraData;
import com.goodbird.cnpcgeckoaddon.mixin.IRangedData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.projectile.Projectile;
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
    /** Why a type was refused, for the one line an npc gets when its id is dropped. */
    private static final Map<EntityType<?>, String> REFUSALS = Collections.synchronizedMap(new WeakHashMap<>());
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

    /** What this npc's next ranged attack is fired with, as things stand right now. Server only. */
    public static ProjectileShotChoice chooseShot(EntityNPCInterface npc) {
        RangedExtraData extra = ((IRangedData) npc.stats.ranged).getRangedExtraData();
        // Loading already went through this; an id put there by a setter since has not.
        validate(npc, extra);
        Level level = npc.level();
        String custom = extra.getProjectileEntity();
        String fallback = extra.getFallbackProjectile();
        return ProjectileShotChoice.decide(
                !custom.isEmpty(), isShootable(getType(custom), level),
                !hasProjectileItem(npc),
                !fallback.isEmpty(), isShootable(getType(fallback), level));
    }

    /**
     * Finds out whether the entities this npc names are projectiles while it is being set up,
     * rather than by firing one: the id of an entity that is not is dropped, with one line
     * saying whose it was and why.
     *
     * <p>Runs wherever the ids arrive on the server - the npc's settings being read, and any
     * question about what it shoots. It used to be the first shot that found out, which meant
     * a boss wound up for an attack it could not make, and CustomNPCs was then left to make
     * it with whatever the npc had in its slot. The fallback is asked too, so the answer is
     * there before anybody needs it, but it is left alone: dropping it would turn a typo into
     * "never shoot", while keeping it keeps the warning that names it.</p>
     */
    public static void validate(EntityNPCInterface npc, RangedExtraData extra) {
        Level level = npc == null ? null : npc.level();
        if (level == null || level.isClientSide) {
            return;
        }
        String custom = extra.getProjectileEntity();
        // An id nothing is registered under is left for /cnpcgecko fix: the mod it came from
        // may only be missing for now, and there is nothing to create and look at anyway.
        EntityType<?> customType = getType(custom);
        if (customType != null && !isShootable(customType, level)) {
            extra.setProjectileEntity("");
            LOGGER.warn("Npc {} names {} as its projectile entity, but {}: the id is dropped, and the npc shoots "
                            + "its projectile item or its fallback projectile instead",
                    nameOf(npc), custom, REFUSALS.getOrDefault(customType, "it is not usable as a projectile"));
        }
        isShootable(getType(extra.getFallbackProjectile()), level);
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

    /**
     * Whether this type really makes a projectile - asked of the type itself, once.
     *
     * <p>The registry cannot say. {@code EntityType.getBaseClass()} answers {@code Entity} for
     * every type on NeoForge, and an id with "projectile" in it is only a guess: a gun mod's
     * {@code tesla_projectile} extends {@code Entity}, got past that guess, and the npc that
     * was given it brought the server down. So one entity is made on the server, looked at and
     * thrown away without ever joining the level, and the answer is kept for the type.</p>
     */
    private static boolean isShootable(EntityType<?> type, Level level) {
        if (type == null) {
            return false;
        }
        Boolean known = USABLE.get(type);
        if (known != null) {
            return known;
        }
        if (level == null || level.isClientSide) {
            // Nothing is ever created on a client; all it has is the picker's guess.
            return isProjectile(type, level);
        }
        return probe(type, level);
    }

    private static boolean probe(EntityType<?> type, Level level) {
        Entity entity = null;
        String refusal;
        try {
            entity = type.create(level);
            refusal = entity instanceof Projectile ? null
                    : entity == null ? "that type makes no entity in this world"
                    : "it is a " + entity.getClass().getName() + ", which is not a projectile";
        } catch (Throwable error) {
            refusal = "creating one failed (" + error.getClass().getSimpleName() + ": " + error.getMessage() + ")";
        }
        discardQuietly(entity);
        USABLE.put(type, refusal == null);
        if (refusal != null) {
            REFUSALS.put(type, refusal);
        }
        return refusal == null;
    }

    private static void discardQuietly(Entity entity) {
        if (entity == null) {
            return;
        }
        try {
            entity.discard();
        } catch (Throwable ignored) {
            // A third-party entity that cannot even be thrown away was never in the level.
        }
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
        EntityType<?> type = getType(fallback);
        String fallbackState = fallback.isEmpty() ? "no fallback projectile"
                : "its fallback projectile " + fallback + " cannot be fired (" + (type == null
                ? "no entity is registered under that id"
                : REFUSALS.getOrDefault(type, "it is not usable as a projectile")) + ")";
        LOGGER.warn("Npc {} does not shoot: it has no projectile item, no usable projectile entity and {}. "
                        + "Give it a projectile item or set a fallback projectile in its ranged extras",
                nameOf(npc), fallbackState);
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
        REFUSALS.put(type, error == null ? "it is not a projectile"
                : "shooting one failed (" + error.getClass().getSimpleName() + ": " + error.getMessage() + ")");
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

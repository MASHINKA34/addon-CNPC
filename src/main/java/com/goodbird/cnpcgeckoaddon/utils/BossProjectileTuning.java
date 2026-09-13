package com.goodbird.cnpcgeckoaddon.utils;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * The numbers a boss hands the thing it throws, on the tick it throws it.
 *
 * <p>A glob of fluid and a stone are entities of their own: they outlive the tick that spawned
 * them, they are saved with their chunk, and by the time one of them lands its boss may be in
 * another phase, in another world, or dead. So what used to be a literal inside the projectile -
 * the gravity it falls under, how long it may live, how much it splashes - is written onto the
 * projectile here, the way the potions a shot carries already are, rather than looked up again
 * later.</p>
 *
 * <p>Persistent data rather than the projectile's own save keys, for the same reason the potions
 * use it: NeoForge writes the block out with the entity and reads it back before
 * {@code readAdditionalSaveData} runs, so a stone that survives a reload keeps the numbers it
 * was launched with for free. A projectile spawned without any of these keys - one from before
 * these were settings, or one whose caster never set them - reads back the literal it always
 * had, which is what {@code fallback} is for.</p>
 */
public final class BossProjectileTuning {

    /** Thousandths of a block a tick the glob loses to gravity. */
    public static final String GRAVITY = "cnpcgeckoaddon:projectile_gravity";
    /** Ticks a projectile that never hits anything is allowed to keep flying. */
    public static final String LIFE_TICKS = "cnpcgeckoaddon:projectile_life";
    /** Splash particles at radius zero, and how many each block of radius adds. */
    public static final String SPLASH_BASE = "cnpcgeckoaddon:projectile_splash_base";
    public static final String SPLASH_PER_RADIUS = "cnpcgeckoaddon:projectile_splash_per_radius";

    /** Tenths of a block a rolling stone climbs; anything taller is a wall to it. */
    public static final String STEP_HEIGHT = "cnpcgeckoaddon:boulder_step";
    /** Tenths of a block a tick a falling stone may reach. */
    public static final String MAX_FALL_SPEED = "cnpcgeckoaddon:boulder_fall_speed";
    /** Blocks of floorless drop after which a stone counts as lost rather than rolling. */
    public static final String MAX_PIT_DEPTH = "cnpcgeckoaddon:boulder_pit_depth";
    /** Thousandths of a block a tick a thrown or dropped stone loses to gravity. */
    public static final String THROW_GRAVITY = "cnpcgeckoaddon:boulder_gravity";
    /** Slack on top of the flight, and the ceiling over the whole lifetime, both in ticks. */
    public static final String LIFETIME_MARGIN = "cnpcgeckoaddon:boulder_life_margin";
    public static final String LIFETIME_MAX = "cnpcgeckoaddon:boulder_life_max";
    /** Debris at size zero, and how much each block of diameter adds. */
    public static final String DEBRIS_BASE = "cnpcgeckoaddon:boulder_debris_base";
    public static final String DEBRIS_PER_SIZE = "cnpcgeckoaddon:boulder_debris_per_size";
    /** How long the wave a shattering stone sends out travels for. */
    public static final String SHATTER_VFX_TICKS = "cnpcgeckoaddon:boulder_shatter_vfx";
    /** Prefix the noise a stone breaks with is written under, as a sound cue's four keys. */
    public static final String BREAK_SOUND = "cnpcgeckoaddon:boulder_break_sound";

    private BossProjectileTuning() {
    }

    /** Writes one number onto the projectile, to be read off it whenever it is needed. */
    public static void put(Entity projectile, String key, int value) {
        if (projectile != null) {
            projectile.getPersistentData().putInt(key, value);
        }
    }

    /** @return the number the boss wrote, clamped, or {@code fallback} when it wrote none */
    public static int read(Entity projectile, String key, int fallback, int min, int max) {
        return read(PersistentDataUtil.read(projectile), key, fallback, min, max);
    }

    /** The same off a plain tag: the half of this a test can ask without a world to throw in. */
    public static int read(CompoundTag data, String key, int fallback, int min, int max) {
        return data != null && data.contains(key, Tag.TAG_INT)
                ? Mth.clamp(data.getInt(key), min, max) : fallback;
    }
}

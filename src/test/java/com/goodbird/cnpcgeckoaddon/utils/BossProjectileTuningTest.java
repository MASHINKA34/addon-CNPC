package com.goodbird.cnpcgeckoaddon.utils;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The promise the thrown things are held to: what the boss wrote on them is what they run on,
 * and a projectile carrying nothing runs on the literal it always did.
 *
 * <p>Asked of the tag rather than of a spawned entity, which is the whole reason the read is a
 * function of a tag: a glob and a stone need a world, a registry and a boss to exist, and none
 * of the three has any say in this answer.</p>
 */
class BossProjectileTuningTest {

    @Test
    @DisplayName("a projectile the boss wrote nothing on keeps the old constants")
    void anEmptyTagIsTheOldConstant() {
        CompoundTag none = new CompoundTag();

        assertEquals(50, BossProjectileTuning.read(none, BossProjectileTuning.GRAVITY, 50, 0, 300));
        assertEquals(200, BossProjectileTuning.read(none, BossProjectileTuning.LIFE_TICKS, 200, 20, 1200));
        assertEquals(12, BossProjectileTuning.read(none, BossProjectileTuning.SPLASH_BASE, 12, 0, 100));
        assertEquals(50, BossProjectileTuning.read(none, BossProjectileTuning.THROW_GRAVITY, 50, 5, 300));
        assertEquals(60, BossProjectileTuning.read(none, BossProjectileTuning.LIFETIME_MARGIN, 60, 0, 600));
        assertEquals(1500, BossProjectileTuning.read(none, BossProjectileTuning.LIFETIME_MAX, 1500, 60, 6000));
    }

    @Test
    @DisplayName("what the boss wrote is what the projectile reads back")
    void whatWasWrittenIsWhatIsRead() {
        CompoundTag data = new CompoundTag();
        data.putInt(BossProjectileTuning.GRAVITY, 200);
        data.putInt(BossProjectileTuning.LIFE_TICKS, 40);
        data.putInt(BossProjectileTuning.SPLASH_PER_RADIUS, 0);
        data.putInt(BossProjectileTuning.STEP_HEIGHT, 0);

        assertEquals(200, BossProjectileTuning.read(data, BossProjectileTuning.GRAVITY, 50, 0, 300));
        assertEquals(40, BossProjectileTuning.read(data, BossProjectileTuning.LIFE_TICKS, 200, 20, 1200));
        assertEquals(0, BossProjectileTuning.read(data, BossProjectileTuning.SPLASH_PER_RADIUS, 8, 0, 50));
        assertEquals(0, BossProjectileTuning.read(data, BossProjectileTuning.STEP_HEIGHT, 10, 0, 30));
    }

    @Test
    @DisplayName("a number from a broken save is clamped rather than trusted")
    void anExtremeIsClamped() {
        CompoundTag data = new CompoundTag();
        data.putInt(BossProjectileTuning.GRAVITY, Integer.MAX_VALUE);
        data.putInt(BossProjectileTuning.LIFETIME_MAX, Integer.MIN_VALUE);
        // A key holding something that is not a number is a key the projectile never wrote.
        data.putString(BossProjectileTuning.DEBRIS_BASE, "nonsense");

        assertEquals(300, BossProjectileTuning.read(data, BossProjectileTuning.GRAVITY, 50, 0, 300));
        assertEquals(60, BossProjectileTuning.read(data, BossProjectileTuning.LIFETIME_MAX, 1500, 60, 6000));
        assertEquals(20, BossProjectileTuning.read(data, BossProjectileTuning.DEBRIS_BASE, 20, 0, 100));
    }
}

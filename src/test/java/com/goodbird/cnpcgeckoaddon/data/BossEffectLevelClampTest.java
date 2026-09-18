package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A residue's stacks go on top of its slot's own level, and the sum has to stay an amplifier
 * vanilla can save.
 *
 * <p>An effect is saved with its amplifier as an unsigned byte ({@code MobEffectInstance.Details},
 * {@code ExtraCodecs.UNSIGNED_BYTE}). Vanilla clamps a potion's amplifier to that as the instance
 * is built, but the fire's level never becomes one: it is the addon's own number, multiplied into
 * every burn. The slot's own level is held to 1..10 by the editor, the stacks are not, so the sum
 * is what has to be held - without wrapping round on a stack count near the top of an int.</p>
 */
class BossEffectLevelClampTest {

    private static BossEffectData levelTen() {
        BossEffectData slot = new BossEffectData();
        slot.setEnabled(true);
        slot.setEffectId("minecraft:poison");
        slot.setLevel(10);
        return slot;
    }

    @Test
    @DisplayName("the stacks add to the slot's own level up to vanilla's ceiling and no further")
    void stacksAreHeldUnderTheByte() {
        BossEffectData slot = levelTen();
        assertEquals(9, slot.getAmplifier());
        assertEquals(9, slot.amplifierWith(0));
        assertEquals(29, slot.amplifierWith(20), "level 10 and 20 stacks is amplifier 29");
        assertEquals(BossEffectData.MAX_AMPLIFIER, slot.amplifierWith(1000));
        assertEquals(255, BossEffectData.MAX_AMPLIFIER);
        assertEquals(256, slot.levelWith(1000), "the fire burns at the same ceiling, one based");
    }

    @Test
    @DisplayName("a stack count near the top of an int does not wrap round below the slot's own level")
    void aHugeStackCountDoesNotOverflow() {
        BossEffectData slot = levelTen();
        assertEquals(BossEffectData.MAX_AMPLIFIER, slot.amplifierWith(Integer.MAX_VALUE));
        assertEquals(BossEffectData.MAX_AMPLIFIER, slot.amplifierWith(Integer.MAX_VALUE - 5));
    }

    @Test
    @DisplayName("stacks only ever add: a negative count leaves the slot's own level")
    void negativeStacksAddNothing() {
        assertEquals(9, levelTen().amplifierWith(-20));
        assertEquals(9, levelTen().amplifierWith(Integer.MIN_VALUE));
    }

    @Test
    @DisplayName("the addon's ceiling is vanilla's, and the clamped amplifier survives a save and a load")
    void theClampedAmplifierSaves() {
        int amplifier = levelTen().amplifierWith(Integer.MAX_VALUE);
        MobEffectInstance clamped = new MobEffectInstance(MobEffects.POISON, 100, amplifier);
        assertEquals(amplifier, clamped.getAmplifier(), "vanilla keeps an amplifier inside its ceiling as it is");
        CompoundTag saved = assertDoesNotThrow(() -> (CompoundTag) clamped.save());
        assertEquals(amplifier, MobEffectInstance.load(saved).getAmplifier());

        // Vanilla holds a potion to the same ceiling as it is built; the addon's clamp is what keeps
        // the fire's level, which no MobEffectInstance ever sees, under it too.
        MobEffectInstance tooHigh = new MobEffectInstance(MobEffects.POISON, 100, BossEffectData.MAX_AMPLIFIER + 1);
        assertEquals(BossEffectData.MAX_AMPLIFIER, tooHigh.getAmplifier(),
                "vanilla's ceiling moved; MAX_AMPLIFIER has to follow it");
    }
}

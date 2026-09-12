package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the fire pseudo-effect to the slot it borrows: the id has to pass the same checks a
 * potion id does, and a slot holding it has to save exactly the way every other slot saves -
 * the bosses already out there are stored in that format.
 */
class BossFireEffectSlotTest {

    @Test
    @DisplayName("the fire id is known, but it is not a potion")
    void fireIsAKnownIdWithoutAMobEffect() {
        assertTrue(BossEffectData.isKnownEffect(BossEffectData.FIRE_ID));
        assertTrue(BossEffectData.isKnownEffect("  " + BossEffectData.FIRE_ID + " "),
                "the screen trims what was typed, so padding must not make the id unknown");
        assertNull(BossEffectData.resolve(BossEffectData.FIRE_ID),
                "fire is not a MobEffect and must never resolve to one");

        assertTrue(BossEffectData.isKnownEffect("minecraft:poison"), "potions should stay known");
        assertFalse(BossEffectData.isKnownEffect("cnpcgeckoaddon:fires"));
        assertFalse(BossEffectData.isKnownEffect(""));
        assertFalse(BossEffectData.isKnownEffect(null));
    }

    @Test
    @DisplayName("the picker list opens with the fire, once, ahead of the potions")
    void selectableIdsStartWithFire() {
        List<String> ids = BossEffectData.getSelectableIds();
        assertEquals(BossEffectData.FIRE_ID, ids.get(0));
        assertEquals(1, ids.stream().filter(BossEffectData.FIRE_ID::equals).count());
        assertTrue(ids.contains("minecraft:poison"), "the potions should still be listed");
    }

    @Test
    @DisplayName("a fire slot survives write -> read -> write in the old format")
    void fireSlotSurvivesTheSaveRoundTrip() {
        BossEffectData fire = new BossEffectData();
        fire.setEnabled(true);
        fire.setEffectId(BossEffectData.FIRE_ID);
        fire.setDurationTicks(100);
        fire.setLevel(3);
        fire.setShowParticles(false);

        CompoundTag once = fire.writeToNBT();
        assertEquals(Set.of("Enabled", "Effect", "Duration", "Amplifier", "Particles"), once.getAllKeys(),
                "fire should be stored in the keys every slot already has, and no others");

        BossEffectData reread = new BossEffectData();
        reread.readFromNBT(once);
        assertEquals(once, reread.writeToNBT(), "write -> read -> write should reproduce the identical slot");
        assertTrue(reread.isEnabled());
        assertEquals(BossEffectData.FIRE_ID, reread.getEffectId());
        assertEquals(100, reread.getDurationTicks());
        assertEquals(3, reread.getLevel());
    }

    @Test
    @DisplayName("a set mixing fire and a potion keeps each slot where it was")
    void mixedSetSurvivesTheSaveRoundTrip() {
        BossEffectSet set = new BossEffectSet();
        set.get(0).setEnabled(true);
        set.get(0).setEffectId(BossEffectData.FIRE_ID);
        set.get(0).setLevel(10);
        set.get(1).setEnabled(true);
        set.get(1).setEffectId("minecraft:slowness");

        ListTag once = set.writeToNBT();
        BossEffectSet reread = new BossEffectSet();
        reread.readFromNBT(once);
        assertEquals(once, reread.writeToNBT());
        assertEquals(BossEffectData.FIRE_ID, reread.get(0).getEffectId());
        assertEquals(10, reread.get(0).getLevel());
        assertEquals("minecraft:slowness", reread.get(1).getEffectId());
        assertFalse(reread.get(2).isEnabled());
    }
}

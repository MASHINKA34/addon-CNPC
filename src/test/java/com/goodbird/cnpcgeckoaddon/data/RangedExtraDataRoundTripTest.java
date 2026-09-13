package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The save round trip of the ranged extras, and the promise that an npc saved before the shot
 * was tunable fires from where it always did, at the volume it always did.
 *
 * <p>{@link NpcDataFieldRangeTest} holds every number here to its range and sees nothing else;
 * the muzzle offset is the one field of this class that is allowed to be negative, which is
 * exactly the shape of setting a careless read turns into a shot out of the npc's feet.</p>
 */
class RangedExtraDataRoundTripTest {

    @Test
    @DisplayName("every ranged extra reaches the save tag")
    void everyFieldIsPersisted() throws IllegalAccessException {
        Set<String> silent = new TreeSet<>();
        int checked = 0;
        for (Field field : RangedExtraData.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            List<Object> candidates = candidatesFor(field.getType());
            assertFalse(candidates.isEmpty(), "no probe values for " + field.getName());
            checked++;
            field.setAccessible(true);
            CompoundTag baseline = new RangedExtraData().writeToNBT(new CompoundTag());
            boolean perturbs = false;
            for (Object candidate : candidates) {
                RangedExtraData data = new RangedExtraData();
                if (candidate.equals(field.get(data))) {
                    continue;
                }
                field.set(data, candidate);
                if (!baseline.equals(data.writeToNBT(new CompoundTag()))) {
                    perturbs = true;
                    break;
                }
            }
            if (!perturbs) {
                silent.add(field.getName());
            }
        }
        assertTrue(checked > 0, "no fields of RangedExtraData were examined");
        assertTrue(silent.isEmpty(),
                "these fields change nothing in the saved tag, so their value is lost on the next "
                        + "load - each needs a line in writeToNBT and readFromNBT: " + silent);
    }

    @Test
    @DisplayName("the shot settings survive write -> read -> write unchanged")
    void shotSettingsSurviveTheRoundTrip() {
        RangedExtraData first = new RangedExtraData();
        first.setKeepDistance(9);
        first.setMuzzleHeightTenths(10);
        first.setShotSoundVolumeTenths(0);
        first.setShotSoundPitchTenths(25);

        CompoundTag once = first.writeToNBT(new CompoundTag());
        RangedExtraData reread = new RangedExtraData();
        reread.readFromNBT(once);

        assertEquals(once, reread.writeToNBT(new CompoundTag()),
                "write -> read -> write should reproduce the identical ranged tag");
        assertEquals(9, reread.getKeepDistance());
        assertEquals(10, reread.getMuzzleHeightTenths());
        assertEquals(0, reread.getShotSoundVolumeTenths());
        assertEquals(25, reread.getShotSoundPitchTenths());
    }

    @Test
    @DisplayName("an npc saved before the shot was tunable reads the old muzzle and volume")
    void aTagWithoutTheNewKeysReadsTheDefaults() {
        CompoundTag old = new CompoundTag();
        // What a ranged tag of that age does carry, so the read is not simply of an empty tag.
        old.putString("GeckoProjectileEntity", "minecraft:arrow");
        old.putInt("GeckoKeepDistance", 12);

        RangedExtraData reread = new RangedExtraData();
        reread.setMuzzleHeightTenths(15);
        reread.setShotSoundVolumeTenths(0);
        reread.setShotSoundPitchTenths(30);
        reread.readFromNBT(old);

        assertEquals("minecraft:arrow", reread.getProjectileEntity());
        assertEquals(12, reread.getKeepDistance());
        assertEquals(RangedExtraData.DEFAULT_MUZZLE_HEIGHT, reread.getMuzzleHeightTenths(),
                "the muzzle used to sit two tenths under the eyes");
        assertEquals(RangedExtraData.DEFAULT_SHOT_VOLUME, reread.getShotSoundVolumeTenths());
        assertEquals(RangedExtraData.DEFAULT_SHOT_PITCH, reread.getShotSoundPitchTenths());
    }

    private static List<Object> candidatesFor(Class<?> type) {
        if (type == int.class) {
            return List.of(17, 7, 1, 0, -7);
        }
        if (type == String.class) {
            return List.of("minecraft:arrow", "");
        }
        return List.of();
    }
}

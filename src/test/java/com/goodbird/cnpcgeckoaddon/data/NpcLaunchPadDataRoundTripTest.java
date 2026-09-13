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
 * The save round trip of the launch pad settings, and the promise that an npc saved before
 * pads existed is not one.
 *
 * <p>{@link NpcDataFieldRangeTest} holds the numbers to their ranges; a boolean, or a field that
 * never reaches the tag, is invisible to it - and two of the booleans here default to on, which
 * is exactly the kind of field a careless read turns off on every existing pad.</p>
 */
class NpcLaunchPadDataRoundTripTest {

    @Test
    @DisplayName("every launch pad field reaches the save tag")
    void everyFieldIsPersisted() throws IllegalAccessException {
        Set<String> silent = new TreeSet<>();
        int checked = 0;
        for (Field field : NpcLaunchPadData.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            List<Object> candidates = candidatesFor(field.getType());
            assertFalse(candidates.isEmpty(), "no probe values for " + field.getName());
            checked++;
            field.setAccessible(true);
            CompoundTag baseline = new NpcLaunchPadData().writeToNBT(new CompoundTag());
            boolean perturbs = false;
            for (Object candidate : candidates) {
                NpcLaunchPadData data = new NpcLaunchPadData();
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
        assertTrue(checked > 0, "no fields of NpcLaunchPadData were examined");
        assertTrue(silent.isEmpty(),
                "these fields change nothing in the saved tag, so their value is lost on the next "
                        + "load - each needs a line in writeToNBT and readFromNBT: " + silent);
    }

    @Test
    @DisplayName("the pad settings survive write -> read -> write unchanged")
    void settingsSurviveTheRoundTrip() {
        NpcLaunchPadData first = new NpcLaunchPadData();
        first.setEnabled(true);
        first.setCoordinateMode(NpcLaunchPadData.COORDINATE_ABSOLUTE);
        first.setPosition(-120, 64, 3500);
        first.setArcHeight(12);
        first.setCooldownTicks(45);
        first.setNoFallDamage(false);
        first.setSound(false);
        first.setLifetimeTicks(200);

        CompoundTag once = first.writeToNBT(new CompoundTag());
        NpcLaunchPadData reread = new NpcLaunchPadData();
        reread.readFromNBT(once);

        assertEquals(once, reread.writeToNBT(new CompoundTag()),
                "write -> read -> write should reproduce the identical pad tag");
        assertTrue(reread.isEnabled());
        assertEquals(NpcLaunchPadData.COORDINATE_ABSOLUTE, reread.getCoordinateMode());
        assertEquals(-120, reread.getX());
        assertEquals(64, reread.getY());
        assertEquals(3500, reread.getZ());
        assertEquals(12, reread.getArcHeight());
        assertEquals(45, reread.getCooldownTicks());
        assertFalse(reread.isNoFallDamage(), "a pad saved with the landing protection off must keep it off");
        assertFalse(reread.isSound(), "a pad saved silent must stay silent");
        assertEquals(200, reread.getLifetimeTicks());
    }

    @Test
    @DisplayName("an npc saved before launch pads existed is not a pad and reads the defaults")
    void aTagWithoutPadKeysReadsAsDisabled() {
        CompoundTag old = new CompoundTag();
        // What an ai tag of that age does carry, so the read is not simply of an empty tag.
        new NpcCarryData().writeToNBT(old);

        NpcLaunchPadData reread = new NpcLaunchPadData();
        reread.setEnabled(true);
        reread.setNoFallDamage(false);
        reread.setSound(false);
        reread.readFromNBT(old);

        assertFalse(reread.isEnabled(), "a tag with no pad keys must not make the npc a launch pad");
        assertEquals(NpcLaunchPadData.COORDINATE_NPC_OFFSET, reread.getCoordinateMode());
        assertEquals(0, reread.getX());
        assertEquals(0, reread.getY());
        assertEquals(0, reread.getZ());
        assertEquals(NpcLaunchPadData.DEFAULT_ARC_HEIGHT, reread.getArcHeight());
        assertEquals(NpcLaunchPadData.DEFAULT_COOLDOWN_TICKS, reread.getCooldownTicks());
        assertTrue(reread.isNoFallDamage(), "the landing protection defaults to on");
        assertTrue(reread.isSound(), "the sound defaults to on");
        assertEquals(0, reread.getLifetimeTicks());
    }

    private static List<Object> candidatesFor(Class<?> type) {
        if (type == boolean.class) {
            return List.of(Boolean.TRUE, Boolean.FALSE);
        }
        if (type == int.class) {
            return List.of(37, 3, 1, 0);
        }
        return List.of();
    }
}

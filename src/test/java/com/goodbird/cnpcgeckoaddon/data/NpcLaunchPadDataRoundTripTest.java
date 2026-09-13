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
            if (Modifier.isStatic(field.getModifiers()) || isCue(field)) {
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

    /**
     * The same sweep over the pad's cues, which the one above cannot see.
     *
     * <p>A cue is a final field writing its own keys under a prefix, so every field of it is
     * invisible to a sweep over the pad's own fields: a cue whose count never reached the tag
     * would read back as twelve motes on every reload with nothing to say so.</p>
     */
    @Test
    @DisplayName("every field of every pad cue reaches the save tag")
    void everyCueFieldIsPersisted() throws IllegalAccessException {
        Set<String> silent = new TreeSet<>();
        int checked = 0;
        for (Field owner : NpcLaunchPadData.class.getDeclaredFields()) {
            if (!isCue(owner)) {
                continue;
            }
            owner.setAccessible(true);
            for (Field field : owner.getType().getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
                    continue;
                }
                List<Object> candidates = candidatesFor(field.getType());
                if (candidates.isEmpty()) {
                    continue;
                }
                checked++;
                field.setAccessible(true);
                CompoundTag baseline = new NpcLaunchPadData().writeToNBT(new CompoundTag());
                boolean perturbs = false;
                for (Object candidate : candidates) {
                    NpcLaunchPadData data = new NpcLaunchPadData();
                    Object cue = owner.get(data);
                    if (candidate.equals(field.get(cue))) {
                        continue;
                    }
                    field.set(cue, candidate);
                    if (!baseline.equals(data.writeToNBT(new CompoundTag()))) {
                        perturbs = true;
                        break;
                    }
                }
                if (!perturbs) {
                    silent.add(owner.getName() + "." + field.getName());
                }
            }
        }
        assertTrue(checked > 0, "no cue fields of NpcLaunchPadData were examined");
        assertTrue(silent.isEmpty(),
                "these cue fields change nothing in the saved tag, so their value is lost on the "
                        + "next load: " + silent);
    }

    private static boolean isCue(Field field) {
        return field.getType() == BossSoundCue.class || field.getType() == BossParticleCue.class;
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
        first.setTouchMarginTenths(14);
        first.setLandingGraceTicks(0);
        first.getLaunchSound().setSoundId("minecraft:entity.slime.jump");
        first.getLaunchParticles().setCount(0);
        first.getExpireParticles().setEnabled(false);

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
        assertEquals(14, reread.getTouchMarginTenths());
        assertEquals(0, reread.getLandingGraceTicks());
        assertEquals("minecraft:entity.slime.jump", reread.getLaunchSound().getSoundId());
        assertEquals(0, reread.getLaunchParticles().getCount());
        assertFalse(reread.getExpireParticles().isEnabled());
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
        reread.setTouchMarginTenths(0);
        reread.setLandingGraceTicks(0);
        reread.getLaunchParticles().setCount(0);
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
        assertEquals(NpcLaunchPadData.DEFAULT_TOUCH_MARGIN_TENTHS, reread.getTouchMarginTenths());
        assertEquals(NpcLaunchPadData.DEFAULT_LANDING_GRACE_TICKS, reread.getLandingGraceTicks());
        assertEquals("minecraft:block.slime_block.fall", reread.getLaunchSound().getSoundId());
        assertEquals(12, reread.getLaunchParticles().getCount());
        assertEquals(8, reread.getExpireParticles().getCount());
    }

    private static List<Object> candidatesFor(Class<?> type) {
        if (type == boolean.class) {
            return List.of(Boolean.TRUE, Boolean.FALSE);
        }
        if (type == int.class) {
            return List.of(37, 3, 1, 0);
        }
        if (type == String.class) {
            return List.of("minecraft:note", "");
        }
        return List.of();
    }
}

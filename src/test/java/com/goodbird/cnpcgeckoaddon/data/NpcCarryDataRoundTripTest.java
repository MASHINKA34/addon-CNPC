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
 * The save round trip of the carry settings, and the promise that an npc saved before the
 * throw existed carries exactly the way it did.
 *
 * <p>{@link NpcDataFieldRangeTest} holds every number here to its range, but a boolean or a
 * field that never reaches the tag is invisible to it; and the boss sweep in
 * {@link BossFieldPersistenceTest} cannot reach a class that lives on a plain npc. So each
 * field of this one is perturbed on its own here, the way the boss fields are there.</p>
 */
class NpcCarryDataRoundTripTest {

    @Test
    @DisplayName("every carry field reaches the save tag")
    void everyFieldIsPersisted() throws IllegalAccessException {
        Set<String> silent = new TreeSet<>();
        int checked = 0;
        for (Field field : NpcCarryData.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            List<Object> candidates = candidatesFor(field.getType());
            assertFalse(candidates.isEmpty(), "no probe values for " + field.getName());
            checked++;
            field.setAccessible(true);
            CompoundTag baseline = new NpcCarryData().writeToNBT(new CompoundTag());
            boolean perturbs = false;
            for (Object candidate : candidates) {
                NpcCarryData data = new NpcCarryData();
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
        assertTrue(checked > 0, "no fields of NpcCarryData were examined");
        assertTrue(silent.isEmpty(),
                "these fields change nothing in the saved tag, so their value is lost on the next "
                        + "load - each needs a line in writeToNBT and readFromNBT: " + silent);
    }

    @Test
    @DisplayName("the throw settings survive write -> read -> write unchanged")
    void throwSettingsSurviveTheRoundTrip() {
        NpcCarryData first = new NpcCarryData();
        first.setCarryable(true);
        first.setThrowable(true);
        first.setThrowSpeed(25);
        first.setThrowDamage(70);
        first.setThrowKnockback(6);
        first.setThrowSelfDamage(15);
        first.setThrowDiesOnImpact(true);
        first.setThrowCooldownTicks(90);

        CompoundTag once = first.writeToNBT(new CompoundTag());
        NpcCarryData reread = new NpcCarryData();
        reread.readFromNBT(once);

        assertEquals(once, reread.writeToNBT(new CompoundTag()),
                "write -> read -> write should reproduce the identical carry tag");
        assertTrue(reread.isThrowable());
        assertEquals(25, reread.getThrowSpeed());
        assertEquals(70, reread.getThrowDamage());
        assertEquals(6, reread.getThrowKnockback());
        assertEquals(15, reread.getThrowSelfDamage());
        assertTrue(reread.isThrowDiesOnImpact());
        assertEquals(90, reread.getThrowCooldownTicks());
    }

    @Test
    @DisplayName("an npc saved before the throw existed cannot be thrown and carries as before")
    void aCarryTagWithoutThrowKeysReadsAsNotThrowable() {
        NpcCarryData saved = new NpcCarryData();
        saved.setCarryable(true);
        saved.setRequireSneak(false);
        saved.setSlownessPercent(45);
        saved.setLeashRadius(12);
        CompoundTag tag = saved.writeToNBT(new CompoundTag());
        for (String key : List.copyOf(tag.getAllKeys())) {
            if (key.contains("Throw")) {
                tag.remove(key);
            }
        }

        NpcCarryData reread = new NpcCarryData();
        reread.readFromNBT(tag);
        assertFalse(reread.isThrowable(), "a tag with no throw keys must not make the npc throwable");
        assertEquals(NpcCarryData.DEFAULT_THROW_SPEED, reread.getThrowSpeed());
        assertEquals(NpcCarryData.DEFAULT_THROW_DAMAGE, reread.getThrowDamage());
        assertEquals(NpcCarryData.DEFAULT_THROW_KNOCKBACK, reread.getThrowKnockback());
        assertEquals(0, reread.getThrowSelfDamage());
        assertFalse(reread.isThrowDiesOnImpact());
        assertEquals(NpcCarryData.DEFAULT_THROW_COOLDOWN_TICKS, reread.getThrowCooldownTicks());
        assertTrue(reread.isCarryable(), "the old carry settings must read back untouched");
        assertFalse(reread.isRequireSneak());
        assertEquals(45, reread.getSlownessPercent());
        assertEquals(12, reread.getLeashRadius());
    }

    private static List<Object> candidatesFor(Class<?> type) {
        if (type == boolean.class) {
            return List.of(Boolean.TRUE, Boolean.FALSE);
        }
        if (type == int.class) {
            return List.of(37, 3, 1, 0);
        }
        if (type == String.class) {
            return List.of("minecraft:torch", "");
        }
        return List.of();
    }
}

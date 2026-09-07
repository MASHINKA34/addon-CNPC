package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Asserts that every settings field of the boss configuration actually reaches the save file.
 *
 * <p>{@link BossPhaseData} carries three hundred odd fields, and each one needs a matching
 * line in both {@code writeToNBT} and {@code readFromNBT}. A field added to only two of the
 * three places compiles, saves nothing, and is noticed by a server owner weeks later when
 * their boss quietly forgets a setting. So each field is perturbed on its own here and the
 * written tag has to change because of it.</p>
 */
class BossFieldPersistenceTest {

    /**
     * Fields that deliberately never reach the tag. Every entry is a decision, not a hole,
     * so the list is spelled out rather than pattern matched.
     */
    private static final Set<String> NOT_PERSISTED = Set.of(
            // Derived on load rather than stored: readFromNBT sets it from whether the tag
            // enables the boss or holds any non-default key, and it is what decides whether
            // the block is written at all. A key of its own would be circular.
            "TeleportPathData.configured");

    @Test
    @DisplayName("every phase field reaches the save tag")
    void everyPhaseFieldIsPersisted() {
        assertPersisted(BossPhaseData.class, BossFieldPersistenceTest::configuredHost,
                data -> data.getPhase(1));
    }

    @Test
    @DisplayName("every boss-wide field reaches the save tag")
    void everyBossFieldIsPersisted() {
        assertPersisted(TeleportPathData.class, BossFieldPersistenceTest::configuredHost,
                data -> data);
    }

    private static TeleportPathData configuredHost() {
        TeleportPathData data = new TeleportPathData();
        data.setEnabled(true);
        data.markConfigured();
        return data;
    }

    private static <T> void assertPersisted(Class<T> owned, Supplier<TeleportPathData> host,
                                            Function<TeleportPathData, T> target) {
        Set<String> silent = new TreeSet<>();
        int checked = 0;
        for (Field field : owned.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
                continue;
            }
            if (NOT_PERSISTED.contains(owned.getSimpleName() + "." + field.getName())) {
                continue;
            }
            List<Object> candidates = candidatesFor(field.getType());
            if (candidates.isEmpty()) {
                continue;
            }
            checked++;
            field.setAccessible(true);
            CompoundTag baseline = host.get().writeToNBT(new CompoundTag());
            if (!perturbs(field, candidates, host, target, baseline)) {
                silent.add(owned.getSimpleName() + "." + field.getName());
            }
        }
        assertTrue(checked > 0, "no fields of " + owned.getSimpleName() + " were examined");
        assertTrue(silent.isEmpty(),
                "these fields change nothing in the saved tag, so their value is lost on the next "
                        + "load - each needs a line in writeToNBT and readFromNBT: " + silent);
    }

    private static <T> boolean perturbs(Field field, List<Object> candidates,
                                        Supplier<TeleportPathData> host,
                                        Function<TeleportPathData, T> target,
                                        CompoundTag baseline) {
        for (Object candidate : candidates) {
            TeleportPathData data = host.get();
            T owner = target.apply(data);
            try {
                if (candidate.equals(field.get(owner))) {
                    continue;
                }
                field.set(owner, candidate);
            } catch (IllegalAccessException error) {
                throw new AssertionError("could not reach " + field.getName(), error);
            }
            if (!baseline.equals(data.writeToNBT(new CompoundTag()))) {
                return true;
            }
        }
        return false;
    }

    private static List<Object> candidatesFor(Class<?> type) {
        if (type == boolean.class) {
            return List.of(Boolean.TRUE, Boolean.FALSE);
        }
        if (type == int.class) {
            return List.of(37, 3, 1, 0, -7);
        }
        if (type == long.class) {
            return List.of(37L, 3L, 1L, 0L);
        }
        if (type == double.class) {
            return List.of(37.5D, 3.25D, 1.0D, 0.0D);
        }
        if (type == float.class) {
            return List.of(37.5F, 3.25F, 1.0F, 0.0F);
        }
        if (type == String.class) {
            return List.of("cnpcgeckoaddon:probe", "probe", "minecraft:deepslate", "");
        }
        // Nested settings objects round trip through their own tests.
        return List.of();
    }
}

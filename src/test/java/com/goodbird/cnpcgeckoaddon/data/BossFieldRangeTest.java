package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Asserts that a number arriving from a save file is put inside a sane range before it is used.
 *
 * <p>The boss configuration is read back from a tag that has been on disk, through a world
 * downgrade, or through somebody's editor. A cooldown of two billion ticks is a boss that
 * never attacks again; a radius of two billion blocks is a scan that hangs the server tick.
 * Neither shows up as an exception, so a field read straight out of the tag with no clamp is
 * a hole that only turns up on somebody's server.</p>
 *
 * <p>Written against the tag rather than against the field list on purpose: it walks
 * whatever {@code writeToNBT} produced, so a setting added later is covered by existing
 * rather than by being remembered here.</p>
 */
class BossFieldRangeTest {

    /**
     * The world border, which is as large as any setting is ever allowed to be: the widest
     * of them are block coordinates, and a build can sit anywhere inside it.
     */
    private static final double WORLD_LIMIT = 30_000_000.0D;

    /**
     * The ceiling for everything that is a duration, a distance or a count rather than a
     * position. A million ticks is fourteen hours and a million blocks is past the border,
     * so anything above this is a field nobody put a range on.
     */
    private static final double MAGNITUDE_LIMIT = 1_000_000.0D;

    /** Suffixes that mark a key as one of those magnitudes rather than a world position. */
    private static final List<String> MAGNITUDE_SUFFIXES = List.of(
            "Ticks", "Percent", "Radius", "Range", "Count", "Damage", "Knockback",
            "Amplifier", "Duration", "Strength", "Speed", "Height", "Width", "Length");

    /**
     * Keys that are free to hold any number, each for a stated reason. Everything else has
     * to come back clamped.
     */
    private static final Set<String> UNBOUNDED = Set.of(
            // Bit masks, not magnitudes: every bit past the ability count is simply never read.
            "GeckoBossTelegraphAbilities", "GeckoNpcImmunityAbilities", "TelegraphAbilities",
            "CastRootMask");

    @Test
    @DisplayName("every number in a boss save is clamped on the way back in")
    void everyNumericSettingIsClamped() {
        CompoundTag baseline = configuredHost().writeToNBT(new CompoundTag());
        List<String> paths = new ArrayList<>();
        collectNumericPaths(baseline, "", paths);
        assertTrue(paths.size() > 100,
                "the sweep found only " + paths.size() + " numbers, so it is not reading the save");

        Set<String> unclamped = new TreeSet<>();
        for (String path : paths) {
            String key = path.substring(path.lastIndexOf('/') + 1);
            if (UNBOUNDED.contains(key)) {
                continue;
            }
            for (double extreme : new double[]{Integer.MAX_VALUE, Integer.MIN_VALUE}) {
                CompoundTag poisoned = baseline.copy();
                if (!writeAt(poisoned, path, extreme)) {
                    continue;
                }
                TeleportPathData reloaded = new TeleportPathData();
                reloaded.readFromNBT(poisoned);
                CompoundTag round = reloaded.writeToNBT(new CompoundTag());
                Double back = readAt(round, path);
                if (back != null && Math.abs(back) > limitFor(key)) {
                    unclamped.add(path + " kept " + back);
                }
            }
        }
        assertTrue(unclamped.isEmpty(),
                "these settings take whatever the save file says, unclamped: " + unclamped);
    }

    @Test
    @DisplayName("a save full of extremes still round-trips instead of throwing")
    void anEntirelyPoisonedSaveStillLoads() {
        CompoundTag baseline = configuredHost().writeToNBT(new CompoundTag());
        List<String> paths = new ArrayList<>();
        collectNumericPaths(baseline, "", paths);
        CompoundTag poisoned = baseline.copy();
        for (String path : paths) {
            writeAt(poisoned, path, Integer.MIN_VALUE);
        }
        TeleportPathData reloaded = new TeleportPathData();
        reloaded.readFromNBT(poisoned);
        // Writing it back out is what walks every list and sub-object the load produced.
        CompoundTag round = reloaded.writeToNBT(new CompoundTag());
        assertTrue(round.size() > 0);
        assertTrue(reloaded.getPhaseCount() >= 1,
                "a poisoned save left the boss with no phase to fight in");
    }

    /**
     * How large this key is allowed to come back. A position gets the world; a duration, a
     * distance or a count gets the much tighter magnitude ceiling.
     */
    private static double limitFor(String key) {
        for (String suffix : MAGNITUDE_SUFFIXES) {
            if (key.endsWith(suffix)) {
                return MAGNITUDE_LIMIT;
            }
        }
        return WORLD_LIMIT;
    }

    private static TeleportPathData configuredHost() {
        TeleportPathData data = new TeleportPathData();
        data.setEnabled(true);
        data.markConfigured();
        return data;
    }

    private static void collectNumericPaths(CompoundTag tag, String prefix, List<String> out) {
        for (String key : tag.getAllKeys()) {
            Tag value = tag.get(key);
            String path = prefix + key;
            if (value instanceof CompoundTag compound) {
                collectNumericPaths(compound, path + "/", out);
            } else if (value instanceof ListTag list) {
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i) instanceof CompoundTag element) {
                        collectNumericPaths(element, path + "[" + i + "]/", out);
                    }
                }
            } else if (value instanceof IntTag || value instanceof FloatTag || value instanceof DoubleTag) {
                // Bytes and shorts are booleans and enum ordinals here, both of which are
                // read through their own guards rather than as magnitudes.
                out.add(path);
            }
        }
    }

    private static boolean writeAt(CompoundTag root, String path, double value) {
        String[] steps = path.split("/");
        CompoundTag current = root;
        for (int i = 0; i < steps.length - 1; i++) {
            current = descend(current, steps[i]);
            if (current == null) {
                return false;
            }
        }
        String key = steps[steps.length - 1];
        Tag existing = current.get(key);
        if (existing instanceof IntTag) {
            current.putInt(key, (int) value);
        } else if (existing instanceof FloatTag) {
            current.putFloat(key, (float) value);
        } else if (existing instanceof DoubleTag) {
            current.putDouble(key, value);
        } else {
            return false;
        }
        return true;
    }

    private static Double readAt(CompoundTag root, String path) {
        String[] steps = path.split("/");
        CompoundTag current = root;
        for (int i = 0; i < steps.length - 1; i++) {
            current = descend(current, steps[i]);
            if (current == null) {
                return null;
            }
        }
        Tag value = current.get(steps[steps.length - 1]);
        return value instanceof NumericTag numeric ? numeric.getAsDouble() : null;
    }

    /** One step of a path, which is either a plain key or {@code key[index]} inside a list. */
    private static CompoundTag descend(CompoundTag tag, String step) {
        int bracket = step.indexOf('[');
        if (bracket < 0) {
            return tag.get(step) instanceof CompoundTag compound ? compound : null;
        }
        String key = step.substring(0, bracket);
        int index = Integer.parseInt(step.substring(bracket + 1, step.length() - 1));
        if (!(tag.get(key) instanceof ListTag list) || index >= list.size()) {
            return null;
        }
        return list.get(index) instanceof CompoundTag compound ? compound : null;
    }
}

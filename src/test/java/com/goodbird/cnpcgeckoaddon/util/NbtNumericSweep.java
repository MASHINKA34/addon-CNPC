package com.goodbird.cnpcgeckoaddon.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * Walks a saved tag and pokes numbers into it, for the tests that ask whether a setting
 * arrives back inside a sane range.
 *
 * <p>Shared rather than copied because two of them ask the same question of two different
 * save formats - the boss configuration and the settings that live on a plain npc - and a
 * walk that had drifted between them would be a hole in exactly one of the two.</p>
 */
public final class NbtNumericSweep {

    private NbtNumericSweep() {
    }

    /**
     * Every path in the tag that leads to a number, as {@code key}, {@code key/inner} or
     * {@code key[0]/inner}.
     *
     * <p>Bytes and shorts are left out: they are booleans and enum ordinals here, and both
     * are read through their own guards rather than as magnitudes.</p>
     */
    public static List<String> numericPaths(CompoundTag tag) {
        List<String> paths = new ArrayList<>();
        collect(tag, "", paths);
        return paths;
    }

    private static void collect(CompoundTag tag, String prefix, List<String> out) {
        for (String key : tag.getAllKeys()) {
            Tag value = tag.get(key);
            String path = prefix + key;
            if (value instanceof CompoundTag compound) {
                collect(compound, path + "/", out);
            } else if (value instanceof ListTag list) {
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i) instanceof CompoundTag element) {
                        collect(element, path + "[" + i + "]/", out);
                    }
                }
            } else if (value instanceof IntTag || value instanceof FloatTag || value instanceof DoubleTag) {
                out.add(path);
            }
        }
    }

    /** The last step of a path, which is the key the value is actually stored under. */
    public static String keyOf(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /**
     * Overwrites the number at {@code path}, keeping the tag type that is already there.
     *
     * @return false when the path no longer leads to a number, which a shorter list on a
     *         reloaded tag can do
     */
    public static boolean writeAt(CompoundTag root, String path, double value) {
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

    /** Whether the value at {@code path} is stored as a float, which is what may hold a NaN. */
    public static boolean isFloatingPoint(CompoundTag root, String path) {
        String[] steps = path.split("/");
        CompoundTag current = root;
        for (int i = 0; i < steps.length - 1; i++) {
            current = descend(current, steps[i]);
            if (current == null) {
                return false;
            }
        }
        Tag value = current.get(steps[steps.length - 1]);
        return value instanceof FloatTag || value instanceof DoubleTag;
    }

    /** @return the number at {@code path}, or null when nothing numeric is there any more */
    public static Double readAt(CompoundTag root, String path) {
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

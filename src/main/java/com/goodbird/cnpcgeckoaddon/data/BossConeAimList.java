package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The points a cone strike sweeps, in the order the builder listed them: a growable, bounded
 * list whose point ids survive row deletion, the way a summon's point list is kept.
 */
public final class BossConeAimList {
    /** One cone a point; past this a series outlasts any cooldown worth giving it. */
    public static final int MAX_ENTRIES = 16;

    private final List<BossConeAimPoint> entries = new ArrayList<>();
    private int nextPointId = 1;

    /** @return the new point, or null when the list is already full */
    public BossConeAimPoint add() {
        if (entries.size() >= MAX_ENTRIES) {
            return null;
        }
        BossConeAimPoint point = new BossConeAimPoint(allocatePointId());
        entries.add(point);
        return point;
    }

    public int size() { return entries.size(); }

    public BossConeAimPoint get(int index) { return entries.get(index); }

    public List<BossConeAimPoint> entries() { return Collections.unmodifiableList(entries); }

    public BossConeAimPoint remove(int index) { return entries.remove(index); }

    public void clear() { entries.clear(); }

    /** Whether any point is switched on, which is what a cast aimed at points needs at least one of. */
    public boolean hasEnabled() {
        for (BossConeAimPoint point : entries) {
            if (point.isEnabled()) {
                return true;
            }
        }
        return false;
    }

    public ListTag writeToNBT() {
        ListTag list = new ListTag();
        for (BossConeAimPoint point : entries) {
            list.add(point.writeToNBT());
        }
        return list;
    }

    public void readFromNBT(ListTag list) {
        entries.clear();
        nextPointId = 1;
        Set<Integer> used = new HashSet<>();
        for (int i = 0; i < list.size() && entries.size() < MAX_ENTRIES; i++) {
            BossConeAimPoint point = BossConeAimPoint.readFromNBT(list.getCompound(i), nextPointId);
            if (point.getPointId() <= 0 || !used.add(point.getPointId())) {
                point.assignPointId(firstFreePointId(used, 1));
                used.add(point.getPointId());
            }
            entries.add(point);
            nextPointId = Math.max(nextPointId, point.getPointId() + 1);
        }
    }

    public void readFromNBT(CompoundTag parent, String key) {
        readFromNBT(parent.contains(key, Tag.TAG_LIST)
                ? parent.getList(key, Tag.TAG_COMPOUND) : new ListTag());
    }

    private int allocatePointId() {
        Set<Integer> used = new HashSet<>();
        for (BossConeAimPoint point : entries) {
            used.add(point.getPointId());
        }
        int result = firstFreePointId(used, nextPointId);
        nextPointId = result == Integer.MAX_VALUE ? 1 : result + 1;
        return result;
    }

    private static int firstFreePointId(Set<Integer> used, int start) {
        int candidate = Math.max(1, start);
        while (candidate < Integer.MAX_VALUE && used.contains(candidate)) {
            candidate++;
        }
        if (!used.contains(candidate)) {
            return candidate;
        }
        candidate = 1;
        while (used.contains(candidate)) {
            candidate++;
        }
        return candidate;
    }
}

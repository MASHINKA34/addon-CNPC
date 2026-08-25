package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Growable, bounded list whose point ids survive row deletion and reordering. */
public final class BossMinionSpawnList {
    public static final int MAX_ENTRIES = 32;

    private final List<BossMinionSpawnPoint> entries = new ArrayList<>();
    private int nextPointId = 1;

    public BossMinionSpawnPoint add() {
        if (entries.size() >= MAX_ENTRIES) {
            return null;
        }
        BossMinionSpawnPoint point = new BossMinionSpawnPoint(allocatePointId());
        entries.add(point);
        return point;
    }

    public int size() { return entries.size(); }
    public BossMinionSpawnPoint get(int index) { return entries.get(index); }
    public List<BossMinionSpawnPoint> entries() { return Collections.unmodifiableList(entries); }
    public BossMinionSpawnPoint remove(int index) { return entries.remove(index); }
    public void clear() { entries.clear(); }

    public ListTag writeToNBT() {
        ListTag list = new ListTag();
        for (BossMinionSpawnPoint point : entries) {
            list.add(point.writeToNBT());
        }
        return list;
    }

    public void readFromNBT(ListTag list) {
        entries.clear();
        nextPointId = 1;
        Set<Integer> used = new HashSet<>();
        for (int i = 0; i < list.size() && entries.size() < MAX_ENTRIES; i++) {
            BossMinionSpawnPoint point = BossMinionSpawnPoint.readFromNBT(list.getCompound(i), nextPointId);
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

    public boolean hasUsableClone(String phaseCloneName) {
        for (BossMinionSpawnPoint point : entries) {
            if (point.isEnabled()
                    && (!point.getCloneNameOverride().isEmpty() || !phaseCloneName.isEmpty())) {
                return true;
            }
        }
        return false;
    }

    private int allocatePointId() {
        Set<Integer> used = new HashSet<>();
        for (BossMinionSpawnPoint point : entries) {
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

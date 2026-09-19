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
 * The zones one rift stands its crystals in, in the order the builder listed them: a growable,
 * bounded list whose ids survive row deletion, kept the way the platforms' zones are.
 *
 * <p>An empty list is not an empty rift: the crystals then stand on a ring round the middle of
 * the platform, as many as the phase asks for. The list is for a builder who wants them
 * somewhere in particular - in the corners of a hand-built arena, say - and its ids are what a
 * crystal on a zone is remembered by, so deleting one row does not move the others' crystals.</p>
 */
public final class BossRiftCrystalList {
    /** As many zones as a ring may hold crystals: past that the platform is more crystal than floor. */
    public static final int MAX_ENTRIES = BossRiftSettings.MAX_CRYSTAL_COUNT;

    private final List<BossRiftCrystalPoint> entries = new ArrayList<>();
    private int nextPointId = 1;

    /** @return the new zone, or null when the list is already full */
    public BossRiftCrystalPoint add() {
        if (entries.size() >= MAX_ENTRIES) {
            return null;
        }
        BossRiftCrystalPoint point = new BossRiftCrystalPoint(allocatePointId());
        entries.add(point);
        return point;
    }

    public int size() { return entries.size(); }

    public BossRiftCrystalPoint get(int index) { return entries.get(index); }

    public List<BossRiftCrystalPoint> entries() { return Collections.unmodifiableList(entries); }

    public BossRiftCrystalPoint remove(int index) { return entries.remove(index); }

    public void clear() { entries.clear(); }

    /** Whether any zone is switched on, which is what makes the list the rift's plan rather than the ring. */
    public boolean hasEnabled() {
        for (BossRiftCrystalPoint point : entries) {
            if (point.isEnabled()) {
                return true;
            }
        }
        return false;
    }

    /** Every zone that is switched on, in list order. */
    public List<BossRiftCrystalPoint> enabled() {
        List<BossRiftCrystalPoint> on = new ArrayList<>();
        for (BossRiftCrystalPoint point : entries) {
            if (point.isEnabled()) {
                on.add(point);
            }
        }
        return on;
    }

    public ListTag writeToNBT() {
        ListTag list = new ListTag();
        for (BossRiftCrystalPoint point : entries) {
            list.add(point.writeToNBT());
        }
        return list;
    }

    public void readFromNBT(ListTag list) {
        entries.clear();
        nextPointId = 1;
        Set<Integer> used = new HashSet<>();
        for (int i = 0; i < list.size() && entries.size() < MAX_ENTRIES; i++) {
            BossRiftCrystalPoint point = BossRiftCrystalPoint.readFromNBT(list.getCompound(i), nextPointId);
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
        for (BossRiftCrystalPoint point : entries) {
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

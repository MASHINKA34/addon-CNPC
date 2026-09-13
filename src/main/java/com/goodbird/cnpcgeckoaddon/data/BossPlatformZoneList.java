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
 * The platforms one phase can set alight, in the order the builder listed them: a growable,
 * bounded list whose zone ids survive row deletion, the way a summon's point list is kept.
 *
 * <p>The id is what a platform taken in turn is remembered by, so deleting or switching off
 * the one that burned last moves the turn on to the next rather than back to the top.</p>
 */
public final class BossPlatformZoneList {
    /** Past this a fuse would be lit on half the arena, and the outlines alone are a flood of dust. */
    public static final int MAX_ENTRIES = 16;

    private final List<BossPlatformZone> entries = new ArrayList<>();
    private int nextZoneId = 1;

    /** @return the new platform, or null when the list is already full */
    public BossPlatformZone add() {
        if (entries.size() >= MAX_ENTRIES) {
            return null;
        }
        BossPlatformZone zone = new BossPlatformZone(allocateZoneId());
        entries.add(zone);
        return zone;
    }

    public int size() { return entries.size(); }

    public BossPlatformZone get(int index) { return entries.get(index); }

    public List<BossPlatformZone> entries() { return Collections.unmodifiableList(entries); }

    public BossPlatformZone remove(int index) { return entries.remove(index); }

    public void clear() { entries.clear(); }

    /** Whether any platform is switched on, which is what a cast needs at least one of. */
    public boolean hasEnabled() {
        for (BossPlatformZone zone : entries) {
            if (zone.isEnabled()) {
                return true;
            }
        }
        return false;
    }

    public ListTag writeToNBT() {
        ListTag list = new ListTag();
        for (BossPlatformZone zone : entries) {
            list.add(zone.writeToNBT());
        }
        return list;
    }

    public void readFromNBT(ListTag list) {
        entries.clear();
        nextZoneId = 1;
        Set<Integer> used = new HashSet<>();
        for (int i = 0; i < list.size() && entries.size() < MAX_ENTRIES; i++) {
            BossPlatformZone zone = BossPlatformZone.readFromNBT(list.getCompound(i), nextZoneId);
            if (zone.getZoneId() <= 0 || !used.add(zone.getZoneId())) {
                zone.assignZoneId(firstFreeZoneId(used, 1));
                used.add(zone.getZoneId());
            }
            entries.add(zone);
            nextZoneId = Math.max(nextZoneId, zone.getZoneId() + 1);
        }
    }

    public void readFromNBT(CompoundTag parent, String key) {
        readFromNBT(parent.contains(key, Tag.TAG_LIST)
                ? parent.getList(key, Tag.TAG_COMPOUND) : new ListTag());
    }

    private int allocateZoneId() {
        Set<Integer> used = new HashSet<>();
        for (BossPlatformZone zone : entries) {
            used.add(zone.getZoneId());
        }
        int result = firstFreeZoneId(used, nextZoneId);
        nextZoneId = result == Integer.MAX_VALUE ? 1 : result + 1;
        return result;
    }

    private static int firstFreeZoneId(Set<Integer> used, int start) {
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

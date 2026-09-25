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
 * The vents one phase can fire, in the order the builder listed them: a growable, bounded list
 * whose zone ids survive row deletion, the way the platforms' list is kept.
 *
 * <p>The order is the one a volley taken one vent after another walks.</p>
 */
public final class BossVentZoneList {
    /** Past this a volley is half the arena going off at once, and the warnings alone are a flood. */
    public static final int MAX_ENTRIES = 16;

    private final List<BossVentZone> entries = new ArrayList<>();
    private int nextZoneId = 1;

    /** @return the new vent, or null when the list is already full */
    public BossVentZone add() {
        if (entries.size() >= MAX_ENTRIES) {
            return null;
        }
        BossVentZone zone = new BossVentZone(allocateZoneId());
        entries.add(zone);
        return zone;
    }

    public int size() { return entries.size(); }

    public BossVentZone get(int index) { return entries.get(index); }

    public List<BossVentZone> entries() { return Collections.unmodifiableList(entries); }

    public BossVentZone remove(int index) { return entries.remove(index); }

    public void clear() { entries.clear(); }

    /** Whether any vent is switched on, which is what a cast needs at least one of. */
    public boolean hasEnabled() {
        for (BossVentZone zone : entries) {
            if (zone.isEnabled()) {
                return true;
            }
        }
        return false;
    }

    public ListTag writeToNBT() {
        ListTag list = new ListTag();
        for (BossVentZone zone : entries) {
            list.add(zone.writeToNBT());
        }
        return list;
    }

    public void readFromNBT(ListTag list) {
        entries.clear();
        nextZoneId = 1;
        Set<Integer> used = new HashSet<>();
        for (int i = 0; i < list.size() && entries.size() < MAX_ENTRIES; i++) {
            BossVentZone zone = BossVentZone.readFromNBT(list.getCompound(i), nextZoneId);
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
        for (BossVentZone zone : entries) {
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

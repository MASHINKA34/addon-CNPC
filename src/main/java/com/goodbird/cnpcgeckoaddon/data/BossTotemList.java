package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Growable, bounded list whose slot ids survive row deletion and reordering. */
public final class BossTotemList {
    public static final int MAX_ENTRIES = 16;

    private final List<BossTotemEntry> entries = new ArrayList<>();
    private int nextSlotId = 1;

    /** Adds an empty entry, or returns {@code null} when all sixteen slots are in use. */
    public BossTotemEntry add() {
        if (entries.size() >= MAX_ENTRIES) {
            return null;
        }
        BossTotemEntry entry = new BossTotemEntry(allocateSlotId());
        entries.add(entry);
        return entry;
    }

    public int size() { return entries.size(); }

    public BossTotemEntry get(int index) { return entries.get(index); }

    public List<BossTotemEntry> entries() { return Collections.unmodifiableList(entries); }

    public BossTotemEntry remove(int index) { return entries.remove(index); }

    public void clear() { entries.clear(); }

    public ListTag writeToNBT() {
        ListTag list = new ListTag();
        for (BossTotemEntry entry : entries) {
            list.add(entry.writeToNBT());
        }
        return list;
    }

    public void readFromNBT(ListTag list) {
        entries.clear();
        nextSlotId = 1;
        Set<Integer> used = new HashSet<>();
        for (int i = 0; i < list.size() && entries.size() < MAX_ENTRIES; i++) {
            BossTotemEntry entry = BossTotemEntry.readFromNBT(list.getCompound(i), nextSlotId);
            if (entry.getSlotId() <= 0 || !used.add(entry.getSlotId())) {
                entry.assignSlotId(firstFreeSlot(used));
                used.add(entry.getSlotId());
            }
            entries.add(entry);
            nextSlotId = Math.max(nextSlotId, entry.getSlotId() + 1);
        }
    }

    private int allocateSlotId() {
        Set<Integer> used = new HashSet<>();
        for (BossTotemEntry entry : entries) {
            used.add(entry.getSlotId());
        }
        int result = firstFreeSlotFrom(used, nextSlotId);
        nextSlotId = result == Integer.MAX_VALUE ? 1 : result + 1;
        return result;
    }

    private static int firstFreeSlot(Set<Integer> used) {
        return firstFreeSlotFrom(used, 1);
    }

    private static int firstFreeSlotFrom(Set<Integer> used, int start) {
        int candidate = Math.max(1, start);
        while (used.contains(candidate) && candidate < Integer.MAX_VALUE) {
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

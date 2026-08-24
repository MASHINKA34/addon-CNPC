package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The hand-picked half of a boss loot chest.
 *
 * <p>A fixed number of slots rather than a growable list, for the same reason the effect
 * set has them: the editing GUI cannot rebuild itself when the count changes, and each
 * slot already says whether it holds anything. Twenty-seven of them, so the list can never
 * ask for more than a chest has room for.</p>
 */
public final class BossLootList {
    public static final int SLOTS = 27;

    private final List<BossLootEntry> entries = new ArrayList<>();

    public BossLootList() {
        for (int i = 0; i < SLOTS; i++) {
            entries.add(new BossLootEntry());
        }
    }

    public BossLootEntry get(int index) {
        return entries.get(Math.max(0, Math.min(index, SLOTS - 1)));
    }

    /** @return the first slot with nothing in it, or -1 when the list is full */
    public int firstEmptySlot() {
        for (int i = 0; i < SLOTS; i++) {
            if (entries.get(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    public void clear() {
        for (BossLootEntry entry : entries) {
            entry.clear();
        }
    }

    /** Rolls every configured slot once; empty slots and failed chances contribute nothing. */
    public List<ItemStack> rollAll(RandomSource random) {
        List<ItemStack> rolled = new ArrayList<>();
        for (BossLootEntry entry : entries) {
            ItemStack stack = entry.roll(random);
            if (!stack.isEmpty()) {
                rolled.add(stack);
            }
        }
        return rolled;
    }

    public ListTag writeToNBT() {
        ListTag list = new ListTag();
        for (BossLootEntry entry : entries) {
            list.add(entry.writeToNBT());
        }
        return list;
    }

    public void readFromNBT(ListTag list) {
        for (int i = 0; i < SLOTS; i++) {
            entries.get(i).readFromNBT(i < list.size() ? list.getCompound(i) : new CompoundTag());
        }
    }

    /** Reads the list stored under {@code key}, emptying every slot when it is absent. */
    public void readFromNBT(CompoundTag parent, String key) {
        readFromNBT(parent.contains(key, Tag.TAG_LIST) ? parent.getList(key, Tag.TAG_COMPOUND) : new ListTag());
    }
}

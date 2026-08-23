package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * The potion effects one boss attack applies.
 *
 * <p>A fixed number of slots rather than a growable list, because the editing GUI has no way
 * to rebuild itself when the count changes - each slot simply carries its own on/off flag.</p>
 */
public final class BossEffectSet {
    public static final int SLOTS = 3;

    private final List<BossEffectData> effects = new ArrayList<>();

    public BossEffectSet() {
        for (int i = 0; i < SLOTS; i++) {
            effects.add(new BossEffectData());
        }
    }

    public BossEffectData get(int index) {
        return effects.get(Math.max(0, Math.min(index, SLOTS - 1)));
    }

    public boolean isAnyEnabled() {
        for (BossEffectData effect : effects) {
            if (effect.isEnabled()) {
                return true;
            }
        }
        return false;
    }

    public void applyAll(LivingEntity victim, Entity source) {
        if (victim == null) {
            return;
        }
        for (BossEffectData effect : effects) {
            effect.apply(victim, source);
        }
    }

    public ListTag writeToNBT() {
        ListTag list = new ListTag();
        for (BossEffectData effect : effects) {
            list.add(effect.writeToNBT());
        }
        return list;
    }

    public void readFromNBT(ListTag list) {
        for (int i = 0; i < SLOTS; i++) {
            CompoundTag tag = i < list.size() ? list.getCompound(i) : new CompoundTag();
            effects.get(i).readFromNBT(tag);
        }
    }

    /** Reads the set stored under {@code key}, resetting to defaults when it is absent. */
    public void readFromNBT(CompoundTag parent, String key) {
        readFromNBT(parent.contains(key, Tag.TAG_LIST) ? parent.getList(key, Tag.TAG_COMPOUND) : new ListTag());
    }
}

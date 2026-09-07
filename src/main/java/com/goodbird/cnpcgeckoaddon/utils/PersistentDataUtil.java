package com.goodbird.cnpcgeckoaddon.utils;

import com.goodbird.cnpcgeckoaddon.mixin.impl.EntityPersistentDataAccessor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

public final class PersistentDataUtil {

    private PersistentDataUtil() {
    }

    public static CompoundTag read(Entity entity) {
        CompoundTag existing = existing(entity);
        return existing == null ? new CompoundTag() : existing;
    }

    public static String getString(Entity entity, String key) {
        CompoundTag existing = existing(entity);
        return existing == null ? "" : existing.getString(key);
    }

    public static int getInt(Entity entity, String key) {
        CompoundTag existing = existing(entity);
        return existing == null ? 0 : existing.getInt(key);
    }

    public static int[] getIntArray(Entity entity, String key) {
        CompoundTag existing = existing(entity);
        return existing == null ? new int[0] : existing.getIntArray(key);
    }

    public static boolean contains(Entity entity, String key, int type) {
        CompoundTag existing = existing(entity);
        return existing != null && existing.contains(key, type);
    }

    private static CompoundTag existing(Entity entity) {
        return entity == null
                ? null
                : ((EntityPersistentDataAccessor) entity).cnpcgeckoaddon$existingPersistentData();
    }
}

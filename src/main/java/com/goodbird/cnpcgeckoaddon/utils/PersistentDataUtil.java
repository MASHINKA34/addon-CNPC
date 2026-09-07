package com.goodbird.cnpcgeckoaddon.utils;

import com.goodbird.cnpcgeckoaddon.mixin.impl.EntityPersistentDataAccessor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

public final class PersistentDataUtil {

    private static final CompoundTag EMPTY = new CompoundTag();

    private PersistentDataUtil() {
    }

    public static CompoundTag read(Entity entity) {
        if (entity == null) {
            return EMPTY;
        }
        CompoundTag existing = ((EntityPersistentDataAccessor) entity).cnpcgeckoaddon$existingPersistentData();
        return existing == null ? EMPTY : existing;
    }
}

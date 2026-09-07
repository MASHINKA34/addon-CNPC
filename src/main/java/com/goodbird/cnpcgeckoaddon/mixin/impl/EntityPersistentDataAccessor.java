package com.goodbird.cnpcgeckoaddon.mixin.impl;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface EntityPersistentDataAccessor {

    @Accessor(value = "persistentData", remap = false)
    CompoundTag cnpcgeckoaddon$existingPersistentData();
}

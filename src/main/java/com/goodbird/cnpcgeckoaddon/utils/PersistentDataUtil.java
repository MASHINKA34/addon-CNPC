package com.goodbird.cnpcgeckoaddon.utils;

import com.goodbird.cnpcgeckoaddon.mixin.impl.EntityPersistentDataAccessor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

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

    /**
     * One compound a player keeps under {@link Player#PERSISTED_NBT_TAG}, the part of their
     * persistent data NeoForge copies onto the new player when they respawn, or an empty one.
     * Read-only: nothing is created on a player that has none.
     */
    public static CompoundTag getPlayerPersisted(Player player, String key) {
        CompoundTag existing = existing(player);
        if (existing == null || !existing.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            return new CompoundTag();
        }
        return existing.getCompound(Player.PERSISTED_NBT_TAG).getCompound(key);
    }

    /** Whether a player keeps a compound of that name under {@link Player#PERSISTED_NBT_TAG}. */
    public static boolean hasPlayerPersisted(Player player, String key) {
        CompoundTag existing = existing(player);
        return existing != null && existing.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)
                && existing.getCompound(Player.PERSISTED_NBT_TAG).contains(key, Tag.TAG_COMPOUND);
    }

    /** Writes one compound under {@link Player#PERSISTED_NBT_TAG}, replacing any of that name. */
    public static void putPlayerPersisted(Player player, String key, CompoundTag value) {
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        persisted.put(key, value);
        root.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    /** Takes one compound off, and the persisted block with it once it is empty. */
    public static void removePlayerPersisted(Player player, String key) {
        CompoundTag existing = existing(player);
        if (existing == null || !existing.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag persisted = existing.getCompound(Player.PERSISTED_NBT_TAG);
        persisted.remove(key);
        if (persisted.isEmpty()) {
            existing.remove(Player.PERSISTED_NBT_TAG);
        }
    }

    private static CompoundTag existing(Entity entity) {
        return entity == null
                ? null
                : ((EntityPersistentDataAccessor) entity).cnpcgeckoaddon$existingPersistentData();
    }
}

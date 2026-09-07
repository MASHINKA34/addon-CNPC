package com.goodbird.cnpcgeckoaddon.world;

import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.utils.PersistentDataUtil;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public abstract class BossOwnedEntityCleanupStore extends SavedData {
    private final String ownerKey;
    private final String generationKey;
    private final Map<UUID, Cleanup> cleanups = new LinkedHashMap<>();

    private record Cleanup(long generation, int removalMode) {
    }

    protected BossOwnedEntityCleanupStore(String ownerKey, String generationKey) {
        this.ownerKey = ownerKey;
        this.generationKey = generationKey;
    }

    public final long generation(UUID owner) {
        Cleanup cleanup = cleanups.get(owner);
        return cleanup == null ? 0L : cleanup.generation();
    }

    public final void invalidate(UUID owner, int removalMode) {
        cleanups.put(owner, new Cleanup(Math.addExact(generation(owner), 1L), removalMode));
        setDirty();
    }

    public final int pendingRemovalMode(Entity entity) {
        CompoundTag data = PersistentDataUtil.read(entity);
        String owner = data.getString(ownerKey);
        if (owner.isEmpty()) {
            return -1;
        }
        UUID ownerId;
        try {
            ownerId = UUID.fromString(owner);
        } catch (IllegalArgumentException ignored) {
            return -1;
        }
        Cleanup cleanup = cleanups.get(ownerId);
        return cleanup != null && data.getLong(generationKey) < cleanup.generation()
                ? cleanup.removalMode() : -1;
    }

    protected final void readEntries(CompoundTag tag) {
        ListTag entries = tag.getList("Entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (entry.hasUUID("Owner") && entry.getLong("Generation") > 0L) {
                int mode = entry.getInt("RemovalMode") == TeleportPathData.MINION_REMOVAL_KILL
                        ? TeleportPathData.MINION_REMOVAL_KILL : TeleportPathData.MINION_REMOVAL_VANISH;
                UUID owner = entry.getUUID("Owner");
                long generation = entry.getLong("Generation");
                if (generation > generation(owner)) {
                    cleanups.put(owner, new Cleanup(generation, mode));
                }
            }
        }
    }

    @Override
    public final CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        cleanups.forEach((owner, cleanup) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Owner", owner);
            entry.putLong("Generation", cleanup.generation());
            entry.putInt("RemovalMode", cleanup.removalMode());
            entries.add(entry);
        });
        tag.put("Entries", entries);
        return tag;
    }
}

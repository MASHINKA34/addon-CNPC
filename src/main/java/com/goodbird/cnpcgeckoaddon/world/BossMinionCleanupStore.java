package com.goodbird.cnpcgeckoaddon.world;

import com.goodbird.cnpcgeckoaddon.ai.BossMinionUtil;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.utils.PersistentDataUtil;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class BossMinionCleanupStore extends SavedData {
    public static final String GENERATION_KEY = "CNPCGeckoBossMinionGeneration";
    private static final String NAME = "cnpcgeckoaddon_minion_cleanup";
    private static final Factory<BossMinionCleanupStore> FACTORY =
            new Factory<>(BossMinionCleanupStore::new, BossMinionCleanupStore::load);
    private static final int MAX_ENTRIES = 4096;

    private final Map<UUID, Cleanup> cleanups = new LinkedHashMap<>();

    private record Cleanup(long generation, int removalMode) {
    }

    public static BossMinionCleanupStore get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public long generation(UUID owner) {
        Cleanup cleanup = cleanups.get(owner);
        return cleanup == null ? 0L : cleanup.generation();
    }

    public void invalidate(UUID owner, int removalMode) {
        Cleanup cleanup = new Cleanup(generation(owner) + 1L, removalMode);
        cleanups.remove(owner);
        cleanups.put(owner, cleanup);
        trim();
        setDirty();
    }

    private void trim() {
        Iterator<UUID> owners = cleanups.keySet().iterator();
        while (cleanups.size() > MAX_ENTRIES && owners.hasNext()) {
            owners.next();
            owners.remove();
        }
    }

    public int pendingRemovalMode(Entity minion) {
        CompoundTag data = PersistentDataUtil.read(minion);
        String owner = data.getString(BossMinionUtil.MINION_OWNER_KEY);
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
        return cleanup != null && data.getLong(GENERATION_KEY) < cleanup.generation()
                ? cleanup.removalMode() : -1;
    }

    public static BossMinionCleanupStore load(CompoundTag tag, HolderLookup.Provider registries) {
        BossMinionCleanupStore store = new BossMinionCleanupStore();
        ListTag entries = tag.getList("Entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (entry.hasUUID("Owner") && entry.getLong("Generation") > 0L) {
                int mode = entry.getInt("RemovalMode") == TeleportPathData.MINION_REMOVAL_KILL
                        ? TeleportPathData.MINION_REMOVAL_KILL : TeleportPathData.MINION_REMOVAL_VANISH;
                store.cleanups.put(entry.getUUID("Owner"), new Cleanup(entry.getLong("Generation"), mode));
            }
        }
        store.trim();
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
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

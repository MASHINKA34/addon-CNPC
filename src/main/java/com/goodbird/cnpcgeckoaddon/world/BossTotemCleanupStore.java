package com.goodbird.cnpcgeckoaddon.world;

import com.goodbird.cnpcgeckoaddon.ai.BossTotemUtil;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

public final class BossTotemCleanupStore extends BossOwnedEntityCleanupStore {
    public static final String GENERATION_KEY = "CNPCGeckoBossTotemGeneration";
    private static final String NAME = "cnpcgeckoaddon_totem_cleanup";
    private static final Factory<BossTotemCleanupStore> FACTORY =
            new Factory<>(BossTotemCleanupStore::new, BossTotemCleanupStore::load);

    public BossTotemCleanupStore() {
        super(BossTotemUtil.TOTEM_OWNER_KEY, GENERATION_KEY);
    }

    public static BossTotemCleanupStore get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public static BossTotemCleanupStore load(CompoundTag tag, HolderLookup.Provider registries) {
        BossTotemCleanupStore store = new BossTotemCleanupStore();
        store.readEntries(tag);
        return store;
    }
}

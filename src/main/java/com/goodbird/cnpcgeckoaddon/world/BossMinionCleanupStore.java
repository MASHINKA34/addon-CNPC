package com.goodbird.cnpcgeckoaddon.world;

import com.goodbird.cnpcgeckoaddon.ai.BossMinionUtil;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

public final class BossMinionCleanupStore extends BossOwnedEntityCleanupStore {
    public static final String GENERATION_KEY = "CNPCGeckoBossMinionGeneration";
    private static final String NAME = "cnpcgeckoaddon_minion_cleanup";
    private static final Factory<BossMinionCleanupStore> FACTORY =
            new Factory<>(BossMinionCleanupStore::new, BossMinionCleanupStore::load);

    public BossMinionCleanupStore() {
        super(BossMinionUtil.MINION_OWNER_KEY, GENERATION_KEY);
    }

    public static BossMinionCleanupStore get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public static BossMinionCleanupStore load(CompoundTag tag, HolderLookup.Provider registries) {
        BossMinionCleanupStore store = new BossMinionCleanupStore();
        store.readEntries(tag);
        return store;
    }
}

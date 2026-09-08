package com.goodbird.cnpcgeckoaddon.world;

import com.goodbird.cnpcgeckoaddon.ai.BossMinionUtil;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

public final class BossCocoonGuardCleanupStore extends BossOwnedEntityCleanupStore {
    public static final String GENERATION_KEY = "CNPCGeckoBossCocoonGuardGeneration";
    private static final String NAME = "cnpcgeckoaddon_cocoon_guard_cleanup";
    private static final Factory<BossCocoonGuardCleanupStore> FACTORY =
            new Factory<>(BossCocoonGuardCleanupStore::new, BossCocoonGuardCleanupStore::load);

    public BossCocoonGuardCleanupStore() {
        super(BossMinionUtil.MINION_OWNER_KEY, GENERATION_KEY);
    }

    public static BossCocoonGuardCleanupStore get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public static BossCocoonGuardCleanupStore load(CompoundTag tag, HolderLookup.Provider registries) {
        BossCocoonGuardCleanupStore store = new BossCocoonGuardCleanupStore();
        store.readEntries(tag);
        return store;
    }
}

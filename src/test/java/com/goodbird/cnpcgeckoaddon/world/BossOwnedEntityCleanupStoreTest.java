package com.goodbird.cnpcgeckoaddon.world;

import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossOwnedEntityCleanupStoreTest {
    @Test
    void generationsSurviveSavingAndAdvanceIndependently() {
        BossTotemCleanupStore store = new BossTotemCleanupStore();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertFalse(store.isDirty());
        store.invalidate(first, TeleportPathData.MINION_REMOVAL_VANISH);
        store.invalidate(first, TeleportPathData.MINION_REMOVAL_VANISH);
        store.invalidate(second, TeleportPathData.MINION_REMOVAL_VANISH);
        assertTrue(store.isDirty());
        BossTotemCleanupStore restored = BossTotemCleanupStore.load(store.save(new CompoundTag(), null), null);
        assertEquals(2, restored.generation(first));
        assertEquals(1, restored.generation(second));
        assertEquals(0, restored.generation(UUID.randomUUID()));
    }

    @Test
    void minionHistoryKeepsTheExistingSaveFormat() {
        UUID owner = UUID.randomUUID();
        CompoundTag saved = entries(entry(owner, 7, TeleportPathData.MINION_REMOVAL_KILL));
        BossMinionCleanupStore restored = BossMinionCleanupStore.load(saved, null);
        assertEquals(saved, restored.save(new CompoundTag(), null));
        restored.invalidate(owner, TeleportPathData.MINION_REMOVAL_VANISH);
        assertEquals(8, restored.generation(owner));
    }

    @Test
    void cleanupIsNotForgottenWhenMoreBossesDie() {
        for (BossOwnedEntityCleanupStore store : new BossOwnedEntityCleanupStore[]{
                new BossMinionCleanupStore(), new BossTotemCleanupStore(), new BossCocoonGuardCleanupStore()}) {
            UUID oldest = UUID.randomUUID();
            store.invalidate(oldest, TeleportPathData.MINION_REMOVAL_VANISH);
            for (int i = 0; i < 5000; i++) {
                store.invalidate(UUID.randomUUID(), TeleportPathData.MINION_REMOVAL_VANISH);
            }
            assertEquals(1, store.generation(oldest));
            CompoundTag saved = store.save(new CompoundTag(), null);
            assertEquals(1, BossTotemCleanupStore.load(saved, null).generation(oldest));
        }
    }

    @Test
    void duplicateEntriesCannotRollBackCleanup() {
        UUID owner = UUID.randomUUID();
        CompoundTag saved = entries(entry(owner, 9, TeleportPathData.MINION_REMOVAL_KILL),
                entry(owner, 2, TeleportPathData.MINION_REMOVAL_VANISH));
        BossMinionCleanupStore restored = BossMinionCleanupStore.load(saved, null);
        assertEquals(entries(entry(owner, 9, TeleportPathData.MINION_REMOVAL_KILL)),
                restored.save(new CompoundTag(), null));
    }

    @Test
    void malformedEntriesDoNotCorruptValidHistory() {
        UUID owner = UUID.randomUUID();
        CompoundTag saved = entries(new CompoundTag(), entry(owner, -1, 0), entry(owner, 3, 999));
        BossTotemCleanupStore restored = BossTotemCleanupStore.load(saved, null);
        assertEquals(entries(entry(owner, 3, TeleportPathData.MINION_REMOVAL_VANISH)),
                restored.save(new CompoundTag(), null));
    }

    private static CompoundTag entries(CompoundTag... values) {
        ListTag entries = new ListTag();
        for (CompoundTag value : values) entries.add(value);
        CompoundTag tag = new CompoundTag();
        tag.put("Entries", entries);
        return tag;
    }

    private static CompoundTag entry(UUID owner, long generation, int mode) {
        CompoundTag entry = new CompoundTag();
        entry.putUUID("Owner", owner);
        entry.putLong("Generation", generation);
        entry.putInt("RemovalMode", mode);
        return entry;
    }
}

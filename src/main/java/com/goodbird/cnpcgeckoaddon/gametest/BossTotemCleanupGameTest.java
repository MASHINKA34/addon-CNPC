package com.goodbird.cnpcgeckoaddon.gametest;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.ai.BossTotemUtil;
import com.goodbird.cnpcgeckoaddon.ai.BossMinionUtil;
import com.goodbird.cnpcgeckoaddon.world.BossTotemCleanupStore;
import com.goodbird.cnpcgeckoaddon.ai.TeleportPathController;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.mixin.ITeleportPathData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import noppes.npcs.CustomEntities;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.stream.Stream;

@GameTestHolder(CNPCGeckoAddon.MODID)
@PrefixGameTestTemplate(false)
public class BossTotemCleanupGameTest {
    @GameTest(template = "fluid_platform")
    public static void savedCloneCanBecomeANewTotemOrMinion(GameTestHelper helper) {
        EntityNPCInterface boss = CustomEntities.entityCustomNpc.create(helper.getLevel());
        EntityNPCInterface old = totem(helper, boss, 1);
        CompoundTag saved = old.saveWithoutId(new CompoundTag());
        BossTotemUtil.clear(helper.getLevel(), boss);
        EntityNPCInterface fresh = CustomEntities.entityCustomNpc.create(helper.getLevel());
        fresh.load(saved);
        fresh.setUUID(java.util.UUID.randomUUID());
        try {
            helper.assertTrue(helper.getLevel().addFreshEntity(fresh),
                    "a newly spawned clone must reach the role assignment after the join event");
            BossTotemUtil.markAsTotem(fresh, boss, 2);
            helper.assertTrue(BossTotemCleanupStore.get(helper.getLevel()).pendingRemovalMode(fresh) == -1,
                    "a new totem must belong to the current generation");
            BossMinionUtil.markAsMinion(fresh, boss);
            helper.assertFalse(BossTotemUtil.isTotem(fresh), "a minion must no longer have the totem role");
            helper.assertFalse(fresh.getPersistentData().contains(BossTotemCleanupStore.GENERATION_KEY),
                    "changing role must discard the previous generation marker");
            helper.succeed();
        } finally {
            fresh.discard();
            old.discard();
            boss.discard();
        }
    }

    @GameTest(template = "fluid_platform")
    public static void cleanupIncludesLegacyTotemsAfterStoreReload(GameTestHelper helper) {
        EntityNPCInterface boss = CustomEntities.entityCustomNpc.create(helper.getLevel());
        EntityNPCInterface legacy = totem(helper, boss, 1);
        legacy.getPersistentData().remove(BossTotemCleanupStore.GENERATION_KEY);
        BossTotemCleanupStore store = BossTotemCleanupStore.get(helper.getLevel());
        store.invalidate(boss.getUUID(), TeleportPathData.MINION_REMOVAL_VANISH);
        BossTotemCleanupStore restored = BossTotemCleanupStore.load(
                store.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertTrue(restored.pendingRemovalMode(legacy) == TeleportPathData.MINION_REMOVAL_VANISH,
                "a saved cleanup request must also reach legacy totems with no generation marker");
        legacy.discard();
        boss.discard();
        helper.succeed();
    }

    @GameTest(template = "fluid_platform")
    public static void bossDeathRejectsUnloadedTotemsAndAllowsNewWave(GameTestHelper helper) {
        EntityNPCInterface boss = CustomEntities.entityCustomNpc.create(helper.getLevel());
        TeleportPathData data = ((ITeleportPathData) boss.ais).cnpcgeckoaddon$getTeleportPathData();
        data.setTotemRemoveOnBossDeath(true);
        EntityNPCInterface stale = totem(helper, boss, 1);
        CompoundTag saved = stale.saveWithoutId(new CompoundTag());
        stale.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        TeleportPathController controller = new TeleportPathController(boss);
        controller.onDeath();
        EntityNPCInterface restored = restore(helper, saved);
        try {
            helper.assertTrue(helper.getLevel().getEntity(restored.getUUID()) == null,
                    "a totem unloaded when its boss died must not reenter the world");
            EntityNPCInterface fresh = totem(helper, boss, 1);
            CompoundTag newSaved = fresh.saveWithoutId(new CompoundTag());
            fresh.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            EntityNPCInterface restoredFresh = restore(helper, newSaved);
            helper.assertTrue(helper.getLevel().getEntity(restoredFresh.getUUID()) == restoredFresh,
                    "a new wave from the same boss must survive previous cleanup");
            restoredFresh.discard();
            helper.succeed();
        } finally {
            restored.discard();
            controller.shutdown();
            boss.discard();
        }
    }

    @GameTest(template = "fluid_platform")
    public static void disabledDeathCleanupPreservesUnloadedTotems(GameTestHelper helper) {
        EntityNPCInterface boss = CustomEntities.entityCustomNpc.create(helper.getLevel());
        ((ITeleportPathData) boss.ais).cnpcgeckoaddon$getTeleportPathData().setTotemRemoveOnBossDeath(false);
        EntityNPCInterface totem = totem(helper, boss, 1);
        CompoundTag saved = totem.saveWithoutId(new CompoundTag());
        totem.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        TeleportPathController controller = new TeleportPathController(boss);
        controller.onDeath();
        EntityNPCInterface restored = restore(helper, saved);
        try {
            helper.assertTrue(helper.getLevel().getEntity(restored.getUUID()) == restored,
                    "turning death cleanup off must also preserve unloaded totems");
            helper.succeed();
        } finally {
            restored.discard();
            controller.shutdown();
            boss.discard();
        }
    }

    private static EntityNPCInterface totem(GameTestHelper helper, Entity boss, int slot) {
        EntityNPCInterface totem = helper.spawn(CustomEntities.entityCustomNpc, new BlockPos(2, 2, 2));
        totem.setNoAi(true);
        BossTotemUtil.markAsTotem(totem, boss, slot);
        return totem;
    }

    private static EntityNPCInterface restore(GameTestHelper helper, CompoundTag saved) {
        EntityNPCInterface totem = CustomEntities.entityCustomNpc.create(helper.getLevel());
        totem.load(saved);
        helper.getLevel().addLegacyChunkEntities(Stream.of(totem));
        return totem;
    }
}

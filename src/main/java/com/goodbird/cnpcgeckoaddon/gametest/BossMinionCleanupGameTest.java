package com.goodbird.cnpcgeckoaddon.gametest;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.ai.BossMinionUtil;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.mixin.ITeleportPathData;
import com.goodbird.cnpcgeckoaddon.world.BossMinionCleanupStore;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import noppes.npcs.CustomEntities;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.stream.Stream;

@GameTestHolder(CNPCGeckoAddon.MODID)
@PrefixGameTestTemplate(false)
public class BossMinionCleanupGameTest {
    @GameTest(template = "fluid_platform")
    public static void cleanupRejectsUnloadedMinionsButKeepsNewGeneration(GameTestHelper helper) {
        EntityNPCInterface boss = CustomEntities.entityCustomNpc.create(helper.getLevel());
        CompoundTag saved = saveUnloadedMinion(helper, boss);
        BossMinionUtil.clear(helper.getLevel(), boss, TeleportPathData.MINION_REMOVAL_VANISH);
        Cow stale = restore(helper, saved);
        helper.assertTrue(helper.getLevel().getEntity(stale.getUUID()) == null,
                "a minion loaded after cleanup must not reenter the world");
        Cow fresh = helper.spawn(EntityType.COW, new BlockPos(3, 2, 2));
        BossMinionUtil.markAsMinion(fresh, boss);
        helper.assertTrue(BossMinionCleanupStore.get(helper.getLevel()).pendingRemovalMode(fresh) == -1,
                "the next generation of the same boss must survive old cleanup");
        CompoundTag freshSaved = fresh.saveWithoutId(new CompoundTag());
        fresh.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        Cow restoredFresh = restore(helper, freshSaved);
        helper.assertTrue(helper.getLevel().getEntity(restoredFresh.getUUID()) == restoredFresh,
                "a new generation must also survive save and reload");
        restoredFresh.discard();
        boss.discard();
        helper.succeed();
    }

    @GameTest(template = "fluid_platform")
    public static void cleanupMarkerSurvivesStoreSave(GameTestHelper helper) {
        EntityNPCInterface boss = CustomEntities.entityCustomNpc.create(helper.getLevel());
        Cow old = EntityType.COW.create(helper.getLevel());
        old.getPersistentData().putString(BossMinionUtil.MINION_OWNER_KEY, boss.getUUID().toString());
        BossMinionCleanupStore store = BossMinionCleanupStore.get(helper.getLevel());
        helper.assertTrue(store.pendingRemovalMode(old) == -1, "legacy minions must survive until cleanup is requested");
        store.invalidate(boss.getUUID(), TeleportPathData.MINION_REMOVAL_KILL);
        CompoundTag saved = store.save(new CompoundTag(), helper.getLevel().registryAccess());
        BossMinionCleanupStore restored = BossMinionCleanupStore.load(saved, helper.getLevel().registryAccess());
        helper.assertTrue(restored.pendingRemovalMode(old) == TeleportPathData.MINION_REMOVAL_KILL,
                "cleanup must survive a restart and include legacy minions without a generation tag");
        old.getPersistentData().putLong(BossMinionCleanupStore.GENERATION_KEY, restored.generation(boss.getUUID()));
        helper.assertTrue(restored.pendingRemovalMode(old) == -1, "the persisted current generation must remain valid");
        old.discard();
        boss.discard();
        helper.succeed();
    }

    @GameTest(template = "fluid_platform")
    public static void bossDeathInvalidatesMinionsThatAreNotLoaded(GameTestHelper helper) {
        EntityNPCInterface boss = helper.spawn(CustomEntities.entityCustomNpc, new BlockPos(2, 2, 2));
        boss.setNoAi(true);
        TeleportPathData data = ((ITeleportPathData) boss.ais).cnpcgeckoaddon$getTeleportPathData();
        data.setEnabled(true);
        data.setClearMinionsOnDeath(true);
        data.setMinionRemovalMode(TeleportPathData.MINION_REMOVAL_VANISH);
        CompoundTag saved = saveUnloadedMinion(helper, boss);
        boss.hurt(helper.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
        helper.assertTrue(!boss.isAlive(), "the fixture boss must die");
        Cow stale = restore(helper, saved);
        helper.assertTrue(helper.getLevel().getEntity(stale.getUUID()) == null,
                "boss death must invalidate minions even when none are loaded at death");
        boss.discard();
        helper.succeed();
    }

    @GameTest(template = "fluid_platform", timeoutTicks = 60)
    public static void unloadedMinionsHonorKillRemovalMode(GameTestHelper helper) {
        EntityNPCInterface boss = CustomEntities.entityCustomNpc.create(helper.getLevel());
        CompoundTag saved = saveUnloadedMinion(helper, boss);
        BossMinionUtil.clear(helper.getLevel(), boss, TeleportPathData.MINION_REMOVAL_KILL);
        Cow stale = restore(helper, saved);
        helper.runAfterDelay(3, () -> {
            try {
                helper.assertTrue(!stale.isAlive() || stale.isRemoved(),
                        "KILL must remove the stale minion after its chunk finishes loading");
                helper.succeed();
            } finally {
                stale.discard();
                boss.discard();
            }
        });
    }

    private static CompoundTag saveUnloadedMinion(GameTestHelper helper, Entity boss) {
        Cow minion = helper.spawn(EntityType.COW, new BlockPos(4, 2, 2));
        minion.setNoAi(true);
        BossMinionUtil.markAsMinion(minion, boss);
        CompoundTag saved = minion.saveWithoutId(new CompoundTag());
        minion.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        return saved;
    }

    private static Cow restore(GameTestHelper helper, CompoundTag saved) {
        Cow minion = EntityType.COW.create(helper.getLevel());
        minion.load(saved);
        helper.getLevel().addLegacyChunkEntities(Stream.of(minion));
        return minion;
    }
}

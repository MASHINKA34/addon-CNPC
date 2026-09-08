package com.goodbird.cnpcgeckoaddon.gametest;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.ai.BossCocoonUtil;
import com.goodbird.cnpcgeckoaddon.ai.BossMinionUtil;
import com.goodbird.cnpcgeckoaddon.ai.TeleportPathController;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.mixin.ITeleportPathData;
import com.goodbird.cnpcgeckoaddon.world.BossCocoonGuardCleanupStore;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import noppes.npcs.CustomEntities;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.stream.Stream;

@GameTestHolder(CNPCGeckoAddon.MODID)
@PrefixGameTestTemplate(false)
public class BossCocoonGuardCleanupGameTest {
    @GameTest(template = "fluid_platform")
    public static void guardCleanupCrossesDimensionsAndPreservesOtherMinions(GameTestHelper helper) {
        EntityNPCInterface boss = CustomEntities.entityCustomNpc.create(helper.getLevel());
        EntityNPCInterface otherBoss = CustomEntities.entityCustomNpc.create(helper.getLevel());
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        Cow remote = trackedCow(nether);
        remote.setPos(8, 100, 8);
        remote.setNoAi(true);
        remote.setNoGravity(true);
        BossCocoonUtil.markAsGuard(remote, boss);
        nether.addFreshEntity(remote);
        Cow ordinary = helper.spawn(EntityType.COW, new BlockPos(3, 2, 2));
        BossMinionUtil.markAsMinion(ordinary, boss);
        Cow unrelated = helper.spawn(EntityType.COW, new BlockPos(4, 2, 2));
        BossCocoonUtil.markAsGuard(unrelated, otherBoss);
        try {
            helper.assertTrue(nether.getEntity(remote.getUUID()) == remote,
                    "the remote fixture must be visible before cleanup");
            CompoundTag saved = saveGuard(helper, boss);
            BossCocoonUtil.removeGuards(helper.getLevel(), boss);
            helper.assertTrue(remote.isRemoved(), "loaded guards must be removed across dimensions");
            helper.assertTrue(!ordinary.isRemoved() && !unrelated.isRemoved(),
                    "guard cleanup must preserve ordinary minions and guards belonging to other bosses");
            Cow stale = restore(nether, saved);
            helper.assertTrue(nether.getEntity(stale.getUUID()) == null,
                    "unloaded guards must not return in another dimension");
            Cow fresh = helper.spawn(EntityType.COW, new BlockPos(5, 2, 2));
            BossCocoonUtil.markAsGuard(fresh, boss);
            CompoundTag freshSaved = fresh.saveWithoutId(new CompoundTag());
            fresh.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            Cow restored = restore(nether, freshSaved);
            helper.assertTrue(nether.getEntity(restored.getUUID()) == restored,
                    "guards from the next encounter must survive loading in another dimension");
            restored.discard();
        } finally {
            remote.discard();
            ordinary.discard();
            unrelated.discard();
            boss.discard();
            otherBoss.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "fluid_platform")
    public static void guardCleanupHistorySurvivesSaving(GameTestHelper helper) {
        EntityNPCInterface boss = CustomEntities.entityCustomNpc.create(helper.getLevel());
        Cow legacy = EntityType.COW.create(helper.getLevel());
        BossCocoonUtil.markAsGuard(legacy, boss);
        legacy.getPersistentData().remove(BossCocoonGuardCleanupStore.GENERATION_KEY);
        BossCocoonUtil.removeGuards(helper.getLevel(), boss);
        BossCocoonGuardCleanupStore store = BossCocoonGuardCleanupStore.get(helper.getLevel());
        BossCocoonGuardCleanupStore restored = BossCocoonGuardCleanupStore.load(
                store.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertTrue(restored.pendingRemovalMode(legacy) == TeleportPathData.MINION_REMOVAL_VANISH,
                "saved cleanup must reject legacy guards without a generation marker");
        BossMinionUtil.markAsMinion(legacy, boss);
        helper.assertTrue(!BossCocoonUtil.hasRole(legacy)
                        && !legacy.getPersistentData().contains(BossCocoonGuardCleanupStore.GENERATION_KEY),
                "cloning a guard as an ordinary minion must remove its guard history");
        legacy.discard();
        boss.discard();
        helper.succeed();
    }

    @GameTest(template = "fluid_platform")
    public static void bossDeathRemovesUnloadedGuardsWithMinionCleanupDisabled(GameTestHelper helper) {
        EntityNPCInterface boss = helper.spawn(CustomEntities.entityCustomNpc, new BlockPos(2, 2, 2));
        boss.setNoAi(true);
        TeleportPathData data = ((ITeleportPathData) boss.ais).cnpcgeckoaddon$getTeleportPathData();
        data.setEnabled(true);
        data.setClearMinionsOnDeath(false);
        CompoundTag saved = saveGuard(helper, boss);
        boss.hurt(helper.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
        helper.assertTrue(!boss.isAlive(), "the fixture boss must die");
        Cow stale = restore(helper.getLevel(), saved);
        helper.assertTrue(helper.getLevel().getEntity(stale.getUUID()) == null,
                "boss death must remove unloaded guards independently of general minion cleanup");
        boss.discard();
        helper.succeed();
    }

    @GameTest(template = "fluid_platform")
    public static void encounterResetAndDisableInvalidateUnloadedGuards(GameTestHelper helper)
            throws ReflectiveOperationException {
        EntityNPCInterface boss = CustomEntities.entityCustomNpc.create(helper.getLevel());
        TeleportPathData data = ((ITeleportPathData) boss.ais).cnpcgeckoaddon$getTeleportPathData();
        data.setEnabled(true);
        data.setClearMinionsOnReset(false);
        data.setResetReturn(false);
        TeleportPathController controller = new TeleportPathController(boss);
        try {
            CompoundTag saved = saveGuard(helper, boss);
            var reset = TeleportPathController.class.getDeclaredMethod("endEncounter", ServerLevel.class, TeleportPathData.class);
            reset.setAccessible(true);
            reset.invoke(controller, helper.getLevel(), data);
            Cow stale = restore(helper.getLevel(), saved);
            helper.assertTrue(helper.getLevel().getEntity(stale.getUUID()) == null,
                    "encounter reset must invalidate unloaded guards even when minions are kept");
            CompoundTag next = saveGuard(helper, boss);
            data.setEnabled(false);
            controller.tick();
            Cow disabled = restore(helper.getLevel(), next);
            helper.assertTrue(helper.getLevel().getEntity(disabled.getUUID()) == null,
                    "disabling the framework must end the current guard generation");
            helper.succeed();
        } finally {
            controller.shutdown();
            boss.discard();
        }
    }

    @GameTest(template = "fluid_platform", timeoutTicks = 60)
    public static void guardCleanupPreservesConfiguredKillMode(GameTestHelper helper) {
        EntityNPCInterface boss = CustomEntities.entityCustomNpc.create(helper.getLevel());
        CompoundTag saved = saveGuard(helper, boss);
        BossMinionUtil.clear(helper.getLevel(), boss, TeleportPathData.MINION_REMOVAL_KILL);
        BossCocoonUtil.removeGuards(helper.getLevel(), boss);
        Cow stale = restore(helper.getLevel(), saved);
        helper.assertTrue(helper.getLevel().getEntity(stale.getUUID()) == stale,
                "KILL must allow the stale guard to join before applying its death handling");
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(!stale.isAlive() || stale.isRemoved(), "the stale guard must then be killed");
            stale.discard();
            boss.discard();
            helper.succeed();
        });
    }

    private static CompoundTag saveGuard(GameTestHelper helper, Entity boss) {
        Cow guard = helper.spawn(EntityType.COW, new BlockPos(4, 2, 3));
        guard.setNoAi(true);
        BossCocoonUtil.markAsGuard(guard, boss);
        CompoundTag saved = guard.saveWithoutId(new CompoundTag());
        guard.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        return saved;
    }

    private static Cow restore(ServerLevel level, CompoundTag saved) {
        Cow restored = trackedCow(level);
        restored.load(saved);
        level.addLegacyChunkEntities(Stream.of(restored));
        return restored;
    }

    private static Cow trackedCow(ServerLevel level) {
        return new Cow(EntityType.COW, level) {
            @Override
            public boolean isAlwaysTicking() {
                return true;
            }
        };
    }
}

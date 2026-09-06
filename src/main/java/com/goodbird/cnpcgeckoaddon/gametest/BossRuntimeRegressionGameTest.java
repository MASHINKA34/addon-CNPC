package com.goodbird.cnpcgeckoaddon.gametest;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.ai.BossHealthScalingUtil;
import com.goodbird.cnpcgeckoaddon.ai.TeleportPathController;
import com.goodbird.cnpcgeckoaddon.data.BossTargetMode;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.mixin.ITeleportPathData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import noppes.npcs.CustomEntities;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.List;
import java.util.function.Predicate;

@GameTestHolder(CNPCGeckoAddon.MODID)
@PrefixGameTestTemplate(false)
public class BossRuntimeRegressionGameTest {
    @GameTest(template = "fluid_platform")
    public static void multipleMainTargetsKeepCombatTargetFirst(GameTestHelper helper) throws ReflectiveOperationException {
        EntityNPCInterface npc = CustomEntities.entityCustomNpc.create(helper.getLevel());
        npc.setPos(helper.absoluteVec(new Vec3(1, 2, 1)));
        TeleportPathData data = ((ITeleportPathData) npc.ais).cnpcgeckoaddon$getTeleportPathData();
        data.setAbilityTargetKind(TeleportPathData.ABILITY_TARGET_ALL);
        var nearest = helper.spawn(EntityType.COW, new BlockPos(2, 2, 1));
        var second = helper.spawn(EntityType.COW, new BlockPos(3, 2, 1));
        var main = helper.spawn(EntityType.COW, new BlockPos(5, 2, 1));
        nearest.setNoAi(true);
        second.setNoAi(true);
        main.setNoAi(true);
        npc.setTarget(main);
        TeleportPathController controller = new TeleportPathController(npc);
        Predicate<LivingEntity> eligible = target -> target == nearest || target == second || target == main;
        try {
            helper.assertTrue(select(controller, helper, BossTargetMode.MAIN, eligible, 2).equals(List.of(main, nearest)),
                    "MAIN must keep the distant combat target before the nearest additional victim");
            helper.assertTrue(select(controller, helper, BossTargetMode.MAIN, eligible, 4).equals(List.of(main, nearest, second)),
                    "the main target must not be duplicated when the requested count exceeds the pool");
            helper.assertTrue(select(controller, helper, BossTargetMode.NEAREST, eligible, 2).equals(List.of(nearest, second)),
                    "NEAREST must retain its own ordering");
            helper.assertTrue(select(controller, helper, BossTargetMode.FARTHEST, eligible, 2).equals(List.of(main, second)),
                    "FARTHEST must retain its own ordering");
            helper.assertTrue(select(controller, helper, BossTargetMode.MAIN,
                    target -> target == nearest || target == second, 2).equals(List.of(nearest, second)),
                    "an ineligible main target must not enter the result");
            helper.succeed();
        } finally {
            controller.shutdown();
            npc.discard();
            nearest.discard();
            second.discard();
            main.discard();
        }
    }

    private static List<?> select(TeleportPathController controller, GameTestHelper helper, int mode,
                                  Predicate<LivingEntity> eligible, int count) throws ReflectiveOperationException {
        var method = TeleportPathController.class.getDeclaredMethod("selectAbilityTargets",
                ServerLevel.class, int.class, double.class, Predicate.class, int.class);
        method.setAccessible(true);
        return (List<?>) method.invoke(controller, helper.getLevel(), mode, 16.0D, eligible, count);
    }

    @GameTest(template = "fluid_platform")
    public static void partyScalingRespectsOtherHealthMultipliers(GameTestHelper helper) throws ReflectiveOperationException {
        EntityNPCInterface npc = CustomEntities.entityCustomNpc.create(helper.getLevel());
        AttributeInstance health = npc.getAttribute(Attributes.MAX_HEALTH);
        health.setBaseValue(100.0D);
        health.addTransientModifier(modifier("total", 1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        npc.setHealth(100.0F);
        TeleportPathData data = ((ITeleportPathData) npc.ais).cnpcgeckoaddon$getTeleportPathData();
        data.setHealthPerPlayerPercent(100);
        data.setHealthScalingAdjustment(TeleportPathData.HEALTH_SCALING_KEEP_PERCENT);
        TeleportPathController controller = new TeleportPathController(npc);
        try {
            applyScaling(controller, data, 2);
            helper.assertTrue(Math.abs(npc.getMaxHealth() - 400.0F) < 0.001F,
                    "two players must produce 400 HP from a 200 HP boss with a total multiplier");
            helper.assertTrue(Math.abs(npc.getHealth() - 200.0F) < 0.001F,
                    "scaling must preserve the current health percentage");
            helper.assertTrue(BossHealthScalingUtil.getMaxHealthWithoutPartyScaling(npc) == 200.0D,
                    "the preview must remove the party bonus without removing other modifiers");
            helper.assertTrue(npc.getMaxHealth() == 400.0F,
                    "reading the preview must not mutate the live attribute");
            applyScaling(controller, data, 3);
            helper.assertTrue(Math.abs(npc.getMaxHealth() - 600.0F) < 0.001F,
                    "reapplying scaling must replace the previous bonus");
            applyScaling(controller, data, 1);
            helper.assertTrue(npc.getMaxHealth() == 200.0F && health.getBaseValue() == 100.0D,
                    "returning to one player must restore the baseline without changing the attribute base");
            helper.succeed();
        } finally {
            controller.shutdown();
            npc.discard();
        }
    }

    @GameTest(template = "fluid_platform")
    public static void combinedPartyScalingHandlesBaseAndTotalMultipliers(GameTestHelper helper) throws ReflectiveOperationException {
        EntityNPCInterface npc = CustomEntities.entityCustomNpc.create(helper.getLevel());
        AttributeInstance health = npc.getAttribute(Attributes.MAX_HEALTH);
        health.setBaseValue(100.0D);
        health.addTransientModifier(modifier("add", 20.0D, AttributeModifier.Operation.ADD_VALUE));
        health.addTransientModifier(modifier("base", 0.5D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        health.addTransientModifier(modifier("total", 1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        npc.setHealth(180.0F);
        TeleportPathData data = ((ITeleportPathData) npc.ais).cnpcgeckoaddon$getTeleportPathData();
        data.setHealthScalingMode(TeleportPathData.HEALTH_SCALING_PERCENT_AND_FLAT);
        data.setHealthPerPlayerPercent(50);
        data.setHealthPerPlayerFlat(60);
        data.setHealthScalingAdjustment(TeleportPathData.HEALTH_SCALING_KEEP_CURRENT);
        TeleportPathController controller = new TeleportPathController(npc);
        try {
            applyScaling(controller, data, 2);
            helper.assertTrue(Math.abs(npc.getMaxHealth() - 600.0F) < 0.001F,
                    "360 baseline HP plus 50 percent and 60 flat HP must produce 600 HP");
            helper.assertTrue(npc.getHealth() == 180.0F, "KEEP_CURRENT must preserve current HP");
            helper.assertTrue(BossHealthScalingUtil.getMaxHealthWithoutPartyScaling(npc) == 360.0D,
                    "the preview must evaluate additive, base and total modifiers in vanilla order");
            helper.succeed();
        } finally {
            controller.shutdown();
            npc.discard();
        }
    }

    private static AttributeModifier modifier(String id, double amount, AttributeModifier.Operation operation) {
        return new AttributeModifier(ResourceLocation.fromNamespaceAndPath(CNPCGeckoAddon.MODID, "test_health_" + id),
                amount, operation);
    }

    private static void applyScaling(TeleportPathController controller, TeleportPathData data, int players)
            throws ReflectiveOperationException {
        var count = TeleportPathController.class.getDeclaredField("scaledPlayerCount");
        count.setAccessible(true);
        count.setInt(controller, players);
        var apply = TeleportPathController.class.getDeclaredMethod("applyHealthScaling", TeleportPathData.class, long.class);
        apply.setAccessible(true);
        apply.invoke(controller, data, (long) players);
    }
}

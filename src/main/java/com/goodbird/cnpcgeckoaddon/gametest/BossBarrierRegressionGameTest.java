package com.goodbird.cnpcgeckoaddon.gametest;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.ai.TeleportPathController;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.mixin.IBossController;
import com.goodbird.cnpcgeckoaddon.mixin.ITeleportPathData;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import noppes.npcs.CustomEntities;
import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.UUID;

@GameTestHolder(CNPCGeckoAddon.MODID)
@PrefixGameTestTemplate(false)
public class BossBarrierRegressionGameTest {
    @GameTest(template = "fluid_platform", timeoutTicks = 180)
    public static void barrierPenaltyAppliesRageOnce(GameTestHelper helper) throws ReflectiveOperationException {
        EntityNPCInterface npc = boss(helper);
        TeleportPathData data = settings(npc);
        data.setRageEnabled(true);
        data.setRageDelayTicks(100);
        data.setRageMultiplierPercent(200);
        data.setRageLockTicks(0);
        data.getPhase(0).barrier().setTimeoutTicks(125);
        data.getPhase(0).barrier().setFailDamage(10);
        FakePlayer player = player(helper, "barrier_damage", 2);
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        player.setHealth(100);
        npc.setTarget(player);
        npc.tick();
        TeleportPathController controller = controller(npc);
        helper.assertTrue(controller.isBarrierUp(), "the opening barrier must be armed");
        helper.runAfterDelay(101, () -> {
            controller.tick();
            helper.assertTrue(controller.isRageActive(), "the penalty fixture must be enraged");
        });
        helper.runAfterDelay(126, () -> {
            try {
                controller.tick();
                helper.assertFalse(controller.isBarrierUp(), "the barrier must expire");
                helper.assertTrue(Math.abs(player.getHealth() - 80) < 0.001F,
                        "10 damage at 200 percent rage must deal 20 damage, not 40; HP=" + player.getHealth());
                player.invulnerableTime = 0;
                player.hurt(helper.getLevel().damageSources().mobAttack(npc), 10);
                helper.assertTrue(Math.abs(player.getHealth() - 60) < 0.001F,
                        "a subsequent ordinary attack must still receive its own rage multiplier");
                helper.succeed();
            } finally {
                controller.shutdown();
                player.discard();
                npc.discard();
            }
        });
    }

    @GameTest(template = "fluid_platform")
    public static void openingBarrierUsesPartyHealth(GameTestHelper helper) throws ReflectiveOperationException {
        EntityNPCInterface npc = boss(helper);
        TeleportPathData data = settings(npc);
        data.setHealthScalingEnabled(true);
        data.setHealthPerPlayerPercent(100);
        data.getPhase(0).barrier().setPercent(50);
        FakePlayer first = player(helper, "barrier_first", 2);
        FakePlayer second = player(helper, "barrier_second", 3);
        npc.setTarget(first);
        try {
            npc.tick();
            TeleportPathController controller = controller(npc);
            helper.assertTrue(controller.scaledPlayerCount() == 2,
                    "both nearby players must count; actual=" + controller.scaledPlayerCount()
                            + ", allied=" + npc.isAlliedTo(second) + ", attack=" + npc.canAttack(second));
            helper.assertTrue(npc.getMaxHealth() == 200, "the party must double maximum health");
            helper.assertTrue(controller.barrierLeft() == 100,
                    "the opening 50 percent barrier must absorb 100; actual=" + controller.barrierLeft());
            helper.succeed();
        } finally {
            if (controller(npc) != null) controller(npc).shutdown();
            first.discard();
            second.discard();
            npc.discard();
        }
    }

    private static EntityNPCInterface boss(GameTestHelper helper) {
        EntityNPCInterface npc = CustomEntities.entityCustomNpc.create(helper.getLevel());
        npc.setPos(helper.absoluteVec(new Vec3(1, 2, 1)));
        npc.setNoAi(true);
        npc.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        npc.setHealth(100);
        TeleportPathData data = settings(npc);
        data.setEnabled(true);
        data.setPhaseCount(1);
        data.setTargetSearchRadius(4);
        data.setBossBarStyle("none");
        BossPhaseData phase = data.getPhase(0);
        phase.barrier().setEnabled(true);
        phase.barrier().setFailMode(BossPhaseData.BARRIER_FAIL_DAMAGE);
        return npc;
    }

    private static FakePlayer player(GameTestHelper helper, String name, int x) throws ReflectiveOperationException {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name)) {
            @Override
            public boolean isInvulnerableTo(DamageSource source) {
                return false;
            }
        };
        var protection = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
        protection.setAccessible(true);
        protection.setInt(player, 0);
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new Vec3(x, 2, 1)));
        helper.getLevel().addNewPlayer(player);
        for (var faction : noppes.npcs.controllers.FactionController.instance.factions.values()) {
            PlayerData.get(player).factionData.factionData.put(faction.id, -1000);
        }
        return player;
    }

    private static TeleportPathData settings(EntityNPCInterface npc) {
        return ((ITeleportPathData) npc.ais).cnpcgeckoaddon$getTeleportPathData();
    }

    private static TeleportPathController controller(EntityNPCInterface npc) {
        return ((IBossController) npc).cnpcgeckoaddon$getTeleportPathController();
    }
}

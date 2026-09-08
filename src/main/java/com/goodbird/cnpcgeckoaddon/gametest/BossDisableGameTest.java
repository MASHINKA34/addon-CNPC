package com.goodbird.cnpcgeckoaddon.gametest;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.ai.TeleportPathController;
import com.goodbird.cnpcgeckoaddon.ai.BossCaptureManager;
import com.goodbird.cnpcgeckoaddon.ai.BossTetherManager;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.mixin.IBossController;
import com.goodbird.cnpcgeckoaddon.mixin.ITeleportPathData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import noppes.npcs.CustomEntities;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.List;
import java.util.function.Consumer;

@GameTestHolder(CNPCGeckoAddon.MODID)
@PrefixGameTestTemplate(false)
public class BossDisableGameTest {
    @GameTest(template = "fluid_platform")
    public static void disablingReleasesCapturedAndTetheredVictims(GameTestHelper helper) {
        verifyVictimRelease(helper, npc -> {
            ((ITeleportPathData) npc.ais).cnpcgeckoaddon$getTeleportPathData().setEnabled(false);
            npc.tick();
        });
    }

    @GameTest(template = "fluid_platform")
    public static void deathReleasesCapturedAndTetheredVictims(GameTestHelper helper) {
        verifyVictimRelease(helper, npc -> {
            npc.hurt(helper.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
            helper.assertTrue(!npc.isAlive(), "the fixture boss must die");
        });
    }

    @GameTest(template = "fluid_platform")
    public static void unloadingReleasesCapturedAndTetheredVictims(GameTestHelper helper) {
        verifyVictimRelease(helper, npc -> npc.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK));
    }

    private static void verifyVictimRelease(GameTestHelper helper, Consumer<EntityNPCInterface> finish) {
        EntityNPCInterface npc = helper.spawn(CustomEntities.entityCustomNpc, new BlockPos(1, 2, 1));
        var captured = helper.spawn(EntityType.COW, new BlockPos(4, 2, 2));
        var tethered = helper.spawn(EntityType.COW, new BlockPos(6, 2, 4));
        npc.setNoAi(true);
        captured.setNoAi(true);
        tethered.setNoAi(true);
        TeleportPathData data = ((ITeleportPathData) npc.ais).cnpcgeckoaddon$getTeleportPathData();
        data.setEnabled(true);
        data.getPhase(0).capture().setEnabled(true);
        data.getPhase(0).tether().setEnabled(true);
        try {
            npc.tick();
            helper.assertTrue(BossCaptureManager.start(npc, captured, data.getPhase(0), 0, helper.getLevel().getGameTime()),
                    "the fixture must capture a victim");
            helper.assertTrue(BossTetherManager.start(helper.getLevel(), npc, List.of(tethered), data.getPhase(0),
                    0, 0, helper.getLevel().getGameTime()) == 1, "the fixture must tether a second victim");
            finish.accept(npc);
            helper.assertTrue(!BossCaptureManager.isCaptured(captured.getUUID())
                            && !BossTetherManager.isTethered(tethered.getUUID()),
                    "ending a boss runtime must immediately release every victim");
            helper.succeed();
        } finally {
            BossCaptureManager.releaseByBoss(npc);
            BossTetherManager.releaseByBoss(npc);
            npc.discard();
            captured.discard();
            tethered.discard();
        }
    }

    @GameTest(template = "fluid_platform")
    public static void disablingRestoresEveryNativeBossBarMode(GameTestHelper helper) {
        var target = helper.spawn(EntityType.COW, new BlockPos(4, 2, 2));
        target.setNoAi(true);
        try {
            for (int mode = 0; mode <= 2; mode++) {
                for (boolean fighting : new boolean[]{false, true}) {
                    EntityNPCInterface npc = CustomEntities.entityCustomNpc.create(helper.getLevel());
                    TeleportPathController controller = new TeleportPathController(npc);
                    try {
                        npc.display.setBossbar(mode);
                        npc.setTarget(fighting ? target : null);
                        helper.assertTrue(npc.getTarget() == (fighting ? target : null),
                                "the fixture must have its requested native combat target");
                        npc.bossInfo.setVisible(false);
                        controller.tick();
                        helper.assertTrue(npc.bossInfo.isVisible() == (mode == 1 || mode == 2 && fighting),
                                "disabling must restore native bossbar mode " + mode + " with combat=" + fighting
                                        + ", target=" + npc.getTarget() + ", alive=" + npc.isAlive());
                    } finally {
                        npc.discard();
                    }
                }
            }
        } finally {
            target.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "fluid_platform")
    public static void disabledControllersDetachAndReenableCleanly(GameTestHelper helper) {
        EntityNPCInterface npc = helper.spawn(CustomEntities.entityCustomNpc, new BlockPos(2, 2, 2));
        npc.setNoAi(true);
        TeleportPathData data = ((ITeleportPathData) npc.ais).cnpcgeckoaddon$getTeleportPathData();
        IBossController holder = (IBossController) npc;
        try {
            npc.display.setBossbar(1);
            data.setEnabled(true);
            npc.tick();
            TeleportPathController first = holder.cnpcgeckoaddon$getTeleportPathController();
            helper.assertTrue(first != null, "enabling must install a controller");
            data.setEnabled(false);
            npc.tick();
            helper.assertTrue(holder.cnpcgeckoaddon$getTeleportPathController() == null && npc.bossInfo.isVisible(),
                    "disabling must release the controller and restore the native bar");
            npc.tick();
            helper.assertTrue(holder.cnpcgeckoaddon$getTeleportPathController() == null && npc.bossInfo.isVisible(),
                    "subsequent disabled ticks must leave the native bar alone");
            data.setEnabled(true);
            npc.tick();
            helper.assertTrue(holder.cnpcgeckoaddon$getTeleportPathController() != null
                            && holder.cnpcgeckoaddon$getTeleportPathController() != first,
                    "reenabling must create a fresh controller");
            helper.succeed();
        } finally {
            npc.discard();
        }
    }
}

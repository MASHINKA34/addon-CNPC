package com.goodbird.cnpcgeckoaddon.gametest;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.ai.TeleportPathController;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.mixin.ITeleportPathData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import noppes.npcs.CustomEntities;
import noppes.npcs.entity.EntityNPCInterface;

@GameTestHolder(CNPCGeckoAddon.MODID)
@PrefixGameTestTemplate(false)
public class BossTelegraphRegressionGameTest {
    @GameTest(template = "fluid_platform", timeoutTicks = 80)
    public static void warningDelaysTheHitUntilPlayersCanReact(GameTestHelper helper) {
        checkWarning(helper, false);
    }

    @GameTest(template = "fluid_platform", timeoutTicks = 80)
    public static void leavingTheWarningZoneCancelsTheHit(GameTestHelper helper) {
        checkWarning(helper, true);
    }

    private static void checkWarning(GameTestHelper helper, boolean dodge) {
        EntityNPCInterface npc = CustomEntities.entityCustomNpc.create(helper.getLevel());
        npc.setPos(helper.absoluteVec(new Vec3(1, 2, 1)));
        npc.setNoAi(true);
        TeleportPathData data = ((ITeleportPathData) npc.ais).cnpcgeckoaddon$getTeleportPathData();
        data.setEnabled(true);
        data.setPhaseCount(1);
        data.setTelegraphEnabled(true);
        data.setTelegraphLeadTicks(20);
        data.setTelegraphDodge(true);
        data.setTelegraphAnnounce(false);
        data.setTelegraphSound(false);
        var attack = data.getPhase(0).areaAttack();
        attack.setEnabled(true);
        attack.setRadius(2);
        attack.setDamage(3);
        attack.setKnockback(0);
        attack.setCooldownTicks(1);
        attack.setActionDelayTicks(0);
        var target = helper.spawn(EntityType.COW, new BlockPos(2, 2, 1));
        target.setNoAi(true);
        target.setNoGravity(true);
        float before = target.getHealth();
        npc.setTarget(target);
        TeleportPathController controller = new TeleportPathController(npc);
        controller.tick();
        for (int tick = 1; tick <= 22; tick++) {
            helper.runAfterDelay(tick, controller::tick);
        }
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(target.getHealth() == before, "the warning must precede the damage");
            if (dodge) target.setPos(helper.absoluteVec(new Vec3(8, 2, 1)));
        });
        helper.runAfterDelay(23, () -> {
            try {
                float expected = dodge ? before : before - 3;
                helper.assertTrue(target.getHealth() == expected,
                        "the warning must resolve against the current victim position; HP=" + target.getHealth());
                helper.succeed();
            } finally {
                controller.shutdown();
                npc.discard();
                target.discard();
            }
        });
    }
}

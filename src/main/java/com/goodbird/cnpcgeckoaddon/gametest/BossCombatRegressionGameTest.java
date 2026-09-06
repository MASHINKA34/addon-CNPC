package com.goodbird.cnpcgeckoaddon.gametest;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.ai.TeleportPathController;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.mixin.IRangedData;
import com.goodbird.cnpcgeckoaddon.mixin.ITeleportPathData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.AABB;
import noppes.npcs.CustomEntities;
import noppes.npcs.entity.EntityNPCInterface;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CNPCGeckoAddon.MODID)
@PrefixGameTestTemplate(false)
public class BossCombatRegressionGameTest {
    @GameTest(template = "fluid_platform", timeoutTicks = 150)
    public static void firstPhaseInvulnerabilityStartsAgainAfterReset(GameTestHelper helper) {
        EntityNPCInterface npc = CustomEntities.entityCustomNpc.create(helper.getLevel());
        helper.assertTrue(npc != null, "the NPC must be constructible");
        npc.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(2, 2, 2)));
        TeleportPathData data = ((ITeleportPathData) npc.ais).cnpcgeckoaddon$getTeleportPathData();
        data.setEnabled(true);
        data.setCombatOnly(true);
        data.setResetTicks(20);
        data.setResetHeal(true);
        data.setPhaseCount(1);
        BossPhaseData phase = data.getPhase(0);
        phase.setInvulnerableEnabled(true);
        phase.setInvulnerableEndMode(BossPhaseData.INVULNERABLE_END_TIMER);
        phase.setInvulnerableDurationTicks(100);
        phase.setInvulnerableSummonImmediately(false);
        Cow target = helper.spawn(EntityType.COW, new BlockPos(3, 2, 2));
        target.setNoAi(true);
        TeleportPathController controller = new TeleportPathController(npc);
        controller.tick();
        helper.assertFalse(controller.isInvulnerable(), "an idle boss must not consume the opening window");
        npc.setTarget(target);
        controller.tick();
        helper.assertTrue(controller.isInvulnerable(), "the first pull must arm invulnerability");
        npc.setTarget(null);
        controller.tick();
        helper.runAfterDelay(21, () -> {
            try {
                controller.tick();
                helper.assertFalse(controller.isEncounterRunning(), "target loss must reset the encounter");
                npc.setTarget(target);
                controller.tick();
                helper.assertTrue(controller.isInvulnerable(), "a second pull must rearm phase one");
                helper.assertTrue(controller.invulnerableTicksLeft() == 100,
                        "the second pull must receive the full protection duration");
                helper.succeed();
            } finally {
                controller.shutdown();
                npc.discard();
            }
        });
    }

    @GameTest(template = "fluid_platform", timeoutTicks = 100)
    public static void fixCommandPreservesBossWithoutTeleportPath(GameTestHelper helper) {
        EntityNPCInterface npc = helper.spawn(CustomEntities.entityCustomNpc, new BlockPos(2, 2, 2));
        npc.setNoAi(true);
        TeleportPathData data = ((ITeleportPathData) npc.ais).cnpcgeckoaddon$getTeleportPathData();
        data.setEnabled(true);
        helper.assertTrue(npc.ais.getMovingPathSize() < 2, "the fixture must have no teleport route");
        helper.getLevel().getServer().getCommands().performPrefixedCommand(
                helper.getLevel().getServer().createCommandSourceStack().withSuppressedOutput(), "cnpcgecko fix");
        helper.assertTrue(data.isEnabled(), "fix must preserve boss abilities without a teleport route");
        npc.discard();
        helper.succeed();
    }

    @GameTest(template = "fluid_platform", timeoutTicks = 100)
    public static void customArrowKeepsDamageConfiguredAtLaunch(GameTestHelper helper) {
        EntityNPCInterface npc = CustomEntities.entityCustomNpc.create(helper.getLevel());
        helper.assertTrue(npc != null, "the NPC must be constructible");
        npc.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1, 2, 2)));
        ((IRangedData) npc.stats.ranged).getRangedExtraData().setProjectileEntity("minecraft:arrow");
        npc.stats.ranged.setStrength(7);
        npc.stats.ranged.setShotCount(1);
        Cow target = helper.spawn(EntityType.COW, new BlockPos(3, 2, 2));
        target.setNoAi(true);
        npc.performRangedAttack(target, 0);
        var arrows = helper.getLevel().getEntitiesOfClass(AbstractArrow.class,
                new AABB(helper.absolutePos(new BlockPos(1, 2, 2))).inflate(2), arrow -> arrow.getOwner() == npc);
        helper.assertTrue(arrows.size() == 1, "the configured arrow must be spawned");
        AbstractArrow arrow = arrows.getFirst();
        npc.stats.ranged.setStrength(1);
        float before = target.getHealth();
        target.hurt(helper.getLevel().damageSources().arrow(arrow, npc), 2);
        helper.assertTrue(Math.abs(before - target.getHealth() - 7) < 0.001F,
                "impact damage must use the launch snapshot instead of native or current NPC damage");
        arrow.discard();
        npc.discard();
        helper.succeed();
    }
}

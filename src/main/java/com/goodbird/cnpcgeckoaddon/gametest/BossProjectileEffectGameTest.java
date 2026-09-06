package com.goodbird.cnpcgeckoaddon.gametest;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.ai.BossDeathEvents;
import com.goodbird.cnpcgeckoaddon.data.BossEffectSet;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.entity.EntityBossBoulder;
import com.goodbird.cnpcgeckoaddon.entity.EntityFluidSpit;
import com.goodbird.cnpcgeckoaddon.mixin.IBossController;
import com.goodbird.cnpcgeckoaddon.mixin.ITeleportPathData;
import com.goodbird.cnpcgeckoaddon.registry.EntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import noppes.npcs.CustomEntities;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.EntityProjectile;

import java.util.List;

@GameTestHolder(CNPCGeckoAddon.MODID)
@PrefixGameTestTemplate(false)
public class BossProjectileEffectGameTest {
    @GameTest(template = "fluid_platform", timeoutTicks = 100)
    public static void projectilesKeepTheirLaunchPhaseEffects(GameTestHelper helper) {
        EntityNPCInterface npc = boss(helper, true);
        try {
            Projectile vanilla = new Snowball(EntityType.SNOWBALL, helper.getLevel());
            Projectile customNpc = new EntityProjectile(helper.getLevel(), npc, new ItemStack(Items.ARROW), false);
            Projectile fluid = new EntityFluidSpit(EntityRegistry.entityFluidSpit, npc, helper.getLevel());
            List<Projectile> projectiles = List.of(vanilla, customNpc, fluid);
            for (Projectile projectile : projectiles) {
                projectile.setOwner(npc);
                projectile.setPos(npc.position());
                helper.getLevel().addFreshEntity(projectile);
            }
            changePhase(helper, npc);
            for (Projectile projectile : projectiles) {
                LivingEntity victim = helper.spawn(EntityType.COW, new BlockPos(3, 2, 2));
                BossDeathEvents.onProjectileImpact(new ProjectileImpactEvent(projectile, new EntityHitResult(victim)));
                helper.assertTrue(victim.hasEffect(projectile instanceof EntityFluidSpit ? MobEffects.MOVEMENT_SLOWDOWN : MobEffects.POISON),
                        "impact must use the effects of the phase that launched " + projectile.getType());
                helper.assertFalse(victim.hasEffect(MobEffects.WEAKNESS), "the next phase must not alter a projectile in flight");
                victim.discard();
                projectile.discard();
            }
            helper.succeed();
        } finally {
            npc.discard();
        }
    }

    @GameTest(template = "fluid_platform", timeoutTicks = 100)
    public static void emptyLaunchEffectsStayEmptyAfterPhaseChange(GameTestHelper helper) {
        EntityNPCInterface npc = boss(helper, false);
        try {
            Snowball projectile = new Snowball(EntityType.SNOWBALL, helper.getLevel());
            projectile.setOwner(npc);
            projectile.setPos(npc.position());
            helper.getLevel().addFreshEntity(projectile);
            changePhase(helper, npc);
            LivingEntity victim = helper.spawn(EntityType.COW, new BlockPos(3, 2, 2));
            BossDeathEvents.onProjectileImpact(new ProjectileImpactEvent(projectile, new EntityHitResult(victim)));
            helper.assertTrue(victim.getActiveEffects().isEmpty(), "an empty launch snapshot must not inherit later effects");
            projectile.discard();
            helper.succeed();
        } finally {
            npc.discard();
        }
    }

    @GameTest(template = "fluid_platform", timeoutTicks = 100)
    public static void projectileEffectsSurviveEntityReload(GameTestHelper helper) {
        EntityNPCInterface npc = boss(helper, true);
        try {
            Snowball projectile = new Snowball(EntityType.SNOWBALL, helper.getLevel());
            projectile.setOwner(npc);
            projectile.setPos(npc.position());
            helper.getLevel().addFreshEntity(projectile);
            CompoundTag saved = projectile.saveWithoutId(new CompoundTag());
            projectile.discard();
            changePhase(helper, npc);
            Snowball reloaded = new Snowball(EntityType.SNOWBALL, helper.getLevel());
            reloaded.load(saved);
            BossDeathEvents.onProjectileJoinLevel(new EntityJoinLevelEvent(reloaded, helper.getLevel(), true));
            LivingEntity victim = helper.spawn(EntityType.COW, new BlockPos(3, 2, 2));
            BossDeathEvents.onProjectileImpact(new ProjectileImpactEvent(reloaded, new EntityHitResult(victim)));
            helper.assertTrue(victim.hasEffect(MobEffects.POISON), "entity NBT must preserve the launch snapshot");
            helper.assertFalse(victim.hasEffect(MobEffects.WEAKNESS), "loading an entity must not recapture the current phase");
            helper.succeed();
        } finally {
            npc.discard();
        }
    }

    @GameTest(template = "fluid_platform", timeoutTicks = 100)
    public static void boulderEffectsSurviveReloadAndSettingsChanges(GameTestHelper helper) {
        BossEffectSet effects = new BossEffectSet();
        enable(effects, "minecraft:slowness");
        effects.get(0).setDurationTicks(240);
        effects.get(0).setAmplifier(2);
        effects.get(0).setShowParticles(false);
        EntityBossBoulder boulder = new EntityBossBoulder(EntityRegistry.entityBossBoulder, helper.getLevel());
        boulder.configure(Blocks.STONE.defaultBlockState(), "", 10, 7, 0, true, 0, 0, "", effects);
        CompoundTag expected = effects.get(0).writeToNBT();
        enable(effects, "minecraft:poison");
        CompoundTag saved = boulder.saveWithoutId(new CompoundTag());
        EntityBossBoulder reloaded = new EntityBossBoulder(EntityRegistry.entityBossBoulder, helper.getLevel());
        reloaded.load(saved);
        CompoundTag roundTrip = reloaded.saveWithoutId(new CompoundTag());
        BossEffectSet restored = new BossEffectSet();
        restored.readFromNBT(roundTrip, "Effects");
        helper.assertTrue(restored.get(0).writeToNBT().equals(expected), "boulders must keep all launch effect settings across NBT reload");
        LivingEntity victim = helper.spawn(EntityType.COW, new BlockPos(3, 2, 2));
        restored.applyAll(victim, reloaded);
        helper.assertTrue(victim.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "the restored effect must remain applicable");
        saved.remove("Effects");
        reloaded.load(saved);
        BossEffectSet legacy = new BossEffectSet();
        legacy.readFromNBT(reloaded.saveWithoutId(new CompoundTag()), "Effects");
        helper.assertFalse(legacy.isAnyEnabled(), "old boulders without effect data must load with empty effects");
        helper.succeed();
    }

    private static EntityNPCInterface boss(GameTestHelper helper, boolean withEffects) {
        EntityNPCInterface npc = helper.spawn(CustomEntities.entityCustomNpc, new BlockPos(2, 2, 2));
        npc.setNoAi(true);
        TeleportPathData data = ((ITeleportPathData) npc.ais).cnpcgeckoaddon$getTeleportPathData();
        data.setEnabled(true);
        data.setCombatOnly(false);
        data.setPhaseCount(2);
        data.getPhase(1).setStartHealthPercent(50);
        if (withEffects) {
            enable(data.getPhase(0).getRangedAttackEffects(), "minecraft:poison");
            enable(data.getPhase(0).getFluidSpitEffects(), "minecraft:slowness");
        }
        enable(data.getPhase(1).getRangedAttackEffects(), "minecraft:weakness");
        enable(data.getPhase(1).getFluidSpitEffects(), "minecraft:weakness");
        npc.tick();
        var controller = ((IBossController) npc).cnpcgeckoaddon$getTeleportPathController();
        helper.assertTrue(controller != null && controller.activePhase() == data.getPhase(0), "the boss must begin in phase one");
        return npc;
    }

    private static void changePhase(GameTestHelper helper, EntityNPCInterface npc) {
        npc.setHealth(npc.getMaxHealth() * 0.25F);
        var controller = ((IBossController) npc).cnpcgeckoaddon$getTeleportPathController();
        controller.tick();
        TeleportPathData data = ((ITeleportPathData) npc.ais).cnpcgeckoaddon$getTeleportPathData();
        helper.assertTrue(controller.activePhase() == data.getPhase(1), "the boss must switch to phase two before impact");
    }

    private static void enable(BossEffectSet effects, String id) {
        effects.get(0).setEnabled(true);
        effects.get(0).setEffectId(id);
    }
}

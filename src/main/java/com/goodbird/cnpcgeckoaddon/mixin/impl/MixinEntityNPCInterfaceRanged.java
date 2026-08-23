package com.goodbird.cnpcgeckoaddon.mixin.impl;

import com.goodbird.cnpcgeckoaddon.ai.KeepDistanceGoal;
import com.goodbird.cnpcgeckoaddon.data.RangedExtraData;
import com.goodbird.cnpcgeckoaddon.mixin.IRangedData;
import com.goodbird.cnpcgeckoaddon.utils.ProjectileEntityUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataAI;
import noppes.npcs.entity.data.DataRanged;
import noppes.npcs.entity.data.DataStats;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityNPCInterface.class)
public abstract class MixinEntityNPCInterfaceRanged extends PathfinderMob implements RangedAttackMob {

    @Shadow(remap = false)
    public DataStats stats;

    @Shadow(remap = false)
    @Final
    public DataAI ais;

    @Shadow(remap = false)
    private int taskCount;

    protected MixinEntityNPCInterfaceRanged(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Unique
    private RangedExtraData cnpcgeckoaddon$rangedExtra() {
        return ((IRangedData) stats.ranged).getRangedExtraData();
    }

    @Unique
    private EntityType<?> cnpcgeckoaddon$projectileType() {
        String id = cnpcgeckoaddon$rangedExtra().getProjectileEntity();
        if (id == null || id.isEmpty()) {
            return null;
        }
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            return null;
        }
        return BuiltInRegistries.ENTITY_TYPE.getOptional(location).orElse(null);
    }

    @Inject(method = "performRangedAttack", at = @At("HEAD"), cancellable = true, remap = false)
    public void cnpcgeckoaddon$performCustomRangedAttack(LivingEntity target, float distanceFactor, CallbackInfo ci) {
        EntityType<?> type = cnpcgeckoaddon$projectileType();
        if (type == null || target == null || level().isClientSide) {
            return;
        }
        if (!ProjectileEntityUtil.isUsable(type)) {
            return;
        }
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        DataRanged ranged = stats.ranged;
        double velocity = Math.max(ranged.getSpeed(), 1) / 10.0D;
        float inaccuracy = (100 - Mth.clamp(ranged.getAccuracy(), 0, 100)) / 10.0F;
        int explodeSize = Mth.clamp(ranged.getExplodeSize(), 1, 4);
        boolean spawned = false;
        int shotCount = Mth.clamp(ranged.getShotCount(), 1, 32);
        for (int i = 0; i < shotCount; i++) {
            double x = npc.getX();
            double y = npc.getEyeY() - 0.2D;
            double z = npc.getZ();
            double dx = target.getX() - x;
            double dy = target.getY(0.5D) - y;
            double dz = target.getZ() - z;
            Entity entity;
            try {
                if (type == EntityType.FIREBALL) {
                    entity = new LargeFireball(level(), npc, new Vec3(dx, dy, dz).normalize(), explodeSize);
                } else {
                    entity = type.create(level());
                }
            } catch (Throwable e) {
                cnpcgeckoaddon$disableProjectile(type, npc, e);
                return;
            }
            if (!(entity instanceof Projectile projectile)) {
                cnpcgeckoaddon$safeDiscard(entity);
                cnpcgeckoaddon$disableProjectile(type, npc, null);
                return;
            }
            try {
                projectile.setOwner(npc);
                projectile.setPos(x, y, z);
                if (type != EntityType.FIREBALL) {
                    projectile.shoot(dx, dy, dz, (float) velocity, inaccuracy);
                    if (projectile instanceof AbstractHurtingProjectile hurting) {
                        hurting.accelerationPower = AbstractHurtingProjectile.INITAL_ACCELERATION_POWER;
                    }
                }
                if (!level().addFreshEntity(projectile)) {
                    throw new IllegalStateException("Projectile entity was rejected by the level");
                }
            } catch (Throwable e) {
                cnpcgeckoaddon$safeDiscard(projectile);
                cnpcgeckoaddon$disableProjectile(type, npc, e);
                return;
            }
            ProjectileEntityUtil.markUsable(type);
            spawned = true;
        }
        if (!spawned) {
            return;
        }
        SoundEvent sound = ranged.getSoundEvent(0);
        if (sound != null) {
            npc.playSound(sound, 2.0F, 1.0F);
        }
        ci.cancel();
    }

    @Unique
    private void cnpcgeckoaddon$disableProjectile(EntityType<?> type, EntityNPCInterface npc, Throwable error) {
        ProjectileEntityUtil.markUnusable(type, npc, error);
        cnpcgeckoaddon$rangedExtra().setProjectileEntity("");
        npc.updateClient();
    }

    @Unique
    private static void cnpcgeckoaddon$safeDiscard(Entity entity) {
        if (entity == null) {
            return;
        }
        try {
            entity.discard();
        } catch (Throwable ignored) {
            // A broken third-party entity must not take the NPC/server down while falling back.
        }
    }

    @Inject(method = "setResponse", at = @At("TAIL"), remap = false)
    private void cnpcgeckoaddon$addKeepDistanceGoal(CallbackInfo ci) {
        if (ais.onAttack != 0 || cnpcgeckoaddon$rangedExtra().getKeepDistance() <= 0) {
            return;
        }
        this.goalSelector.addGoal(taskCount++, new KeepDistanceGoal((EntityNPCInterface) (Object) this));
    }
}

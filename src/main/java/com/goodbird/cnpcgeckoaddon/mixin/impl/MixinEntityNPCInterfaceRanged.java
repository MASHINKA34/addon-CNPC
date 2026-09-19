package com.goodbird.cnpcgeckoaddon.mixin.impl;

import com.goodbird.cnpcgeckoaddon.ai.KeepDistanceGoal;
import com.goodbird.cnpcgeckoaddon.ai.NpcRangedAi;
import com.goodbird.cnpcgeckoaddon.ai.NpcRangedAttackGoal;
import com.goodbird.cnpcgeckoaddon.ai.NpcProjectileDamage;
import com.goodbird.cnpcgeckoaddon.data.RangedExtraData;
import com.goodbird.cnpcgeckoaddon.mixin.IRangedData;
import com.goodbird.cnpcgeckoaddon.utils.CrashGuard;
import com.goodbird.cnpcgeckoaddon.utils.NpcAimLead;
import com.goodbird.cnpcgeckoaddon.utils.ProjectileEntityUtil;
import com.goodbird.cnpcgeckoaddon.utils.ProjectileShotChoice;
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

@Mixin(value = EntityNPCInterface.class, priority = 1000)
public abstract class MixinEntityNPCInterfaceRanged extends PathfinderMob implements RangedAttackMob {

    /**
     * How often one attack may change its mind. Every volley that fails takes its entity type
     * off the list, so the choice only ever moves down it: custom, then fallback, then nothing.
     */
    @Unique
    private static final int cnpcgeckoaddon$MAX_SHOT_CHOICES = 3;

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

    /**
     * Decides what this attack is fired with before CustomNPCs gets to fire anything.
     *
     * <p>CustomNPCs is only left to shoot when the npc has an item to shoot. Its own
     * projectile made of an empty stack throws the moment it lands, so every other outcome
     * ends here with the attack cancelled - fired as an entity of the addon's, or not at all.</p>
     */
    @Inject(method = "performRangedAttack", at = @At("HEAD"), cancellable = true, remap = false)
    public void cnpcgeckoaddon$performCustomRangedAttack(LivingEntity target, float distanceFactor, CallbackInfo ci) {
        // Without a target CustomNPCs fails on its first line, before it has made a projectile.
        if (target == null || level().isClientSide) {
            return;
        }
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        // Whether a volley already went out: a failure after that must not let CustomNPCs fire
        // a second one on top. Before it, the attack is left to CustomNPCs the way it would be
        // without the addon - and an empty shot of theirs is caught by the projectile's guard.
        boolean fired = false;
        try {
            RangedExtraData extra = cnpcgeckoaddon$rangedExtra();
            for (int choices = 0; choices < cnpcgeckoaddon$MAX_SHOT_CHOICES; choices++) {
                ProjectileShotChoice choice = ProjectileEntityUtil.chooseShot(npc);
                if (choice == ProjectileShotChoice.CNPC) {
                    // CustomNPCs is about to build its own projectile out of the npc's item and
                    // send it at the target's feet; the lead and the fan are put on it as it
                    // joins the level, which is the first moment it exists to be turned.
                    NpcRangedAi.expectShot(npc, target);
                    return;
                }
                if (choice == ProjectileShotChoice.NONE) {
                    break;
                }
                boolean custom = choice == ProjectileShotChoice.CUSTOM;
                EntityType<?> type = ProjectileEntityUtil.getType(
                        custom ? extra.getProjectileEntity() : extra.getFallbackProjectile());
                if (type != null && cnpcgeckoaddon$fireVolley(type, npc, target, extra)) {
                    fired = true;
                    ci.cancel();
                    SoundEvent sound = stats.ranged.getSoundEvent(0);
                    if (sound != null) {
                        npc.playSound(sound, extra.getShotSoundVolumeTenths() / 10.0F,
                                extra.getShotSoundPitchTenths() / 10.0F);
                    }
                    return;
                }
                if (custom) {
                    cnpcgeckoaddon$dropProjectileEntity();
                }
            }
            ProjectileEntityUtil.warnNoShot(npc);
            ci.cancel();
        } catch (Throwable error) {
            CrashGuard.caught("mixin.npc.ranged_attack", error);
            if (fired && !ci.isCancelled()) {
                ci.cancel();
            }
        }
    }

    /**
     * Fires the npc's whole volley as entities of this type.
     *
     * @return false when the type let the npc down; it is marked unusable by then, so the next
     *         choice is made without it
     */
    @Unique
    private boolean cnpcgeckoaddon$fireVolley(EntityType<?> type, EntityNPCInterface npc,
                                              LivingEntity target, RangedExtraData extra) {
        DataRanged ranged = stats.ranged;
        // Speed is kept in tenths of a block per tick, the way the CustomNPCs editor shows it.
        double velocity = Math.max(ranged.getSpeed(), 1) / 10.0D;
        float inaccuracy = (100 - Mth.clamp(ranged.getAccuracy(), 0, 100)) / 10.0F;
        // Held to what the CustomNPCs editor itself offers rather than to a figure of ours:
        // a projectile it shows as "none" must not go off, and a count it accepts must fire.
        int explodeSize = Mth.clamp(ranged.getExplodeSize(),
                RangedExtraData.MIN_EXPLODE_SIZE, RangedExtraData.MAX_EXPLODE_SIZE);
        int shotCount = Mth.clamp(ranged.getShotCount(),
                RangedExtraData.MIN_SHOT_COUNT, RangedExtraData.MAX_SHOT_COUNT);
        // Where the shot is pointed: ahead of a target that is running, and one draw of the fan
        // for each projectile of the volley, so a volley of three is three shots and not one.
        Vec3 aim = NpcRangedAi.aimPoint(npc, target, extra);
        for (int i = 0; i < shotCount; i++) {
            Vec3 from = NpcRangedAi.muzzle(npc, extra);
            double x = from.x;
            double y = from.y;
            double z = from.z;
            Vec3 direction = NpcAimLead.spread(aim.subtract(from), extra.getSpreadDegrees(), npc.getRandom());
            double dx = direction.x;
            double dy = direction.y;
            double dz = direction.z;
            Entity entity;
            try {
                if (type == EntityType.FIREBALL) {
                    entity = new LargeFireball(level(), npc, new Vec3(dx, dy, dz).normalize(), explodeSize);
                } else {
                    entity = type.create(level());
                }
            } catch (Throwable e) {
                ProjectileEntityUtil.markUnusable(type, npc, e);
                return false;
            }
            if (!(entity instanceof Projectile projectile)) {
                cnpcgeckoaddon$safeDiscard(entity);
                ProjectileEntityUtil.markUnusable(type, npc, null);
                return false;
            }
            try {
                projectile.setOwner(npc);
                NpcProjectileDamage.configure(projectile, ranged.getStrength());
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
                ProjectileEntityUtil.markUnusable(type, npc, e);
                return false;
            }
            ProjectileEntityUtil.markUsable(type);
        }
        return true;
    }

    /**
     * Takes a broken projectile entity off this npc for good.
     *
     * <p>Deliberately does not sync the npc: this runs from inside {@code performRangedAttack},
     * and a caller may have swapped a setting of its own into {@code DataRanged} for the
     * duration of the shot - the boss controller puts its phase damage there and takes it back
     * out in a {@code finally}. Syncing from in here would publish that temporary value to
     * every client that has the editor open. The cleared id is in memory and goes out with the
     * npc's next save and sync; the reason it was cleared is already in the log.</p>
     */
    @Unique
    private void cnpcgeckoaddon$dropProjectileEntity() {
        cnpcgeckoaddon$rangedExtra().setProjectileEntity("");
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
        try {
            if (ais.onAttack != 0) {
                return;
            }
            EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
            if (cnpcgeckoaddon$rangedExtra().getKeepDistance() > 0) {
                this.goalSelector.addGoal(taskCount++, new KeepDistanceGoal(npc));
            }
            // Added whatever the switch says and asked on every use instead: the switch is edited
            // from a screen, and this list is only rebuilt when CustomNPCs decides to.
            this.goalSelector.addGoal(taskCount++, new NpcRangedAttackGoal(npc));
        } catch (Throwable error) {
            CrashGuard.caught("mixin.npc.keep_distance_goal", error);
        }
    }
}

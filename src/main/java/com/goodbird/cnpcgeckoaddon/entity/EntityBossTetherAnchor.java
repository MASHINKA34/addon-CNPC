package com.goodbird.cnpcgeckoaddon.entity;

import com.goodbird.cnpcgeckoaddon.registry.EntityRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;

/**
 * The stake a tether to a spot is tied to.
 *
 * <p>The beam is drawn between two entities, so a leash tied to a point on the floor needs
 * one standing there, and this is the least an entity can be: no shape to draw, no physics,
 * and never written into the chunk it stands in, so a server stopped mid-fight comes back
 * without it. It keeps its own clock as well. The manager renews the lease every tick the
 * leash holds, and a stake nobody is renewing - its manager emptied by an unload, say - takes
 * itself out of the arena within two seconds rather than standing there for good.</p>
 */
public final class EntityBossTetherAnchor extends Entity {
    /** How long the stake outlives its last renewal. Two seconds is a whole manager gone. */
    private static final int LEASE_TICKS = 40;

    private int leaseTicks = LEASE_TICKS;

    public EntityBossTetherAnchor(EntityType<? extends EntityBossTetherAnchor> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setInvisible(true);
        setSilent(true);
    }

    /** Plants a stake on {@code spot}, or answers null when the level would not take it. */
    public static EntityBossTetherAnchor plant(ServerLevel level, Vec3 spot) {
        EntityBossTetherAnchor stake = new EntityBossTetherAnchor(EntityRegistry.entityBossTetherAnchor, level);
        stake.setPos(spot);
        return level.addFreshEntity(stake) ? stake : null;
    }

    /** Another tick of the leash it belongs to; without these it discards itself. */
    public void renewLease() {
        leaseTicks = LEASE_TICKS;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && --leaseTicks <= 0) {
            discard();
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    /** Never: a stake in a save file is a stake still standing after the restart. */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }
}

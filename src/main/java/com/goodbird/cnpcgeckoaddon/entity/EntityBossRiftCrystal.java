package com.goodbird.cnpcgeckoaddon.entity;

import com.goodbird.cnpcgeckoaddon.ai.BossRiftCrystalPlacement;
import com.goodbird.cnpcgeckoaddon.ai.BossRiftManager;
import com.goodbird.cnpcgeckoaddon.data.BossRiftSettings;
import com.goodbird.cnpcgeckoaddon.registry.EntityRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * One crystal of a reality rift: a block hanging over the floor of its zone, painted in the
 * rift's colour, turning and bobbing, waiting to be collected.
 *
 * <p>An entity rather than a block in the world because it hovers, turns and glows, and because
 * the rift dimension's platforms are left standing between fights: a crystal left in the blocks
 * of a platform would still be there next time, and taking it away again would mean editing
 * somebody's arena. It carries nothing of the mechanic - the collecting is the rift manager's
 * tick - and nothing but a block id, a colour and four numbers about how it moves, so the look
 * could be swapped for a model later by replacing its renderer alone.</p>
 *
 * <p>Never written into the chunk it stands in: the rift keeps its crystals by UUID in memory,
 * a save holds none of them, and one that somehow comes back from a disk is kept out on the way
 * in. A crystal whose rift is no longer open takes itself out of the level within five seconds
 * rather than hanging there for good.</p>
 */
public final class EntityBossRiftCrystal extends Entity {

    /** How long a crystal outlives the rift that stood it up, if one ever escapes the cleanup. */
    public static final int ORPHAN_TICKS = 100;

    private static final EntityDataAccessor<Integer> BLOCK_STATE =
            SynchedEntityData.defineId(EntityBossRiftCrystal.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COLOR =
            SynchedEntityData.defineId(EntityBossRiftCrystal.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> GLOW =
            SynchedEntityData.defineId(EntityBossRiftCrystal.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> SPIN_DEGREES =
            SynchedEntityData.defineId(EntityBossRiftCrystal.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BOB_TENTHS =
            SynchedEntityData.defineId(EntityBossRiftCrystal.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BOB_PERIOD =
            SynchedEntityData.defineId(EntityBossRiftCrystal.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SCALE_TENTHS =
            SynchedEntityData.defineId(EntityBossRiftCrystal.class, EntityDataSerializers.INT);
    /** The tick it was stood up on, so every client works the turn and the bob out for itself. */
    private static final EntityDataAccessor<Long> START_TICK =
            SynchedEntityData.defineId(EntityBossRiftCrystal.class, EntityDataSerializers.LONG);

    /** Whose rift stood it up; server-side only, the way the rift's own tables are. */
    private UUID bossId;
    /** Ticks this crystal has been standing with no rift of its own to belong to. */
    private int orphanTicks;

    public EntityBossRiftCrystal(EntityType<? extends EntityBossRiftCrystal> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setSilent(true);
    }

    /**
     * Stands one crystal up.
     *
     * @param spot     where it hovers, already worked out by {@link BossRiftCrystalPlacement}
     * @param block    what it is drawn as, the zone's own block or the rift's, already resolved
     * @param settings the rift's own look, for everything the zone did not override
     * @return the crystal, in the world; or null when the level would not take it
     */
    public static EntityBossRiftCrystal place(ServerLevel level, UUID bossId, BossRiftCrystalPlacement.Spot spot,
                                              BlockState block, BossRiftSettings settings) {
        EntityBossRiftCrystal crystal = new EntityBossRiftCrystal(EntityRegistry.entityBossRiftCrystal, level);
        crystal.bossId = bossId;
        crystal.setPos(spot.x(), spot.y(), spot.z());
        crystal.configure(block, spot.color(), settings, level.getGameTime());
        return level.addFreshEntity(crystal) ? crystal : null;
    }

    private void configure(BlockState block, int color, BossRiftSettings settings, long gameTime) {
        entityData.set(BLOCK_STATE, Block.getId(block));
        entityData.set(COLOR, Mth.clamp(color, 0, BossRiftSettings.MAX_COLOR));
        entityData.set(GLOW, settings.isCrystalGlow());
        entityData.set(SPIN_DEGREES, settings.getCrystalSpinDegrees());
        entityData.set(BOB_TENTHS, settings.getCrystalBobTenths());
        entityData.set(BOB_PERIOD, settings.getCrystalBobPeriodTicks());
        entityData.set(SCALE_TENTHS, settings.getCrystalScaleTenths());
        entityData.set(START_TICK, gameTime);
        refreshDimensions();
    }

    /** Whose rift this crystal belongs to, or null for one that was never told. */
    public UUID bossId() {
        return bossId;
    }

    /** The block the renderer draws; a state that is not a block any more falls back to amethyst. */
    public BlockState blockState() {
        BlockState state = Block.stateById(entityData.get(BLOCK_STATE));
        return state.isAir() ? Blocks.AMETHYST_BLOCK.defaultBlockState() : state;
    }

    /** The tint the block is drawn through, as 0xRRGGBB. */
    public int tint() {
        return entityData.get(COLOR);
    }

    public boolean glows() {
        return entityData.get(GLOW);
    }

    public int spinDegrees() {
        return entityData.get(SPIN_DEGREES);
    }

    public int bobTenths() {
        return entityData.get(BOB_TENTHS);
    }

    public int bobPeriodTicks() {
        return entityData.get(BOB_PERIOD);
    }

    /** How wide the drawn block is, in blocks. */
    public float scale() {
        return entityData.get(SCALE_TENTHS) / 10.0F;
    }

    public long startTick() {
        return entityData.get(START_TICK);
    }

    /** How long this crystal has been standing, for the turn and the bob, partial tick and all. */
    public double age(float partialTick) {
        return level().getGameTime() - startTick() + partialTick;
    }

    /** How far round it has turned by now, in degrees. */
    public float spinAngle(float partialTick) {
        return BossRiftCrystalPlacement.spinAngle(age(partialTick), spinDegrees());
    }

    /** How far off its resting height it is bobbing right now, in blocks. */
    public double bobOffset(float partialTick) {
        return BossRiftCrystalPlacement.bobOffset(age(partialTick), bobTenths(), bobPeriodTicks());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(BLOCK_STATE, Block.getId(Blocks.AMETHYST_BLOCK.defaultBlockState()));
        builder.define(COLOR, BossRiftSettings.DEFAULT_CRYSTAL_COLOR);
        builder.define(GLOW, true);
        builder.define(SPIN_DEGREES, 3);
        builder.define(BOB_TENTHS, 3);
        builder.define(BOB_PERIOD, 40);
        builder.define(SCALE_TENTHS, 10);
        builder.define(START_TICK, 0L);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        float size = Math.max(0.1F, scale());
        return EntityDimensions.scalable(size, size);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        // The client learns the size after the spawn packet, so the box has to follow it.
        if (SCALE_TENTHS.equals(accessor)) {
            refreshDimensions();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            // Nothing moves it: the turn and the bob are drawn from the start tick, so a client
            // needs no update at all after the spawn.
            return;
        }
        setDeltaMovement(Vec3.ZERO);
        // A crystal its rift no longer counts is a crystal nothing will ever collect: whatever
        // slipped past the cleanup - a level that came back without its rift, say - goes here.
        if (BossRiftManager.isLiveCrystal(getUUID())) {
            orphanTicks = 0;
        } else if (++orphanTicks > ORPHAN_TICKS) {
            discard();
        }
    }

    /** Never: a crystal in a save file is a crystal left over from a rift that died with the server. */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    /** Untouchable: the crystal is collected by being walked up to, not by being hit. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
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

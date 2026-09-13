package com.goodbird.cnpcgeckoaddon.entity;

import com.goodbird.cnpcgeckoaddon.data.BossFluidSpitSettings;
import com.goodbird.cnpcgeckoaddon.utils.BossProjectileTuning;
import com.goodbird.cnpcgeckoaddon.utils.PersistentDataUtil;
import com.goodbird.cnpcgeckoaddon.world.TemporaryFluidStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * The glob a boss spits. On impact it drops a short-lived puddle of the configured fluid
 * through {@link TemporaryFluidStore}, so nothing it does to the world is permanent.
 */
public class EntityFluidSpit extends ThrowableProjectile {
    private static final EntityDataAccessor<Integer> FLUID_STATE =
            SynchedEntityData.defineId(EntityFluidSpit.class, EntityDataSerializers.INT);
    /**
     * Thousandths of a block a tick the glob falls by.
     *
     * <p>Synced rather than kept to the server, unlike the three below it: a thrown projectile
     * runs its own physics on the client between position updates, and a client still pulling
     * the glob down by the old fifth of a block would draw an arc the server is forever
     * correcting.</p>
     */
    private static final EntityDataAccessor<Integer> GRAVITY =
            SynchedEntityData.defineId(EntityFluidSpit.class, EntityDataSerializers.INT);

    private static final String FLUID_KEY = "GeckoFluidState";
    private static final String LIFETIME_KEY = "GeckoFluidLifetime";
    private static final String RADIUS_KEY = "GeckoFluidRadius";
    private static final String DAMAGE_KEY = "GeckoFluidDamage";

    /** What a glob carrying none of the boss' numbers does, which is what it always did. */
    private static final int DEFAULT_GRAVITY_THOUSANDTHS = 50;
    private static final int DEFAULT_LIFE_TICKS = 200;
    private static final int DEFAULT_SPLASH_BASE = 12;
    private static final int DEFAULT_SPLASH_PER_RADIUS = 8;

    private int fluidLifetimeTicks = 60;
    private int puddleRadius = 1;
    private float impactDamage;
    /** Guards against the projectile living forever when it never hits anything. */
    private int age;
    private int projectileLifeTicks = DEFAULT_LIFE_TICKS;
    private int splashBase = DEFAULT_SPLASH_BASE;
    private int splashPerRadius = DEFAULT_SPLASH_PER_RADIUS;
    /** Whether the numbers the boss wrote on this glob have been read off it yet. */
    private boolean tuned;

    public EntityFluidSpit(EntityType<? extends EntityFluidSpit> type, Level level) {
        super(type, level);
    }

    public EntityFluidSpit(EntityType<? extends EntityFluidSpit> type, LivingEntity shooter, Level level) {
        super(type, shooter, level);
    }

    public void configure(BlockState fluid, int lifetimeTicks, int radius, float damage) {
        this.entityData.set(FLUID_STATE, Block.getId(fluid));
        this.fluidLifetimeTicks = Math.max(lifetimeTicks, 1);
        this.puddleRadius = Mth.clamp(radius, 0, 4);
        this.impactDamage = Math.max(damage, 0.0F);
    }

    public BlockState getFluidState() {
        BlockState state = Block.stateById(this.entityData.get(FLUID_STATE));
        return state.getFluidState().isEmpty() ? TemporaryFluidStore.defaultFluid() : state;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(FLUID_STATE, Block.getId(TemporaryFluidStore.defaultFluid()));
        builder.define(GRAVITY, DEFAULT_GRAVITY_THOUSANDTHS);
    }

    /**
     * Reads the numbers the boss wrote on this glob when it spat it, once.
     *
     * <p>They ride along as persistent data rather than as save keys of this class, so the
     * boss can set them at the spawn without the glob having to be told twice - and NeoForge
     * carries them through a reload with the entity for free. The gravity is copied into the
     * synced slot on the way past, which is the only one of the four the client needs.</p>
     */
    private void readTuning() {
        if (tuned) {
            return;
        }
        tuned = true;
        CompoundTag data = PersistentDataUtil.read(this);
        projectileLifeTicks = BossProjectileTuning.read(data, BossProjectileTuning.LIFE_TICKS,
                DEFAULT_LIFE_TICKS, BossFluidSpitSettings.MIN_PROJECTILE_LIFE,
                BossFluidSpitSettings.MAX_PROJECTILE_LIFE);
        splashBase = BossProjectileTuning.read(data, BossProjectileTuning.SPLASH_BASE,
                DEFAULT_SPLASH_BASE, 0, BossFluidSpitSettings.MAX_SPLASH_BASE);
        splashPerRadius = BossProjectileTuning.read(data, BossProjectileTuning.SPLASH_PER_RADIUS,
                DEFAULT_SPLASH_PER_RADIUS, 0, BossFluidSpitSettings.MAX_SPLASH_PER_RADIUS);
        if (!level().isClientSide) {
            this.entityData.set(GRAVITY, BossProjectileTuning.read(data, BossProjectileTuning.GRAVITY,
                    DEFAULT_GRAVITY_THOUSANDTHS, 0, BossFluidSpitSettings.MAX_GRAVITY));
        }
    }

    @Override
    protected double getDefaultGravity() {
        readTuning();
        return this.entityData.get(GRAVITY) / 1000.0D;
    }

    @Override
    public void tick() {
        readTuning();
        super.tick();
        if (level().isClientSide) {
            spawnTrailParticles();
            return;
        }
        // A spit that flies off into unloaded terrain must not linger as a ticking entity.
        if (++age > projectileLifeTicks) {
            discard();
        }
    }

    private void spawnTrailParticles() {
        BlockParticleOption particle = new BlockParticleOption(ParticleTypes.BLOCK, getFluidState());
        for (int i = 0; i < 2; i++) {
            level().addParticle(particle,
                    getX() + (random.nextDouble() - 0.5D) * 0.3D,
                    getY() + (random.nextDouble() - 0.5D) * 0.3D,
                    getZ() + (random.nextDouble() - 0.5D) * 0.3D,
                    0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && target != getOwner();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (level().isClientSide) {
            return;
        }
        Entity target = result.getEntity();
        if (impactDamage > 0.0F) {
            Entity owner = getOwner();
            target.hurt(owner instanceof LivingEntity living
                    ? damageSources().mobProjectile(this, living)
                    : damageSources().thrown(this, owner), impactDamage);
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level().isClientSide) {
            return;
        }
        splash(BlockPos.containing(result.getLocation()));
        discard();
    }

    private void splash(BlockPos center) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        TemporaryFluidStore store = TemporaryFluidStore.get(serverLevel);
        BlockState fluid = getFluidState();
        int radius = puddleRadius;
        // A flat disc reads much better than a sphere for a puddle, so the vertical
        // spread is deliberately kept to the impact layer plus one block below.
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z > radius * radius) {
                    continue;
                }
                for (int y = 0; y >= -1; y--) {
                    BlockPos pos = center.offset(x, y, z);
                    if (store.place(serverLevel, pos, fluid, fluidLifetimeTicks)) {
                        break;
                    }
                }
            }
        }
        serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, fluid),
                center.getX() + 0.5D, center.getY() + 0.5D, center.getZ() + 0.5D,
                splashBase + radius * splashPerRadius,
                0.3D + radius * 0.2D, 0.2D, 0.3D + radius * 0.2D, 0.05D);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt(FLUID_KEY, this.entityData.get(FLUID_STATE));
        tag.putInt(LIFETIME_KEY, fluidLifetimeTicks);
        tag.putInt(RADIUS_KEY, puddleRadius);
        tag.putFloat(DAMAGE_KEY, impactDamage);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(FLUID_KEY)) {
            this.entityData.set(FLUID_STATE, tag.getInt(FLUID_KEY));
        }
        if (tag.contains(LIFETIME_KEY)) {
            fluidLifetimeTicks = Math.max(tag.getInt(LIFETIME_KEY), 1);
        }
        if (tag.contains(RADIUS_KEY)) {
            puddleRadius = Mth.clamp(tag.getInt(RADIUS_KEY), 0, 4);
        }
        if (tag.contains(DAMAGE_KEY)) {
            impactDamage = Math.max(tag.getFloat(DAMAGE_KEY), 0.0F);
        }
    }
}

package com.goodbird.cnpcgeckoaddon.entity;

import com.goodbird.cnpcgeckoaddon.ai.BossAbilityDamageUtil;
import com.goodbird.cnpcgeckoaddon.ai.BossAreaVfxScheduler;
import com.goodbird.cnpcgeckoaddon.ai.TeleportPathController;
import com.goodbird.cnpcgeckoaddon.data.AreaVfxStyles;
import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossEffectSet;
import com.goodbird.cnpcgeckoaddon.mixin.IBossController;
import com.goodbird.cnpcgeckoaddon.utils.AnimationFileUtil;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.HashSet;
import java.util.Set;

/**
 * The stone a boss rolls or throws down a committed corridor.
 *
 * <p>The mechanic lives entirely here on the server: a fixed flat direction, a floor-hugging
 * roll or a plain arc, and one hit per victim per flight. The entity itself carries nothing
 * visual beyond a block id and a size, so the renderer can be swapped for a real model
 * without this class changing.</p>
 *
 * <p>It never touches the world: no blocks broken, no fire, no explosion - it breaks against
 * the arena, never the other way round.</p>
 */
public class EntityBossBoulder extends Projectile {
    private static final EntityDataAccessor<Integer> BLOCK_STATE =
            SynchedEntityData.defineId(EntityBossBoulder.class, EntityDataSerializers.INT);
    /** Diameter in tenths of a block; the hitbox and the drawn cube both read it. */
    private static final EntityDataAccessor<Integer> SCALE_TENTHS =
            SynchedEntityData.defineId(EntityBossBoulder.class, EntityDataSerializers.INT);

    public static final int MIN_SCALE_TENTHS = 5;
    public static final int MAX_SCALE_TENTHS = 40;

    /** How exactly one stair a rolling boulder climbs; anything taller is a wall. */
    private static final double STEP_HEIGHT = 1.0D;
    private static final double ROLL_GRAVITY = 0.08D;
    private static final double MAX_FALL_SPEED = 1.5D;
    /** A roll that has dropped this far without floor has left the arena, not crossed it. */
    private static final double MAX_PIT_DEPTH = 16.0D;
    private static final double THROW_GRAVITY = 0.05D;

    private static final String BLOCK_KEY = "GeckoBoulderBlock";
    private static final String SCALE_KEY = "GeckoBoulderScale";
    private static final String ROLLS_KEY = "GeckoBoulderRolls";
    private static final String DIR_X_KEY = "GeckoBoulderDirX";
    private static final String DIR_Z_KEY = "GeckoBoulderDirZ";
    private static final String SPEED_KEY = "GeckoBoulderSpeed";
    private static final String RANGE_KEY = "GeckoBoulderRange";
    private static final String TRAVELED_KEY = "GeckoBoulderTraveled";
    private static final String DAMAGE_KEY = "GeckoBoulderDamage";
    private static final String KNOCKBACK_KEY = "GeckoBoulderKnockback";
    private static final String STOPS_KEY = "GeckoBoulderStops";
    private static final String SHATTER_RADIUS_KEY = "GeckoBoulderShatterRadius";
    private static final String SHATTER_DAMAGE_KEY = "GeckoBoulderShatterDamage";
    private static final String VFX_KEY = "GeckoBoulderVfx";

    private boolean rolling = true;
    private Vec3 direction = new Vec3(0.0D, 0.0D, 1.0D);
    /** Blocks per tick along {@link #direction}. */
    private double speed = 0.6D;
    private double range = 20.0D;
    private double traveled;
    private int hitDamage;
    private int hitKnockback;
    private boolean stopsOnHit;
    private int shatterRadius;
    private int shatterDamage;
    private String vfx = AreaVfxStyles.NONE;
    /** Not persisted: a boulder outliving a world reload just loses its potions. */
    private BossEffectSet effects;

    /** Everyone already clipped this flight, so a slow roll cannot grind one victim down. */
    private final Set<Integer> struckIds = new HashSet<>();
    /** Downward speed: the roll's fall between ledges, or the arc's vertical half. */
    private double verticalSpeed;
    /** Floorless blocks fallen so far; reset by every landing. */
    private double fallenDepth;
    /** Guards against a boulder living forever when it never reaches anything. */
    private int age;
    private int maxAgeTicks = 200;

    /** Client-only spin, accumulated from how far the stone actually moved. */
    private float rollAngle;
    private float rollAngleO;

    public EntityBossBoulder(EntityType<? extends EntityBossBoulder> type, Level level) {
        super(type, level);
    }

    /** @return the block state behind {@code id}, or null when the id is unknown or air */
    public static BlockState resolveBlock(String id) {
        ResourceLocation location = AnimationFileUtil.parse(id);
        if (location == null) {
            return null;
        }
        Block block = BuiltInRegistries.BLOCK.getOptional(location).orElse(null);
        return block == null || block.defaultBlockState().isAir() ? null : block.defaultBlockState();
    }

    public void configure(BlockState block, int scaleTenths, int damage, int knockback,
                          boolean stops, int shatterRadiusBlocks, int shatterDamageAmount,
                          String vfxStyle, BossEffectSet effectSet) {
        this.entityData.set(BLOCK_STATE, Block.getId(block));
        this.entityData.set(SCALE_TENTHS, Mth.clamp(scaleTenths, MIN_SCALE_TENTHS, MAX_SCALE_TENTHS));
        refreshDimensions();
        hitDamage = Math.max(damage, 0);
        hitKnockback = Mth.clamp(knockback, 0, 10);
        stopsOnHit = stops;
        shatterRadius = Mth.clamp(shatterRadiusBlocks, 0, 16);
        shatterDamage = Math.max(shatterDamageAmount, 0);
        vfx = AreaVfxStyles.normalize(vfxStyle);
        effects = effectSet;
    }

    /** Sends the boulder rolling flat along {@code axis} for at most {@code rangeBlocks}. */
    public void launchRoll(Vec3 axis, int speedTenths, double rangeBlocks) {
        rolling = true;
        aim(axis, speedTenths, rangeBlocks);
        setDeltaMovement(direction.scale(speed));
        maxAgeTicks = travelBudgetTicks();
    }

    /**
     * Throws the boulder in an arc pitched to come down about {@code rangeBlocks} out on
     * flat ground; whatever it meets earlier breaks it there instead.
     */
    public void launchThrow(Vec3 axis, int speedTenths, double rangeBlocks) {
        rolling = false;
        aim(axis, speedTenths, rangeBlocks);
        int flightTicks = Math.max(2, Mth.ceil(range / speed));
        // Discrete ballistics: the arc loses THROW_GRAVITY each tick, so this starting rise
        // is what brings it back to the launch height after exactly flightTicks steps.
        verticalSpeed = THROW_GRAVITY * (flightTicks - 1) / 2.0D;
        setDeltaMovement(direction.x * speed, verticalSpeed, direction.z * speed);
        maxAgeTicks = travelBudgetTicks();
    }

    private void aim(Vec3 axis, int speedTenths, double rangeBlocks) {
        Vec3 flat = new Vec3(axis.x, 0.0D, axis.z);
        direction = flat.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
        speed = Mth.clamp(speedTenths, 1, 20) / 10.0D;
        range = Math.max(rangeBlocks, 1.0D);
        float yaw = (float) (Mth.atan2(direction.z, direction.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yRotO = yaw;
    }

    /** Twice the straight-line flight plus slack, so a snagged boulder still dies on its own. */
    private int travelBudgetTicks() {
        return Mth.clamp(Mth.ceil(range / speed) * 2 + 60, 60, 600);
    }

    public BlockState getBlockState() {
        BlockState state = Block.stateById(this.entityData.get(BLOCK_STATE));
        return state.isAir() ? Blocks.STONE.defaultBlockState() : state;
    }

    /** Diameter in blocks: hitbox, drawn cube and spin radius all share it. */
    public float diameter() {
        return this.entityData.get(SCALE_TENTHS) / 10.0F;
    }

    public float rollAngle(float partialTick) {
        return Mth.lerp(partialTick, rollAngleO, rollAngle);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(BLOCK_STATE, Block.getId(Blocks.STONE.defaultBlockState()));
        builder.define(SCALE_TENTHS, 15);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        float size = diameter();
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
            clientTick();
            return;
        }
        // The boulder is the boss doing something; without its boss it is clutter, and there
        // is nobody left to credit the damage to either.
        if (!(getOwner() instanceof EntityNPCInterface boss) || !boss.isAlive()) {
            vanish();
            return;
        }
        if (++age > maxAgeTicks) {
            vanish();
            return;
        }
        if (rolling) {
            tickRoll(boss);
        } else {
            tickThrow(boss);
        }
    }

    /** Integrates the last known motion so the stone glides between server updates. */
    private void clientTick() {
        Vec3 motion = getDeltaMovement();
        rollAngleO = rollAngle;
        double horizontal = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        // v = omega * r: the spin is exactly what a stone this size needs to roll without
        // slipping, which is what sells the block as rolling rather than sliding.
        rollAngle += (float) (horizontal / Math.max(diameter() * 0.5D, 0.1D) * Mth.RAD_TO_DEG);
        setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);
        if (horizontal > 1.0E-4D && random.nextInt(3) == 0) {
            level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, getBlockState()),
                    getX() + (random.nextDouble() - 0.5D) * diameter(), getY() + 0.1D,
                    getZ() + (random.nextDouble() - 0.5D) * diameter(), 0.0D, 0.0D, 0.0D);
        }
    }

    private void tickRoll(EntityNPCInterface boss) {
        double stepX = direction.x * speed;
        double stepZ = direction.z * speed;
        AABB before = getBoundingBox();
        AABB moved = before.move(stepX, 0.0D, stepZ);
        double lift = 0.0D;
        if (!level().noCollision(this, moved)) {
            AABB stepped = moved.move(0.0D, STEP_HEIGHT, 0.0D);
            // Only a grounded boulder climbs; one mid-fall meeting an edge has hit a wall.
            if (verticalSpeed <= 0.0D && level().noCollision(this, stepped)) {
                moved = stepped;
                lift = STEP_HEIGHT;
            } else {
                shatter(boss, position());
                return;
            }
        }

        double fall = 0.0D;
        if (lift > 0.0D) {
            verticalSpeed = 0.0D;
            fallenDepth = 0.0D;
        } else {
            verticalSpeed = Math.min(verticalSpeed + ROLL_GRAVITY, MAX_FALL_SPEED);
            fall = resolveFall(moved, verticalSpeed);
            if (fall < verticalSpeed) {
                // Something took the landing, so the next drop starts from rest again.
                verticalSpeed = 0.0D;
                fallenDepth = 0.0D;
            } else {
                fallenDepth += fall;
                // A hole this deep is not part of any arena floor: the boulder is gone, not
                // rolling, and must not keep ticking its way down for ever.
                if (fallenDepth > MAX_PIT_DEPTH) {
                    vanish();
                    return;
                }
            }
            moved = moved.move(0.0D, -fall, 0.0D);
        }

        setPos((moved.minX + moved.maxX) * 0.5D, moved.minY, (moved.minZ + moved.maxZ) * 0.5D);
        setDeltaMovement(stepX, lift - fall, stepZ);
        if (runOver(boss, before.minmax(moved))) {
            return;
        }
        traveled += speed;
        if (traveled >= range) {
            // Rolled the whole promised corridor: it breaks apart where the warning ended.
            shatter(boss, position());
        }
    }

    /**
     * How far of {@code want} the box really has beneath it before something solid.
     *
     * @return {@code want} itself while the drop is clear - the boulder is still falling
     */
    private double resolveFall(AABB box, double want) {
        if (level().noCollision(this, box.move(0.0D, -want, 0.0D))) {
            return want;
        }
        double clear = 0.0D;
        double step = want * 0.5D;
        // Eight halvings of at most 1.5 blocks land within a couple of millimetres, which
        // is close enough for a stone that is about to be drawn resting on the floor.
        for (int i = 0; i < 8; i++) {
            double attempt = clear + step;
            if (level().noCollision(this, box.move(0.0D, -attempt, 0.0D))) {
                clear = attempt;
            }
            step *= 0.5D;
        }
        return clear;
    }

    private void tickThrow(EntityNPCInterface boss) {
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() == HitResult.Type.ENTITY) {
            Entity victim = ((EntityHitResult) hit).getEntity();
            if (victim instanceof LivingEntity living) {
                strike(boss, living);
            }
            // A thrown stone has no second act: whatever it met first breaks it there.
            shatter(boss, hit.getLocation());
            return;
        }
        if (hit.getType() == HitResult.Type.BLOCK) {
            shatter(boss, hit.getLocation());
            return;
        }
        Vec3 motion = getDeltaMovement();
        setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);
        traveled += Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        setDeltaMovement(motion.x, motion.y - THROW_GRAVITY, motion.z);
        // Thrown over the arena's edge: nothing below will ever stop it.
        if (getY() < level().getMinBuildHeight() - 16) {
            vanish();
        }
    }

    /**
     * Clips everyone the roll swept over this tick.
     *
     * @return whether the boulder broke on somebody and is gone
     */
    private boolean runOver(EntityNPCInterface boss, AABB sweep) {
        for (LivingEntity victim : level().getEntitiesOfClass(LivingEntity.class,
                sweep.inflate(0.1D), candidate -> isVictim(boss, candidate))) {
            if (!struckIds.add(victim.getId())) {
                continue;
            }
            strike(boss, victim);
            if (stopsOnHit) {
                shatter(boss, victim.position());
                return true;
            }
        }
        return false;
    }

    /** The path hit: damage, potions, and the shove straight down the boulder's own line. */
    private void strike(EntityNPCInterface boss, LivingEntity victim) {
        // Vanilla shoves against the vector it is handed, so the direction goes in negated
        // to throw the victim the way the boulder is moving.
        BossAbilityDamageUtil.hit(victim, BossAbilityKind.BOULDER, boss, hitDamage, effects,
                hitKnockback, -direction.x, -direction.z);
    }

    /**
     * Who this boulder may run over, judged by the boss that launched it.
     *
     * <p>Asked of the controller rather than worked out here, so a boulder and an area slam
     * can never end up with different ideas of who counts as an enemy.</p>
     */
    private boolean isVictim(EntityNPCInterface boss, LivingEntity candidate) {
        if (candidate == boss || !candidate.isAlive()) {
            return false;
        }
        TeleportPathController controller = boss instanceof IBossController holder
                ? holder.cnpcgeckoaddon$getTeleportPathController() : null;
        return controller != null && controller.isBoulderVictim(candidate);
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && target instanceof LivingEntity living
                && getOwner() instanceof EntityNPCInterface boss && isVictim(boss, living);
    }

    /** Breaks the boulder against {@code centre}: the burst circle, the debris, the crack. */
    private void shatter(EntityNPCInterface boss, Vec3 centre) {
        if (!(level() instanceof ServerLevel server)) {
            discard();
            return;
        }
        BlockState state = getBlockState();
        server.playSound(null, centre.x, centre.y, centre.z,
                state.getSoundType().getBreakSound(), SoundSource.HOSTILE, 2.0F, 0.7F);
        spawnDebris(server, centre);
        if (shatterRadius > 0) {
            double radiusSquared = (double) shatterRadius * shatterRadius;
            AABB box = new AABB(centre, centre).inflate(shatterRadius + 1.0D);
            for (LivingEntity victim : server.getEntitiesOfClass(LivingEntity.class, box,
                    candidate -> isVictim(boss, candidate)
                            && candidate.position().distanceToSqr(centre) <= radiusSquared)) {
                BossAbilityDamageUtil.hit(victim, BossAbilityKind.BOULDER, boss, shatterDamage,
                        null, 0, 0.0D, 0.0D);
            }
            BossAreaVfxScheduler.schedule(server, centre, vfx, shatterRadius, 20, false);
        }
        discard();
    }

    /** The quiet exit for a boulder nothing stopped: a pit, a lost boss, a stuck flight. */
    private void vanish() {
        if (level() instanceof ServerLevel server) {
            spawnDebris(server, position());
        }
        discard();
    }

    private void spawnDebris(ServerLevel server, Vec3 centre) {
        double size = diameter();
        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, getBlockState()),
                centre.x, centre.y + size * 0.5D, centre.z,
                (int) (20 + size * 15), size * 0.4D, size * 0.4D, size * 0.4D, 0.1D);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt(BLOCK_KEY, this.entityData.get(BLOCK_STATE));
        tag.putInt(SCALE_KEY, this.entityData.get(SCALE_TENTHS));
        tag.putBoolean(ROLLS_KEY, rolling);
        tag.putDouble(DIR_X_KEY, direction.x);
        tag.putDouble(DIR_Z_KEY, direction.z);
        tag.putDouble(SPEED_KEY, speed);
        tag.putDouble(RANGE_KEY, range);
        tag.putDouble(TRAVELED_KEY, traveled);
        tag.putInt(DAMAGE_KEY, hitDamage);
        tag.putInt(KNOCKBACK_KEY, hitKnockback);
        tag.putBoolean(STOPS_KEY, stopsOnHit);
        tag.putInt(SHATTER_RADIUS_KEY, shatterRadius);
        tag.putInt(SHATTER_DAMAGE_KEY, shatterDamage);
        tag.putString(VFX_KEY, vfx);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(BLOCK_KEY)) {
            this.entityData.set(BLOCK_STATE, tag.getInt(BLOCK_KEY));
        }
        if (tag.contains(SCALE_KEY)) {
            this.entityData.set(SCALE_TENTHS,
                    Mth.clamp(tag.getInt(SCALE_KEY), MIN_SCALE_TENTHS, MAX_SCALE_TENTHS));
            refreshDimensions();
        }
        rolling = !tag.contains(ROLLS_KEY) || tag.getBoolean(ROLLS_KEY);
        Vec3 flat = new Vec3(tag.getDouble(DIR_X_KEY), 0.0D, tag.getDouble(DIR_Z_KEY));
        direction = flat.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
        speed = Mth.clamp(tag.getDouble(SPEED_KEY), 0.1D, 2.0D);
        range = Math.max(tag.getDouble(RANGE_KEY), 1.0D);
        traveled = Math.max(tag.getDouble(TRAVELED_KEY), 0.0D);
        hitDamage = Math.max(tag.getInt(DAMAGE_KEY), 0);
        hitKnockback = Mth.clamp(tag.getInt(KNOCKBACK_KEY), 0, 10);
        stopsOnHit = tag.getBoolean(STOPS_KEY);
        shatterRadius = Mth.clamp(tag.getInt(SHATTER_RADIUS_KEY), 0, 16);
        shatterDamage = Math.max(tag.getInt(SHATTER_DAMAGE_KEY), 0);
        vfx = AreaVfxStyles.normalize(tag.getString(VFX_KEY));
        maxAgeTicks = travelBudgetTicks();
    }
}

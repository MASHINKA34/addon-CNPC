package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.AreaVfxStyles;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.utils.TickQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SculkChargeParticleOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Draws the wave an area attack throws out around the boss, or runs down the line a strike
 * was aimed along.
 *
 * <p>The attack itself lands in a single tick, so the wave cannot be driven from it: it is
 * snapshotted here and expanded from the level tick instead, the same way
 * {@link BossExplosionScheduler} handles a delayed blast. Every boss owns its own entry, so
 * two of them fighting side by side keep their own style, radius and timing.</p>
 *
 * <p>Everything below is cosmetic. Nothing here damages an entity, and the block wave lifts
 * copies of the floor rather than the floor itself - the arena comes out of a fight exactly
 * as it went in.</p>
 *
 * <p>Nothing is persisted. A wave lives for a second or two, and a server that shuts down
 * inside that window should not resume a light show on the next start.</p>
 *
 * <p>A wave spawns entities as it goes, so it runs outside the walk over its own queue for
 * the reason {@link TickQueue} spells out.</p>
 */
public final class BossAreaVfxScheduler {

    /** Roughly one emit per this many blocks around the ring. */
    private static final double EMIT_SPACING = 0.6D;
    private static final int MIN_RING_POINTS = 4;
    /** Ceiling on the emits per tick, so a wide ring costs no more than a narrow one. */
    private static final int MAX_RING_POINTS = 64;
    /** The flanks of a corridor are walked at this, which is what makes them read as thinner. */
    private static final double SIDE_EMIT_SPACING = EMIT_SPACING * 2.0D;
    /**
     * Ceilings on the corridor's front, chosen so its widest possible row - the corridor
     * itself plus a flank on either side - still costs less than {@link #MAX_RING_POINTS}.
     */
    private static final int MAX_FRONT_POINTS = 24;
    private static final int MAX_SIDE_POINTS = 8;
    /**
     * Roughly how fast a corridor's front travels, in blocks per tick.
     *
     * <p>The strike has no length setting for its wave, so the wave takes its time from the
     * ground it has to cover: a long corridor is watched running down the arena instead of
     * being over in the same twenty ticks a short one gets.</p>
     */
    private static final double CORRIDOR_FRONT_SPEED = 0.5D;
    private static final int MIN_CORRIDOR_DURATION_TICKS = 10;
    private static final int MAX_CORRIDOR_DURATION_TICKS = 60;
    /** Beyond this the wave is invisible anyway, so it plays out without costing anything. */
    private static final double AUDIENCE_RANGE = 64.0D;
    /** How far below the boss the ring will look for a floor to run along. */
    private static final int FLOOR_SEARCH_DEPTH = 4;

    private static final int MAX_BLOCKS_PER_WAVE = 48;
    private static final int MAX_BLOCKS_PER_TICK = 12;
    /** A lifted block that never lands - launched over a pit - is dropped after this. */
    private static final int BLOCK_LIFETIME_TICKS = 40;

    /**
     * Ceilings on the queues, high enough that ordinary play never reaches them: a handful of
     * bosses is a handful of waves, and one wave alone can have {@link #MAX_BLOCKS_PER_WAVE}
     * blocks in the air. They are here to stop a runaway, not to shape the show.
     */
    private static final int MAX_WAVES_PER_TICK = 64;
    private static final int MAX_BLOCKS_TRACKED_PER_TICK = 256;

    /** A ring spreads out around its centre; a corridor runs away from it down one line. */
    private enum Shape { RING, CORRIDOR }

    /** One boss's wave, mid-expansion. */
    private static final class Wave {
        private final ResourceKey<Level> dimension;
        private final Vec3 center;
        private final Shape shape;
        /** Ring only: how far out it ends up spreading. */
        private final double radius;
        /** Corridor only: the flat unit direction its front travels in. */
        private final Vec3 axis;
        /** Corridor only: how far down the line the front runs, and how wide it is. */
        private final double length;
        private final double width;
        private final double sideWidth;
        private final String style;
        private final int duration;
        private final boolean blockWave;
        /**
         * Every floor block this wave has already thrown up. A slow wave crosses the same
         * cell for several ticks running, and without this its whole allowance would be
         * spent on the ring it started from.
         */
        private final Set<BlockPos> lifted = new HashSet<>();
        private int tick;

        private Wave(ResourceKey<Level> dimension, Vec3 center, Shape shape, double radius,
                     Vec3 axis, double length, double width, double sideWidth, String style,
                     int duration, boolean blockWave) {
            this.dimension = dimension;
            this.center = center;
            this.shape = shape;
            this.radius = radius;
            this.axis = axis;
            this.length = length;
            this.width = width;
            this.sideWidth = sideWidth;
            this.style = style;
            this.duration = duration;
            this.blockWave = blockWave;
        }

        private static Wave ring(ResourceKey<Level> dimension, Vec3 center, double radius,
                                 String style, int duration, boolean blockWave) {
            return new Wave(dimension, center, Shape.RING, radius, null, 0.0D, 0.0D, 0.0D,
                    style, duration, blockWave);
        }

        private static Wave corridor(ResourceKey<Level> dimension, Vec3 origin, Vec3 axis,
                                     double length, double width, double sideWidth,
                                     String style, int duration, boolean blockWave) {
            return new Wave(dimension, origin, Shape.CORRIDOR, 0.0D, axis, length, width,
                    sideWidth, style, duration, blockWave);
        }
    }

    private record Launched(ResourceKey<Level> dimension, FallingBlockEntity entity, long expireAt) {
    }

    private static final TickQueue<Wave> WAVES = new TickQueue<>("boss area waves", MAX_WAVES_PER_TICK);
    private static final TickQueue<Launched> LAUNCHED =
            new TickQueue<>("wave-lifted blocks", MAX_BLOCKS_TRACKED_PER_TICK);

    private BossAreaVfxScheduler() {
    }

    /** Starts a wave for an area attack that has just landed. */
    public static void schedule(ServerLevel level, Vec3 center, BossPhaseData phase) {
        schedule(level, center, phase.getAreaAttackVfx(), phase.getAreaAttackRadius(),
                phase.getAreaAttackVfxDurationTicks(), phase.isAreaAttackBlockWave());
    }

    /**
     * Starts a wave from a spot given outright, for an ability that lands somewhere other
     * than where the boss is standing - a leap slam goes off wherever the boss came down.
     */
    public static void schedule(ServerLevel level, Vec3 center, String style, double radius,
                                int duration, boolean blockWave) {
        style = AreaVfxStyles.normalize(style);
        if (!AreaVfxStyles.isVisible(style) && !blockWave) {
            return;
        }
        WAVES.add(Wave.ring(level.dimension(), center, radius, style, duration, blockWave));
        // One shout at the front of the wave. Repeating it every tick would drown the fight.
        playStyleSound(level, center, style);
    }

    /**
     * Starts a wave that runs down a corridor instead of spreading out in a ring, for a
     * strike that was aimed along one line rather than thrown out all around.
     *
     * @param axis the flat unit direction the strike was committed to
     */
    public static void scheduleLine(ServerLevel level, Vec3 origin, Vec3 axis, BossPhaseData phase) {
        String style = AreaVfxStyles.normalize(phase.getLineAttackVfx());
        if (!AreaVfxStyles.isVisible(style) && !phase.isLineAttackBlockWave()) {
            return;
        }
        int length = phase.getLineAttackLength();
        WAVES.add(Wave.corridor(level.dimension(), origin, axis, length,
                phase.getLineAttackWidth(), phase.getLineAttackSideWidth(), style,
                corridorDuration(length), phase.isLineAttackBlockWave()));
        playStyleSound(level, origin, style);
    }

    /** How long a corridor of this length takes to run itself out. */
    private static int corridorDuration(int length) {
        return Mth.clamp((int) Math.round(length / CORRIDOR_FRONT_SPEED),
                MIN_CORRIDOR_DURATION_TICKS, MAX_CORRIDOR_DURATION_TICKS);
    }

    public static boolean hasPending() {
        return !WAVES.isEmpty() || !LAUNCHED.isEmpty();
    }

    public static void tick(ServerLevel level) {
        tickLaunched(level);
        WAVES.sweep(wave -> wave.dimension.equals(level.dimension()), wave -> {
            if (++wave.tick > wave.duration) {
                return false;
            }
            // With nobody around the wave still runs its clock down, so a player walking in
            // halfway through catches the rest of it rather than a ring frozen in time.
            if (level.getNearestPlayer(wave.center.x, wave.center.y, wave.center.z,
                    AUDIENCE_RANGE, false) != null) {
                if (wave.shape == Shape.CORRIDOR) {
                    emitCorridor(level, wave);
                } else {
                    emitRing(level, wave);
                }
            }
            return true;
        });
    }

    /** Drops anything still waiting in a level that is going away. */
    public static void clear(ServerLevel level) {
        WAVES.removeIf(wave -> wave.dimension.equals(level.dimension()));
        LAUNCHED.removeIf(launched -> launched.dimension().equals(level.dimension()));
    }

    private static void emitRing(ServerLevel level, Wave wave) {
        double progress = (double) wave.tick / wave.duration;
        double radius = wave.radius * progress;
        int points = Mth.clamp((int) Math.round(Mth.TWO_PI * radius / EMIT_SPACING),
                MIN_RING_POINTS, MAX_RING_POINTS);
        boolean hurricane = AreaVfxStyles.HURRICANE.equals(wave.style);
        // The hurricane's spiral is the same ring walk: it turns a little further and climbs
        // a little higher every tick, which is all a rising vortex ever was.
        double spin = hurricane ? wave.tick * 0.4D : 0.0D;
        double lift = hurricane ? progress * 2.0D : 0.0D;
        // Spread the lifted blocks evenly around the ring instead of clumping the tick's
        // whole allowance onto one side of it.
        int blockStride = Math.max(1, points / MAX_BLOCKS_PER_TICK);

        RandomSource random = level.getRandom();
        int blocksThisTick = 0;
        for (int i = 0; i < points; i++) {
            double angle = spin + i * Mth.TWO_PI / points;
            double x = wave.center.x + Math.cos(angle) * radius;
            double z = wave.center.z + Math.sin(angle) * radius;
            BlockPos floor = findFloor(level, x, wave.center.y, z);
            if (floor == null) {
                continue;
            }
            double y = floor.getY() + 1.0D + lift;
            emit(level, wave.style, x, y, z, i, random);

            if (wave.blockWave && i % blockStride == 0
                    && blocksThisTick < MAX_BLOCKS_PER_TICK
                    && wave.lifted.size() < MAX_BLOCKS_PER_WAVE
                    && !wave.lifted.contains(floor)
                    && launchBlock(level, floor, wave.center, random)) {
                wave.lifted.add(floor);
                blocksThisTick++;
            }
        }
    }

    /**
     * Walks the front of a corridor wave one tick further down its line.
     *
     * <p>Nothing here spreads: the row of emits is the same width all the way along, and it
     * is the row itself that travels, which is what tells a player the strike is coming down
     * the line at them rather than out at them from the boss.</p>
     */
    private static void emitCorridor(ServerLevel level, Wave wave) {
        double progress = (double) wave.tick / wave.duration;
        double front = wave.length * progress;
        // A quarter turn of the axis, which is what the corridor is measured across.
        double acrossX = wave.axis.z;
        double acrossZ = -wave.axis.x;
        double half = wave.width * 0.5D;
        int mainPoints = Mth.clamp((int) Math.round(wave.width / EMIT_SPACING) + 1,
                2, MAX_FRONT_POINTS);
        int sidePoints = wave.sideWidth <= 0.0D ? 0
                : Mth.clamp((int) Math.round(wave.sideWidth / SIDE_EMIT_SPACING), 1, MAX_SIDE_POINTS);
        int points = mainPoints + 2 * sidePoints;
        // Spread the lifted blocks across the front instead of clumping the tick's whole
        // allowance onto one edge of it, exactly as the ring does around itself.
        int blockStride = Math.max(1, points / MAX_BLOCKS_PER_TICK);

        RandomSource random = level.getRandom();
        int blocksThisTick = 0;
        for (int i = 0; i < points; i++) {
            double offset = frontOffset(i, mainPoints, sidePoints, half, wave.sideWidth);
            double x = wave.center.x + wave.axis.x * front + acrossX * offset;
            double z = wave.center.z + wave.axis.z * front + acrossZ * offset;
            BlockPos floor = findFloor(level, x, wave.center.y, z);
            if (floor == null) {
                continue;
            }
            emit(level, wave.style, x, floor.getY() + 1.0D, z, i, random);

            if (wave.blockWave && i % blockStride == 0
                    && blocksThisTick < MAX_BLOCKS_PER_TICK
                    && wave.lifted.size() < MAX_BLOCKS_PER_WAVE
                    && !wave.lifted.contains(floor)
                    && launchBlock(level, floor, wave.center, random)) {
                wave.lifted.add(floor);
                blocksThisTick++;
            }
        }
    }

    /**
     * Where one point of a corridor's front sits across the line.
     *
     * <p>The first {@code mainPoints} walk the corridor itself from edge to edge. The rest
     * step out along one flank and then the other, at the coarser spacing that makes the
     * side wave read as the weaker half of the strike that it is.</p>
     */
    private static double frontOffset(int index, int mainPoints, int sidePoints, double half,
                                      double sideWidth) {
        if (index < mainPoints) {
            return -half + index * 2.0D * half / (mainPoints - 1);
        }
        int step = index - mainPoints;
        double distance = half + (step / 2 + 1) * sideWidth / sidePoints;
        return (step & 1) == 0 ? distance : -distance;
    }

    /**
     * The block the wave runs along at one point of the ring, or null when the floor is more
     * than {@link #FLOOR_SEARCH_DEPTH} below the boss - a wave hanging in mid air over a
     * balcony edge looks worse than one that simply skips the gap. Shared with the
     * ability warnings, which lie on the arena floor under the same rule.
     */
    static BlockPos findFloor(ServerLevel level, double x, double y, double z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        for (int depth = 0; depth <= FLOOR_SEARCH_DEPTH; depth++) {
            if (!level.isLoaded(pos)) {
                return null;
            }
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && !state.getCollisionShape(level, pos).isEmpty()) {
                return pos.immutable();
            }
            pos.move(Direction.DOWN);
        }
        return null;
    }

    /**
     * Paints one point of the ring.
     *
     * <p>Only the leading particle goes out on every point: the rest thin out to every other
     * point and to the odd flourish, which keeps a sixteen-block ring inside a packet budget
     * a boss fight can afford.</p>
     */
    private static void emit(ServerLevel level, String style, double x, double y, double z,
                             int index, RandomSource random) {
        boolean second = (index & 1) == 0;
        boolean accent = random.nextInt(4) == 0;
        switch (style) {
            case AreaVfxStyles.VINES -> {
                level.sendParticles(ParticleTypes.COMPOSTER, x, y + 0.1D, z, 2, 0.05D, 0.3D, 0.05D, 0.02D);
                if (second) {
                    level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y + 0.4D, z, 1, 0.1D, 0.3D, 0.1D, 0.0D);
                }
                if (accent) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_LEAVES.defaultBlockState()),
                            x, y + 0.6D, z, 2, 0.15D, 0.2D, 0.15D, 0.05D);
                    level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, x, y + 0.9D, z, 1, 0.2D, 0.3D, 0.2D, 0.0D);
                }
            }
            case AreaVfxStyles.STONE -> {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                        x, y + 0.2D, z, 3, 0.15D, 0.1D, 0.15D, 0.15D);
                if (second) {
                    level.sendParticles(ParticleTypes.CRIT, x, y + 0.3D, z, 2, 0.1D, 0.2D, 0.1D, 0.1D);
                }
                if (accent) {
                    level.sendParticles(ParticleTypes.POOF, x, y + 0.4D, z, 1, 0.2D, 0.1D, 0.2D, 0.02D);
                }
            }
            case AreaVfxStyles.HURRICANE -> {
                level.sendParticles(ParticleTypes.CLOUD, x, y + 0.3D, z, 1, 0.1D, 0.2D, 0.1D, 0.02D);
                if (second) {
                    level.sendParticles(ParticleTypes.POOF, x, y + 0.6D, z, 1, 0.15D, 0.25D, 0.15D, 0.03D);
                }
                if (accent) {
                    level.sendParticles(ParticleTypes.SWEEP_ATTACK, x, y + 1.0D, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                }
            }
            case AreaVfxStyles.FIRE -> {
                level.sendParticles(ParticleTypes.FLAME, x, y + 0.2D, z, 2, 0.1D, 0.1D, 0.1D, 0.01D);
                if (second) {
                    level.sendParticles(ParticleTypes.SMALL_FLAME, x, y + 0.4D, z, 2, 0.15D, 0.2D, 0.15D, 0.01D);
                }
                if (accent) {
                    level.sendParticles(ParticleTypes.LAVA, x, y + 0.3D, z, 1, 0.1D, 0.0D, 0.1D, 0.0D);
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.8D, z, 1, 0.2D, 0.2D, 0.2D, 0.01D);
                }
            }
            case AreaVfxStyles.GHOST -> {
                level.sendParticles(ParticleTypes.SOUL, x, y + 0.2D, z, 1, 0.1D, 0.1D, 0.1D, 0.03D);
                if (second) {
                    level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y + 0.5D, z, 1, 0.1D, 0.2D, 0.1D, 0.01D);
                }
                if (accent) {
                    level.sendParticles(ParticleTypes.WHITE_ASH, x, y + 0.9D, z, 3, 0.3D, 0.3D, 0.3D, 0.0D);
                }
            }
            case AreaVfxStyles.SCULK_WAVE -> {
                level.sendParticles(ParticleTypes.SCULK_SOUL, x, y + 0.2D, z, 1, 0.1D, 0.1D, 0.1D, 0.02D);
                if (second) {
                    level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, x, y + 0.4D, z, 2, 0.15D, 0.15D, 0.15D, 0.0D);
                }
                if (accent) {
                    level.sendParticles(new SculkChargeParticleOptions(random.nextFloat() * Mth.TWO_PI),
                            x, y + 0.6D, z, 1, 0.1D, 0.2D, 0.1D, 0.0D);
                }
            }
            default -> {
                // AreaVfxStyles.NONE, and anything the block wave alone was scheduled for.
            }
        }
    }

    private static void playStyleSound(ServerLevel level, Vec3 center, String style) {
        switch (style) {
            case AreaVfxStyles.VINES -> playSound(level, center, SoundEvents.AZALEA_LEAVES_BREAK, 2.5F, 0.7F);
            case AreaVfxStyles.STONE -> playSound(level, center, SoundEvents.STONE_BREAK, 4.0F, 0.5F);
            case AreaVfxStyles.HURRICANE -> playSound(level, center, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 3.0F, 0.8F);
            case AreaVfxStyles.FIRE -> playSound(level, center, SoundEvents.FIRECHARGE_USE, 3.0F, 0.7F);
            case AreaVfxStyles.GHOST -> playSound(level, center, SoundEvents.SOUL_ESCAPE.value(), 3.0F, 0.6F);
            case AreaVfxStyles.SCULK_WAVE -> playSound(level, center, SoundEvents.SCULK_SHRIEKER_SHRIEK, 2.5F, 0.9F);
            default -> {
                // Styleless waves stay quiet.
            }
        }
    }

    private static void playSound(ServerLevel level, Vec3 center, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, center.x, center.y, center.z, sound, SoundSource.HOSTILE, volume, pitch);
    }

    /**
     * Throws a copy of one floor block into the air.
     *
     * <p>The world is never written to: the entity is built straight from the state the floor
     * already has, so the real block never leaves and no neighbour ever hears about it. The
     * copy drops nothing, hurts nobody and refuses to settle when it lands.</p>
     */
    private static boolean launchBlock(ServerLevel level, BlockPos pos, Vec3 center, RandomSource random) {
        BlockState state = level.getBlockState(pos);
        if (!canLaunch(level, pos, state)) {
            return false;
        }
        FallingBlockEntity block = new FallingBlockEntity(level,
                pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, state);
        block.dropItem = false;
        // Also what stops it from becoming a real block again wherever it comes down.
        block.disableDrop();

        double dx = pos.getX() + 0.5D - center.x;
        double dz = pos.getZ() + 0.5D - center.z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        double outward = distance > 1.0E-4D ? (0.05D + random.nextDouble() * 0.1D) / distance : 0.0D;
        block.setDeltaMovement(dx * outward, 0.35D + random.nextDouble() * 0.25D, dz * outward);

        level.addFreshEntity(block);
        LAUNCHED.add(new Launched(level.dimension(), block, level.getGameTime() + BLOCK_LIFETIME_TICKS));
        return true;
    }

    /**
     * Whether a floor block may be copied into the air.
     *
     * <p>Block entities are out - a chest, a spawner or the boss's own chest carry contents
     * the copy would advertise. So is anything unbreakable, which is how bedrock and barriers
     * stay where a map maker put them, and anything that is not a full solid cube, which
     * covers liquids, plants and carpets.</p>
     *
     * <p>The block also needs somewhere to go. Where the ring runs into a wall the floor it
     * finds is the wall's own bottom block, and a copy of that would only rattle against the
     * course above it for the rest of its life.</p>
     */
    private static boolean canLaunch(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (state.getDestroySpeed(level, pos) < 0.0F) {
            return false;
        }
        if (!state.isSolidRender(level, pos)) {
            return false;
        }
        BlockPos above = pos.above();
        return level.isLoaded(above) && level.getBlockState(above).getCollisionShape(level, above).isEmpty();
    }

    private static void tickLaunched(ServerLevel level) {
        long gameTime = level.getGameTime();
        LAUNCHED.sweep(launched -> launched.dimension().equals(level.dimension()), launched -> {
            if (launched.entity().isRemoved()) {
                return false;
            }
            if (gameTime < launched.expireAt()) {
                return true;
            }
            launched.entity().discard();
            return false;
        });
    }
}

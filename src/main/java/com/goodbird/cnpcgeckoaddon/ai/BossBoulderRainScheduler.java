package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossEffectSet;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.entity.EntityBossBoulder;
import com.goodbird.cnpcgeckoaddon.registry.EntityRegistry;
import com.goodbird.cnpcgeckoaddon.utils.TickQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;

/**
 * Holds a volley of stones falling out of the sky around the boss.
 *
 * <p>The mechanic is the ring and the gap. One cast picks its points in a ring around wherever
 * the boss was standing, and each of them lights a mark on the arena floor the moment its own
 * stone leaves the ceiling - so the warning lasts exactly as long as the drop does, and the
 * fall height is the knob that sets it. The mark is drawn unconditionally rather than through
 * the warning settings, for the reason the geyser's fuse is: an invisible fuse is not a
 * mechanic, it is a trap.</p>
 *
 * <p>The wait cannot be run off the ability that started it: the boss is back on its rotation
 * long before the last stone lands. Everything a drop needs is therefore snapshotted here on
 * the tick of the cast - the enrage bonus included - and driven from the level tick, the way
 * {@link BossGeyserScheduler} drives its fuses.</p>
 *
 * <p>A drop spawns an entity and paints particles, so it never runs while the queue is being
 * walked: {@link TickQueue} takes the entries a tick is going to work on out first and runs
 * them afterwards.</p>
 *
 * <p>Nothing here is persisted. A volley lasts a couple of seconds, and a server that shuts
 * down inside that window should not start dropping rocks on whoever logs in first.</p>
 */
public final class BossBoulderRainScheduler {

    /**
     * How many stones already in the air one level tick paints and lands. Two full volleys
     * at once, which is well past anything an arena asks for - a stone still waiting for its
     * turn in the interval costs nothing here, because it is not ready yet. Anything over
     * the cap keeps its place and is picked up next tick.
     */
    private static final int MAX_PER_TICK = 64;
    /** Beyond this nobody can see the mark, so it burns down without costing anything. */
    private static final double AUDIENCE_RANGE = 64.0D;
    /** How often the mark is repainted. Every other tick reads as a steady shape. */
    private static final int MARK_INTERVAL_TICKS = 2;
    /** Smallest mark a stone with no shards still gets, so every drop is announced. */
    private static final double MIN_MARK_RADIUS = 1.0D;
    /** Tries at finding floor for one stone before the volley gives that stone up. */
    private static final int PLACEMENT_ATTEMPTS = 4;
    /** A roof this close leaves nothing worth calling a drop, so the point is skipped. */
    private static final double MIN_DROP_BLOCKS = 1.0D;

    /** One stone: the point it is coming down on, and everything it will hit with. */
    private static final class Pending {
        private final ResourceKey<Level> dimension;
        private final EntityNPCInterface boss;
        /** Where the stone lands, on the floor, from the tick of the cast. */
        private final Vec3 pos;
        /** Bottom of the stone at spawn; below the asked-for height under a low roof. */
        private final double spawnY;
        private final BlockState block;
        private final String style;
        private final int scale;
        /** Direct hit and shard damage, enrage already counted into both. */
        private final int damage;
        private final int knockback;
        private final int shatterRadius;
        private final int shatterDamage;
        private final String vfx;
        private final BossEffectSet effects;
        private final long dropsAt;
        private final long landsAt;
        private boolean dropped;

        private Pending(ResourceKey<Level> dimension, EntityNPCInterface boss, Vec3 pos,
                        double spawnY, BlockState block, String style, int scale, int damage,
                        int knockback, int shatterRadius, int shatterDamage, String vfx,
                        BossEffectSet effects, long dropsAt, long landsAt) {
            this.dimension = dimension;
            this.boss = boss;
            this.pos = pos;
            this.spawnY = spawnY;
            this.block = block;
            this.style = style;
            this.scale = scale;
            this.damage = damage;
            this.knockback = knockback;
            this.shatterRadius = shatterRadius;
            this.shatterDamage = shatterDamage;
            this.vfx = vfx;
            this.effects = effects;
            this.dropsAt = dropsAt;
            this.landsAt = landsAt;
        }

        /** How wide a circle this stone burns on the floor: what its landing really covers. */
        private double markRadius() {
            return Math.max(Math.max(shatterRadius, scale / 20.0D), MIN_MARK_RADIUS);
        }
    }

    private static final TickQueue<Pending> PENDING = new TickQueue<>("boss boulder rains", MAX_PER_TICK);

    private BossBoulderRainScheduler() {
    }

    /**
     * Lays out one whole volley around {@code origin}.
     *
     * @param damage        what a direct hit lands for, enrage bonus already in it
     * @param shatterDamage the same, for the shards the landing throws
     * @return how many stones really found floor to come down on
     */
    public static int schedule(ServerLevel level, EntityNPCInterface boss, BossPhaseData phase,
                               Vec3 origin, BlockState block, int damage, int knockback,
                               int shatterDamage, long gameTime) {
        RandomSource random = level.getRandom();
        double diameter = phase.getBoulderRainScale() / 10.0D;
        int interval = phase.getBoulderRainIntervalTicks();
        int scheduled = 0;
        for (int i = 0; i < phase.getBoulderRainCount(); i++) {
            Vec3 point = findPoint(level, origin, phase, random);
            if (point == null) {
                continue;
            }
            double spawnY = spawnHeight(level, point, phase.getBoulderRainFallHeight(), diameter);
            if (Double.isNaN(spawnY)) {
                continue;
            }
            // Its own drop, not the volley's: a stone stopped short by a low roof falls for
            // less time, and its mark has to say so.
            long dropsAt = gameTime + (long) i * interval;
            long landsAt = dropsAt + EntityBossBoulder.fallTicks(spawnY - point.y);
            PENDING.add(new Pending(level.dimension(), boss, point, spawnY, block,
                    phase.getBoulderRainStyle(), phase.getBoulderRainScale(), damage, knockback,
                    phase.getBoulderRainShatterRadius(), shatterDamage, phase.getBoulderRainVfx(),
                    phase.getBoulderRainEffects(), dropsAt, landsAt));
            scheduled++;
        }
        return scheduled;
    }

    public static boolean hasPending() {
        return !PENDING.isEmpty();
    }

    public static void tick(ServerLevel level) {
        long gameTime = level.getGameTime();
        // A stone whose turn in the interval has not come round yet is deliberately not
        // ready: it has nothing to paint and nothing to drop, and pulling it every tick
        // would spend the tick budget on stones that are only waiting.
        PENDING.sweep(pending -> pending.dimension.equals(level.dimension())
                        && gameTime >= pending.dropsAt,
                pending -> tickDrop(level, pending, gameTime));
    }

    /** Drops anything still waiting in a level that is going away. */
    public static void clear(ServerLevel level) {
        PENDING.removeIf(pending -> pending.dimension.equals(level.dimension()));
    }

    /**
     * Drops the volley one boss has in the air, for its death and for the end of its fight.
     *
     * <p>A rain is the boss doing something, not a minefield left behind: killing it while
     * the last stones are still coming is a win, and the arena owes the party nothing more.
     * Stones already spawned are entities and finish their own drop.</p>
     */
    public static void clearBoss(EntityNPCInterface boss) {
        if (PENDING.isEmpty()) {
            return;
        }
        PENDING.removeIf(pending -> pending.boss == boss);
    }

    /** @return whether this stone is still owed something and belongs back in the queue */
    private static boolean tickDrop(ServerLevel level, Pending pending, long gameTime) {
        if (!pending.boss.isAlive() || pending.boss.isRemoved()) {
            return false;
        }
        if (!pending.dropped) {
            pending.dropped = true;
            drop(level, pending);
        }
        // The stone is an entity now and breaks wherever it really meets something; the mark
        // is held for the drop it was measured for and then let go.
        if (gameTime >= pending.landsAt) {
            return false;
        }
        markFloor(level, pending, gameTime);
        return true;
    }

    /** Puts the stone in the air over its mark and lets go of it. */
    private static void drop(ServerLevel level, Pending pending) {
        EntityBossBoulder boulder = new EntityBossBoulder(EntityRegistry.entityBossBoulder, level);
        boulder.setOwner(pending.boss);
        boulder.configure(pending.block, pending.style, pending.scale, pending.damage,
                pending.knockback, true, pending.shatterRadius, pending.shatterDamage,
                pending.vfx, pending.effects);
        boulder.setPos(pending.pos.x, pending.spawnY, pending.pos.z);
        boulder.launchFall(pending.spawnY - pending.pos.y);
        if (!level.addFreshEntity(boulder)) {
            return;
        }
        // Played down on the mark rather than up at the stone: the one who needs to hear it
        // is standing underneath.
        level.playSound(null, pending.pos.x, pending.pos.y, pending.pos.z,
                pending.block.getSoundType().getPlaceSound(), SoundSource.HOSTILE, 1.2F, 0.5F);
    }

    /** Paints the circle the stone is about to come down in, and the grit shaken off it. */
    private static void markFloor(ServerLevel level, Pending pending, long gameTime) {
        if (gameTime % MARK_INTERVAL_TICKS != 0L || level.getNearestPlayer(pending.pos.x,
                pending.pos.y, pending.pos.z, AUDIENCE_RANGE, false) == null) {
            return;
        }
        BossTelegraphUtil.ring(level, pending.pos, pending.markRadius(),
                BossTelegraphUtil.dust(BossAbilityKind.BOULDER_RAIN));
        // Debris of the stone's own block, so the mark says what is arriving as well as where.
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, pending.block),
                pending.pos.x, pending.pos.y + 0.2D, pending.pos.z, 2, 0.2D, 0.05D, 0.2D, 0.02D);
    }

    /**
     * One point of the ring, on the floor, or null when this one is over a hole.
     *
     * <p>Drawn evenly over the ring's area rather than over its width, so a wide ring does
     * not pile its stones up against the inner edge.</p>
     */
    private static Vec3 findPoint(ServerLevel level, Vec3 origin, BossPhaseData phase,
                                  RandomSource random) {
        double min = phase.getBoulderRainMinRadius();
        double max = phase.getBoulderRainRadius();
        for (int attempt = 0; attempt < PLACEMENT_ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = Math.sqrt(min * min + random.nextDouble() * (max * max - min * min));
            double x = origin.x + Math.cos(angle) * distance;
            double z = origin.z + Math.sin(angle) * distance;
            BlockPos floor = BossAreaVfxScheduler.findFloor(level, x, origin.y, z);
            if (floor != null) {
                return new Vec3(x, floor.getY() + 1.0D, z);
            }
        }
        return null;
    }

    /**
     * Where the bottom of a stone this wide can start above {@code floor}.
     *
     * <p>Walked block by block the way the floor search is walked, so a low cave simply gets
     * a shorter drop instead of a stone spawned inside the roof. A ceiling with no room to
     * fall under it at all takes the point out of the volley: a mark nothing can reach would
     * be a lie about where the danger is.</p>
     *
     * @return the spawn height, or NaN when this point has no headroom worth dropping into
     */
    private static double spawnHeight(ServerLevel level, Vec3 floor, int wanted, double diameter) {
        int limit = wanted + Mth.ceil(diameter);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(
                Mth.floor(floor.x), Mth.floor(floor.y), Mth.floor(floor.z));
        int headroom = 0;
        while (headroom < limit) {
            if (!level.isLoaded(pos)) {
                break;
            }
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && !state.getCollisionShape(level, pos).isEmpty()) {
                break;
            }
            headroom++;
            pos.move(Direction.UP);
        }
        if (headroom < MIN_DROP_BLOCKS + diameter) {
            // Not a height at all: a world floor sits below zero often enough that no real
            // coordinate can be borrowed as the "nowhere to fall" answer.
            return Double.NaN;
        }
        return Math.min(floor.y + wanted, floor.y + headroom - diameter);
    }
}

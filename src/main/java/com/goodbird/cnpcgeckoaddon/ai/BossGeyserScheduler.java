package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossEffectSet;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.mixin.IBossController;
import com.goodbird.cnpcgeckoaddon.utils.TickQueue;
import com.goodbird.cnpcgeckoaddon.world.TemporaryFluidStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.List;

/**
 * Holds a geyser's fuse: a mark burns on the arena floor for a while, and only then does the
 * column come up through it.
 *
 * <p>The whole mechanic is that gap. A player who looks down sees the circle under their feet
 * and has the fuse to walk out of it, which is why the mark is drawn unconditionally rather
 * than through the warning settings - an invisible fuse is not a mechanic, it is a trap.</p>
 *
 * <p>The wait cannot be run off the ability that lit it: the boss goes back to its rotation
 * the moment the cast lands, and the eruption is still seconds away. Everything the eruption
 * needs is therefore snapshotted here - the enrage bonus included - and driven from the level
 * tick, the same way {@link BossExplosionScheduler} handles a delayed blast.</p>
 *
 * <p>An eruption damages, launches and places blocks, so it never runs while the queue is
 * being walked: {@link TickQueue} takes the entries a tick is going to work on out first and
 * runs them afterwards. That is not theoretical - the same shape of mistake in the explosion
 * scheduler once took a live server down with a {@code ConcurrentModificationException}.</p>
 *
 * <p>Nothing here is persisted. A fuse lives for a second or two, and a server that shuts down
 * inside that window should not erupt under whoever logs in first on the next start.</p>
 */
public final class BossGeyserScheduler {

    /**
     * How many fuses one level tick works on, eruptions included. Far above anything a fight
     * asks for - eight victims is the most a single cast can mark - so it is here to stop a
     * runaway, not to shape the mechanic. The rest keeps its place and is picked up next tick.
     */
    private static final int MAX_PER_TICK = 32;
    /** Beyond this nobody can see the mark, so the fuse burns down without costing anything. */
    private static final double AUDIENCE_RANGE = 64.0D;
    /** How often the mark is repainted. Every other tick reads as a steady shape. */
    private static final int MARK_INTERVAL_TICKS = 2;
    /** How long the eruption's wave runs for; the geyser has no length setting of its own. */
    private static final int VFX_DURATION_TICKS = 20;
    /**
     * Ceiling on the puddle, whatever the eruption's own radius is. The circle is what the
     * geyser hits; the puddle is what it leaves lying about afterwards, and a sixteen block
     * disc of lava is arena vandalism even when it does clean up after itself.
     */
    private static final int MAX_FLUID_RADIUS = 4;
    /** Spacing between the column's emits, and the height it climbs to per block of radius. */
    private static final double COLUMN_SPACING = 0.5D;
    private static final double COLUMN_HEIGHT_PER_RADIUS = 1.5D;
    private static final double MIN_COLUMN_HEIGHT = 3.0D;
    private static final double MAX_COLUMN_HEIGHT = 12.0D;
    /** How hard the boil at the centre spits, from the moment it is lit to the last tick. */
    private static final double MIN_BOIL_SPEED = 0.02D;
    private static final double MAX_BOIL_SPEED = 0.12D;

    /** One geyser, mid-fuse. */
    private static final class Pending {
        private final ResourceKey<Level> dimension;
        private final EntityNPCInterface boss;
        /** The victim the mark rides, or -1 when it was nailed down where it was lit. */
        private final int followId;
        private final double radius;
        private final int damage;
        /** Upward throw in tenths of a block per tick, enrage already counted in. */
        private final int launch;
        private final BossEffectSet effects;
        private final String vfx;
        private final boolean blockWave;
        /** null when the eruption leaves nothing behind. */
        private final BlockState fluid;
        private final int fluidLifetimeTicks;
        private final long litAt;
        private final long eruptsAt;
        /** Where the column comes up; moves under a followed victim, otherwise fixed. */
        private Vec3 pos;

        private Pending(ResourceKey<Level> dimension, EntityNPCInterface boss, int followId,
                        double radius, int damage, int launch, BossEffectSet effects, String vfx,
                        boolean blockWave, BlockState fluid, int fluidLifetimeTicks, long litAt,
                        long eruptsAt, Vec3 pos) {
            this.dimension = dimension;
            this.boss = boss;
            this.followId = followId;
            this.radius = radius;
            this.damage = damage;
            this.launch = launch;
            this.effects = effects;
            this.vfx = vfx;
            this.blockWave = blockWave;
            this.fluid = fluid;
            this.fluidLifetimeTicks = fluidLifetimeTicks;
            this.litAt = litAt;
            this.eruptsAt = eruptsAt;
            this.pos = pos;
        }
    }

    private static final TickQueue<Pending> PENDING = new TickQueue<>("boss geysers", MAX_PER_TICK);

    private BossGeyserScheduler() {
    }

    /**
     * Lights one geyser under {@code victim}.
     *
     * @param fluid  the pool the eruption leaves behind, or null for none
     * @param damage what the eruption hits for, with the enrage bonus already in it
     * @param launch how hard it throws, in the same already-scaled terms
     * @return whether a mark was really lit, i.e. whether there was floor to lay it on
     */
    public static boolean schedule(ServerLevel level, EntityNPCInterface boss, LivingEntity victim,
                                   BossPhaseData phase, BlockState fluid, int damage, int launch,
                                   long gameTime) {
        Vec3 point = groundUnder(level, victim);
        if (point == null) {
            return false;
        }
        PENDING.add(new Pending(level.dimension(), boss,
                phase.isGeyserFollowTarget() ? victim.getId() : -1,
                phase.getGeyserRadius(), damage, launch, phase.getGeyserEffects(),
                phase.getGeyserVfx(), phase.isGeyserBlockWave(), fluid,
                phase.getGeyserFluidLifetimeTicks(), gameTime,
                gameTime + phase.getGeyserFuseTicks(), point));
        // One hiss as the ground opens, for the player who is not looking down.
        level.playSound(null, point.x, point.y, point.z, SoundEvents.LAVA_POP,
                SoundSource.HOSTILE, 1.6F, 0.5F);
        return true;
    }

    public static boolean hasPending() {
        return !PENDING.isEmpty();
    }

    public static void tick(ServerLevel level) {
        long gameTime = level.getGameTime();
        PENDING.sweep(pending -> pending.dimension.equals(level.dimension()),
                pending -> tickFuse(level, pending, gameTime));
    }

    /** Drops anything still waiting in a level that is going away. */
    public static void clear(ServerLevel level) {
        PENDING.removeIf(pending -> pending.dimension.equals(level.dimension()));
    }

    /**
     * Drops the fuses one boss lit, for its death and for the end of its fight.
     *
     * <p>A geyser is the boss doing something, not a mine left in the floor: killing it while
     * the ground is still smoking is a win, and the arena owes the party nothing more.</p>
     */
    public static void clearBoss(EntityNPCInterface boss) {
        if (PENDING.isEmpty()) {
            return;
        }
        PENDING.removeIf(pending -> pending.boss == boss);
    }

    /** @return whether this fuse is still burning and belongs back in the queue */
    private static boolean tickFuse(ServerLevel level, Pending pending, long gameTime) {
        if (!pending.boss.isAlive() || pending.boss.isRemoved()) {
            return false;
        }
        follow(level, pending);
        if (gameTime < pending.eruptsAt) {
            markFuse(level, pending, gameTime);
            return true;
        }
        erupt(level, pending);
        return false;
    }

    /** Walks the mark back under whoever it was told to chase. */
    private static void follow(ServerLevel level, Pending pending) {
        if (pending.followId < 0
                || !(level.getEntity(pending.followId) instanceof LivingEntity victim)
                || !victim.isAlive()) {
            return;
        }
        Vec3 moved = groundUnder(level, victim);
        // A victim out over a hole leaves the mark on the last floor it had, rather than
        // dropping it into one.
        if (moved != null) {
            pending.pos = moved;
        }
    }

    /**
     * The floor somebody is standing on, or null when there is none within reach.
     *
     * <p>Shares the wave's floor search, so the mark lies on the arena the same way every
     * other shape the boss paints does, and gives up on the same holes.</p>
     */
    private static Vec3 groundUnder(ServerLevel level, LivingEntity victim) {
        BlockPos floor = BossAreaVfxScheduler.findFloor(level, victim.getX(), victim.getY(), victim.getZ());
        return floor == null ? null : new Vec3(victim.getX(), floor.getY() + 1.0D, victim.getZ());
    }

    /**
     * Paints the fuse: the circle that is about to go off, and the boil in the middle of it.
     *
     * <p>The boil spits harder the closer the eruption gets, so the mark says how long is
     * left as well as where not to be standing.</p>
     */
    private static void markFuse(ServerLevel level, Pending pending, long gameTime) {
        if (gameTime % MARK_INTERVAL_TICKS != 0L || level.getNearestPlayer(pending.pos.x,
                pending.pos.y, pending.pos.z, AUDIENCE_RANGE, false) == null) {
            return;
        }
        BossTelegraphUtil.ring(level, pending.pos, pending.radius,
                BossTelegraphUtil.dust(BossAbilityKind.GEYSER));
        double speed = Mth.lerp(fuseProgress(pending, gameTime), MIN_BOIL_SPEED, MAX_BOIL_SPEED);
        level.sendParticles(ParticleTypes.BUBBLE_POP, pending.pos.x, pending.pos.y + 0.2D,
                pending.pos.z, 3, 0.25D, 0.05D, 0.25D, speed);
        level.sendParticles(ParticleTypes.SMOKE, pending.pos.x, pending.pos.y + 0.3D,
                pending.pos.z, 2, 0.15D, 0.05D, 0.15D, speed);
    }

    /** How far the fuse has burned, from 0 the tick it was lit to 1 the tick it goes. */
    private static double fuseProgress(Pending pending, long gameTime) {
        long fuse = pending.eruptsAt - pending.litAt;
        return fuse <= 0L ? 1.0D : Mth.clamp((double) (gameTime - pending.litAt) / fuse, 0.0D, 1.0D);
    }

    private static void erupt(ServerLevel level, Pending pending) {
        Vec3 pos = pending.pos;
        // Both started before the hits, so what a player sees leaves at the same moment the
        // damage lands rather than a tick behind it.
        BossAreaVfxScheduler.schedule(level, pos, pending.vfx, pending.radius, VFX_DURATION_TICKS,
                pending.blockWave);
        drawColumn(level, pending);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.LAVA_EXTINGUISH,
                SoundSource.HOSTILE, 3.0F, 0.5F);

        for (LivingEntity victim : victims(level, pending)) {
            // The launch is this eruption's knockback rather than something on top of it, so
            // it goes with the hit rather than after it: a totem whose list this geyser is not
            // on has to be left standing, not thrown while taking nothing.
            if (BossAbilityDamageUtil.passesBy(victim, BossAbilityKind.GEYSER)) {
                continue;
            }
            // No knockback asked for: what this throws with is the launch below, and vanilla
            // only ever shoves along the ground.
            BossAbilityDamageUtil.hit(victim, BossAbilityKind.GEYSER, pending.boss, pending.damage,
                    pending.effects, 0, 0.0D, 0.0D);
            launch(victim, pending.launch);
        }
        pool(level, pending);
    }

    /**
     * Everyone this eruption may catch.
     *
     * <p>Asked of the boss that lit the fuse rather than worked out here, so a geyser and an
     * area slam can never end up with different ideas of who counts as an enemy.</p>
     */
    private static List<LivingEntity> victims(ServerLevel level, Pending pending) {
        TeleportPathController controller = pending.boss instanceof IBossController holder
                ? holder.cnpcgeckoaddon$getTeleportPathController() : null;
        return controller == null ? List.of()
                : controller.geyserVictims(level, pending.pos, pending.radius);
    }

    /** Straight up, which is the one push {@code knockback} cannot be asked for. */
    private static void launch(LivingEntity victim, int strength) {
        if (strength <= 0) {
            return;
        }
        Vec3 movement = victim.getDeltaMovement();
        victim.setDeltaMovement(movement.x, strength / 10.0D, movement.z);
        // Wipes the fall they were already in, so the throw is measured from here and the
        // ride up cannot be what kills them.
        victim.fallDistance = 0.0F;
        // Players simulate their own movement, so the server has to push the new velocity to
        // them explicitly. hurtMarked is what makes ServerEntity send it.
        victim.hurtMarked = true;
    }

    /** The column itself: what a player watching sees come up out of the mark. */
    private static void drawColumn(ServerLevel level, Pending pending) {
        Vec3 pos = pending.pos;
        if (level.getNearestPlayer(pos.x, pos.y, pos.z, AUDIENCE_RANGE, false) == null) {
            return;
        }
        double height = Mth.clamp(pending.radius * COLUMN_HEIGHT_PER_RADIUS,
                MIN_COLUMN_HEIGHT, MAX_COLUMN_HEIGHT);
        int steps = (int) Math.round(height / COLUMN_SPACING);
        for (int step = 0; step <= steps; step++) {
            double y = pos.y + step * COLUMN_SPACING;
            level.sendParticles(ParticleTypes.CLOUD, pos.x, y, pos.z, 2, 0.2D, 0.1D, 0.2D, 0.08D);
            // The smoke thins out to every other step, which keeps a tall column inside a
            // packet budget a boss fight can afford.
            if ((step & 1) == 0) {
                level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, y, pos.z, 1,
                        0.25D, 0.1D, 0.25D, 0.04D);
            }
        }
        if (pending.fluid != null) {
            // A geyser of something shows what it is throwing before the puddle says so.
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, pending.fluid),
                    pos.x, pos.y + 0.5D, pos.z, 16, 0.4D, 0.6D, 0.4D, 0.15D);
        }
    }

    /**
     * Leaves the pool behind, on loan.
     *
     * <p>A flat disc for the reason the fluid spit's puddle is one, and through the same
     * store, so the arena comes out of the fight exactly as it went in.</p>
     */
    private static void pool(ServerLevel level, Pending pending) {
        if (pending.fluid == null) {
            return;
        }
        TemporaryFluidStore store = TemporaryFluidStore.get(level);
        BlockPos centre = BlockPos.containing(pending.pos);
        int radius = Math.min((int) pending.radius, MAX_FLUID_RADIUS);
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z > radius * radius) {
                    continue;
                }
                // The impact layer, then the one below it, so a puddle on a step still
                // finds somewhere to lie rather than hanging over the drop.
                for (int y = 0; y >= -1; y--) {
                    if (store.place(level, centre.offset(x, y, z), pending.fluid,
                            pending.fluidLifetimeTicks)) {
                        break;
                    }
                }
            }
        }
    }
}

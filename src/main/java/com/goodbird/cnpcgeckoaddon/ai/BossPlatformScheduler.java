package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossEffectSet;
import com.goodbird.cnpcgeckoaddon.data.BossParticleCue;
import com.goodbird.cnpcgeckoaddon.data.BossPlatformSettings;
import com.goodbird.cnpcgeckoaddon.data.BossSoundCue;
import com.goodbird.cnpcgeckoaddon.mixin.IBossController;
import com.goodbird.cnpcgeckoaddon.utils.BossFloorUtil;
import com.goodbird.cnpcgeckoaddon.utils.TickQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Holds the platforms a boss set alight: each one's outline flashes and the countdown runs for
 * the length of its fuse, the platform goes off under whoever is still standing on it, and it
 * may smoulder on for a while after, burning anyone who steps back on.
 *
 * <p>The whole mechanic is that fuse. A player who sees the outline under their feet has until
 * the countdown ends to jump to another platform, which is why the outline is drawn whatever
 * the warning settings say - an invisible fuse is not a mechanic, it is a trap - and why it
 * flashes the way the arena hazard's edge does before it opens, and burns steady once it has.
 * The outline alone was found to read as a faint dotted line from ten blocks off, so a lit
 * platform also burns over its whole floor, thicker towards the bang, raises a pillar of fire
 * in each corner, flashes as it goes and smokes while it smoulders - every part of it a
 * setting, and all of it off at densities and pillars of nought.</p>
 *
 * <p>The wait cannot be run off the ability that lit it: the boss goes back to its rotation the
 * moment the cast lands. Everything a platform needs is therefore snapshotted here - the
 * enrage bonus included - and driven from the level tick, the way a geyser's fuse is. Hits
 * never run while the queue is being walked: {@link TickQueue} takes the entries a tick works
 * on out first and runs them afterwards.</p>
 *
 * <p>Nothing here is persisted. A server that shuts down inside a fuse or a smoulder should not
 * set a platform alight under whoever logs in first on the next start.</p>
 */
public final class BossPlatformScheduler {

    /**
     * How many platforms one level tick works on. A boss burns one set at a time and a set is
     * at most a phase's sixteen platforms, so this only spreads several bosses' sets over a
     * couple of ticks; the rest keeps its place and is picked up next tick.
     */
    private static final int MAX_PER_TICK = 64;

    /**
     * Ceiling on the fill or the smoulder one repaint of one platform sows, so a sixteen by
     * sixteen floor does not spit hundreds a tick; past it the density is simply held.
     */
    static final int MAX_FILL_POINTS = 128;
    /** The same for the four pillars together, held by spacing them out rather than cutting them short. */
    static final int MAX_PILLAR_POINTS = 64;
    /** How far inside its corners a platform's pillars stand, so they rise out of the floor and not off its rim. */
    static final double PILLAR_INSET = 0.5D;
    /** One pillar particle for every this much of its height. */
    static final double PILLAR_STEP = 0.5D;
    /** How fast the smoke over a smoulder is sent up, in blocks a tick: a drift, not a jet. */
    private static final double SMOKE_RISE = 0.04D;
    /** How far the smoulder's pillars drop once the platform has gone off: half of the fuse's. */
    private static final double SMOULDER_PILLAR_SHARE = 0.5D;

    /**
     * How a platform flashes, bangs and sounds, taken off the settings on the tick the fuse was
     * lit.
     *
     * <p>Frozen with the rest of the cast for the reason the damage is: a builder editing the
     * ability while a fuse is burning must not change what the party already saw start. Split
     * out of {@link Pending} so it can be taken without a world to light one in.</p>
     */
    static final class Look {
        private final int blinkTicks;
        private final int countdownIntervalTicks;
        private final int flareMax;
        private final double flareArea;
        private final BossParticleCue outlineParticles;
        private final BossParticleCue blastParticles;
        private final BossSoundCue blastSound;
        private final double edgeSpacing;
        private final int fuseFillDensity;
        private final int fuseRampPercent;
        private final BossParticleCue fuseParticles;
        private final double pillarHeight;
        private final BossParticleCue pillarParticles;
        private final int smoulderDensity;
        private final BossParticleCue smokeParticles;
        private final BossParticleCue blastFlash;

        private Look(BossPlatformSettings platform) {
            blinkTicks = platform.getBlinkTicks();
            countdownIntervalTicks = platform.getCountdownIntervalTicks();
            flareMax = platform.getFlareMax();
            flareArea = platform.flareAreaPerPop();
            outlineParticles = platform.getOutlineParticles().copy();
            blastParticles = platform.getBlastParticles().copy();
            blastSound = platform.getBlastSound().copy();
            edgeSpacing = platform.edgeSpacing();
            fuseFillDensity = platform.getFuseFillDensity();
            fuseRampPercent = platform.getFuseRampPercent();
            fuseParticles = platform.getFuseParticles().copy();
            pillarHeight = platform.pillarHeight();
            pillarParticles = platform.getPillarParticles().copy();
            smoulderDensity = platform.getSmoulderDensity();
            smokeParticles = platform.getSmokeParticles().copy();
            blastFlash = platform.getBlastFlash().copy();
        }

        int blinkTicks() {
            return blinkTicks;
        }

        int countdownIntervalTicks() {
            return countdownIntervalTicks;
        }

        /**
         * How many pops a platform of this much floor throws up as it goes off.
         *
         * <p>A cap of nothing is a bang with no pops at all, the way a particle cue set to no
         * particles is; anywhere above that the count is held at one, so the smallest platform
         * still reads as going off.</p>
         */
        int pops(double area) {
            return flareMax <= 0 ? 0
                    : Mth.clamp((int) Math.round(area / flareArea), 1, flareMax);
        }

        BossParticleCue outlineParticles() {
            return outlineParticles;
        }

        BossParticleCue blastParticles() {
            return blastParticles;
        }

        BossSoundCue blastSound() {
            return blastSound;
        }

        /** Blocks between two points of the outline. */
        double edgeSpacing() {
            return edgeSpacing;
        }

        /** How much fire this much floor gets on one repaint of the fuse, this far into it. */
        int fusePoints(double area, float progress) {
            return fillPoints(fuseFillDensity, area, fuseRampPercent, progress);
        }

        /** How much smoulder - and as much smoke - it gets on one repaint after it went off. */
        int smoulderPoints(double area) {
            return fillPoints(smoulderDensity, area, 0, 0.0F);
        }

        BossParticleCue fuseParticles() {
            return fuseParticles;
        }

        /** How high the pillars in the corners rise while the fuse burns, in blocks. */
        double pillarHeight() {
            return pillarHeight;
        }

        BossParticleCue pillarParticles() {
            return pillarParticles;
        }

        BossParticleCue smokeParticles() {
            return smokeParticles;
        }

        BossParticleCue blastFlash() {
            return blastFlash;
        }
    }

    /** What this platform will look and sound like, whatever the builder does next. */
    static Look look(BossPlatformSettings platform) {
        return new Look(platform);
    }

    /**
     * How many particles a floor of {@code area} square blocks gets on one repaint: {@code density}
     * per ten of them, grown by {@code rampPercent} over the fuse, and held under the ceiling.
     *
     * <p>Rounded rather than cut, so a small platform at a low density still gets its one or
     * two; {@link BossTelegraphPaint#NO_END} and anything else off the fuse counts as its start.</p>
     */
    static int fillPoints(int density, double area, int rampPercent, float progress) {
        double grown = 1.0D + rampPercent / 100.0D * Mth.clamp(progress, 0.0F, 1.0F);
        return Mth.clamp((int) Math.round(density * area / 10.0D * grown), 0, MAX_FILL_POINTS);
    }

    /**
     * Where the four pillars stand: the corners of the box, each {@link #PILLAR_INSET} in on
     * both sides, in the order the outline walks them. A box too narrow for the inset folds them
     * onto one another rather than putting them outside it.
     */
    static Vec3[] pillarCorners(AABB box, double y) {
        double west = Math.min(box.minX + PILLAR_INSET, box.getCenter().x);
        double east = Math.max(box.maxX - PILLAR_INSET, box.getCenter().x);
        double north = Math.min(box.minZ + PILLAR_INSET, box.getCenter().z);
        double south = Math.max(box.maxZ - PILLAR_INSET, box.getCenter().z);
        return new Vec3[]{
                new Vec3(west, y, north), new Vec3(east, y, north),
                new Vec3(east, y, south), new Vec3(west, y, south)};
    }

    /**
     * How high above the floor each particle of one pillar sits: one every {@link #PILLAR_STEP}
     * up to {@code height}, the topmost on it, and nothing at all for no height. Held to a
     * quarter of {@link #MAX_PILLAR_POINTS} by spacing them out, so a pillar keeps its height
     * and loses only its density.
     */
    static double[] pillarHeights(double height) {
        if (height <= 0.0D) {
            return new double[0];
        }
        int steps = Mth.clamp((int) Math.round(height / PILLAR_STEP), 1, MAX_PILLAR_POINTS / 4);
        double[] heights = new double[steps];
        for (int i = 0; i < steps; i++) {
            heights[i] = height * (i + 1) / steps;
        }
        return heights;
    }

    /**
     * One platform's clock, kept apart from the world so the fuse, the blast and the doses of its
     * smoulder can be tested without one.
     *
     * <p>The smoulder and its doses are counted from when the platform really went off, the way a
     * cone series counts its pauses: a tick that held the platform up - more than a tick's worth
     * of platforms coming due at once - moves the rest along with it rather than landing every
     * dose that fell due in the meantime at once.</p>
     */
    static final class Burn {
        private final long blastAt;
        private final int lingerTicks;
        private final int intervalTicks;
        private boolean blasted;
        private long endsAt;
        private long nextDoseAt;

        Burn(long litAt, int fuseTicks, int lingerTicks, int intervalTicks) {
            this.blastAt = litAt + Math.max(0, fuseTicks);
            this.lingerTicks = Math.max(0, lingerTicks);
            this.intervalTicks = Math.max(1, intervalTicks);
        }

        /** The tick the fuse runs out on. */
        long blastAt() {
            return blastAt;
        }

        /** Whether the fuse is still burning on this tick: the outline flashes, and nobody is hit yet. */
        boolean isFusing(long gameTime) {
            return !blasted && gameTime < blastAt;
        }

        /** Takes the blast off the clock: true exactly once, on the first tick at or past the end of the fuse. */
        boolean takeBlast(long gameTime) {
            if (blasted || gameTime < blastAt) {
                return false;
            }
            blasted = true;
            endsAt = gameTime + lingerTicks;
            nextDoseAt = gameTime + intervalTicks;
            return true;
        }

        /** Takes one dose of the smoulder off the clock: true on each tick one falls due, the last on its final tick. */
        boolean takeDose(long gameTime) {
            if (!blasted || gameTime < nextDoseAt || nextDoseAt > endsAt) {
                return false;
            }
            nextDoseAt = gameTime + intervalTicks;
            return true;
        }

        /** Whether the platform has gone off and is done smouldering: from here it is out. */
        boolean isOver(long gameTime) {
            return blasted && gameTime >= endsAt;
        }
    }

    /** One platform, from the moment its fuse is lit until it is out. */
    private static final class Pending {
        private final ResourceKey<Level> dimension;
        private final EntityNPCInterface boss;
        private final AABB box;
        /** The height the outline is drawn at, and the wave runs out from. */
        private final double floorY;
        /** Whether this one sends the countdown: only the first platform of a cast does. */
        private final boolean announces;
        /** What it hits for, how hard it shoves and throws, enrage already counted in. */
        private final int damage;
        private final int knockback;
        private final int launch;
        private final BossEffectSet effects;
        private final String vfx;
        /** How the boss had its waves tuned when this was lit; see BossWaveTuning. */
        private final BossWaveTuning wave;
        /** How it flashes, bangs and sounds, taken off the settings on the same tick. */
        private final Look look;
        private final long litAt;
        private final Burn burn;

        private Pending(ResourceKey<Level> dimension, EntityNPCInterface boss, AABB box, double floorY,
                        boolean announces, BossPlatformSettings platform, int damage, int knockback,
                        int launch, long litAt) {
            this.dimension = dimension;
            this.boss = boss;
            this.box = box;
            this.floorY = floorY;
            this.announces = announces;
            this.damage = damage;
            this.knockback = knockback;
            this.launch = launch;
            this.effects = platform.getEffects();
            this.vfx = platform.getVfx();
            this.wave = BossWaveTuning.of(boss, this.vfx);
            this.look = look(platform);
            this.litAt = litAt;
            this.burn = new Burn(litAt, platform.getFuseTicks(), platform.getLingerTicks(),
                    platform.getLingerIntervalTicks());
        }
    }

    private static final TickQueue<Pending> PENDING = new TickQueue<>("boss platforms", MAX_PER_TICK);

    private BossPlatformScheduler() {
    }

    /**
     * Lights the fuse under one platform.
     *
     * @param floorY    the height the outline is drawn at
     * @param announces whether this platform sends the cast's countdown
     * @param damage    what the platform hits for, with the enrage bonus already in it
     * @param knockback how hard it shoves, in the same already-scaled terms
     * @param launch    how hard it throws, the same again
     */
    public static void schedule(ServerLevel level, EntityNPCInterface boss, AABB box, double floorY,
                                boolean announces, BossPlatformSettings platform, int damage, int knockback,
                                int launch, long gameTime) {
        PENDING.add(new Pending(level.dimension(), boss, box, floorY, announces, platform, damage, knockback,
                launch, gameTime));
        // One hiss as the fuse catches, for the player who is not looking down.
        Vec3 centre = box.getCenter();
        platform.getLitSound().play(level, centre.x, floorY, centre.z, SoundSource.HOSTILE);
    }

    public static boolean hasPending() {
        return !PENDING.isEmpty();
    }

    /**
     * Whether this boss still has a platform lit or smouldering; what a cast spot's stay rule,
     * the finish gate and the chains wait on, and what keeps a second set from being lit on top.
     */
    public static boolean hasPending(EntityNPCInterface boss) {
        return !PENDING.isEmpty() && PENDING.find(pending -> pending.boss == boss) != null;
    }

    public static void tick(ServerLevel level) {
        long gameTime = level.getGameTime();
        PENDING.sweep(pending -> pending.dimension.equals(level.dimension()),
                pending -> tickPlatform(level, pending, gameTime));
    }

    /** Drops anything still burning in a level that is going away. */
    public static void clear(ServerLevel level) {
        PENDING.removeIf(pending -> pending.dimension.equals(level.dimension()));
    }

    /**
     * Puts out every platform one boss set alight, for its death, the end of its fight and a
     * change of phase.
     *
     * <p>A platform is the boss doing something, the way its sweeping beam is, not a mine left in
     * the arena: a phase that is over or a boss that is dead owes the party nothing more.</p>
     */
    public static void clearBoss(EntityNPCInterface boss) {
        if (PENDING.isEmpty()) {
            return;
        }
        PENDING.removeIf(pending -> pending.boss == boss);
    }

    /** @return whether this platform is still lit or smouldering and belongs back in the queue */
    private static boolean tickPlatform(ServerLevel level, Pending pending, long gameTime) {
        TeleportPathController controller = controllerOf(pending.boss);
        if (controller == null || !pending.boss.isAlive() || pending.boss.isRemoved()) {
            return false;
        }
        Burn burn = pending.burn;
        if (burn.isFusing(gameTime)) {
            if (pending.announces
                    && (gameTime - pending.litAt) % pending.look.countdownIntervalTicks() == 0L) {
                announceCountdown(level, controller, pending, gameTime);
            }
            if (gameTime % controller.telegraphIntervalTicks() == 0L
                    && hasAudience(level, pending)) {
                float progress = fuseProgress(pending, gameTime);
                outline(level, controller, pending, progress,
                        (gameTime / pending.look.blinkTicks()) % 2L == 0L);
                // The fire over the floor and the pillars in the corners burn steady through the
                // flash: only the outline blinks, so the platform never vanishes whole for half of
                // every flash, and the fire thickens towards the bang so the party can read how
                // long is left without the countdown.
                scatter(level, pending, pending.look.fuseParticles(),
                        pending.look.fusePoints(floorArea(pending.box), progress));
                pillars(level, pending, pending.look.pillarHeight());
            }
            return true;
        }
        if (burn.takeBlast(gameTime)) {
            blast(level, controller, pending);
        } else if (burn.takeDose(gameTime)) {
            dose(level, controller, pending);
        }
        if (burn.isOver(gameTime)) {
            return false;
        }
        if (gameTime % controller.telegraphIntervalTicks() == 0L && hasAudience(level, pending)) {
            // Steady from here on: the outline no longer blinks, embers and as much smoke over
            // the whole floor say the platform is still burning, and the pillars drop to half
            // so a smoulder reads as less than a fuse and not as another one.
            outline(level, controller, pending, BossTelegraphPaint.NO_END, true);
            int points = pending.look.smoulderPoints(floorArea(pending.box));
            scatter(level, pending, pending.look.outlineParticles(), points);
            scatter(level, pending, pending.look.smokeParticles(), points, SMOKE_RISE);
            pillars(level, pending, pending.look.pillarHeight() * SMOULDER_PILLAR_SHARE);
        }
        return true;
    }

    /**
     * The platform going off: the wave out of its middle, the flare and the bang, and then
     * everyone still standing on it hit, shoved away from its middle and thrown up.
     */
    private static void blast(ServerLevel level, TeleportPathController controller, Pending pending) {
        AABB box = pending.box;
        Vec3 centre = box.getCenter();
        Vec3 ground = new Vec3(centre.x, pending.floorY, centre.z);
        double radius = halfDiagonal(box);
        // All three started before the hits, so what a player sees and hears goes out at the same
        // moment the damage lands rather than a tick behind it. No block wave: the platform is the
        // builder's, and lifting its floor out from under the party is not the mechanic.
        BossAreaVfxScheduler.schedule(level, ground, pending.vfx, radius,
                BossCoverRuntime.waveDuration(radius), false, pending.wave);
        if (hasAudience(level, pending)) {
            // The outline once more as it goes: the same shape the fuse flashed, so a band
            // does not blink out of existence for the tick of the bang. The flare itself is
            // not a shape on the floor and stays the dust it always was.
            outline(level, controller, pending, BossTelegraphPaint.NO_END, true);
            scatter(level, pending, pending.look.blastParticles(), pending.look.pops(floorArea(box)));
            // One flash a block over the middle of the floor, so the bang is seen from across
            // the arena and not only heard; a floor that is not there gets it at the outline's height.
            BlockPos floor = BossFloorUtil.findFloor(level, ground.x, ground.y, ground.z);
            double flashY = (floor == null ? ground.y : floor.getY() + 1.0D) + 1.0D;
            pending.look.blastFlash().emitDust(level, ground.x, flashY, ground.z, 0.0D, 0.0D, 0.0D, 0.0D,
                    BossAbilityKind.PLATFORM);
        }
        pending.look.blastSound().play(level, ground.x, ground.y, ground.z, SoundSource.HOSTILE);

        for (LivingEntity victim : controller.platformVictims(level, box)) {
            // The shove and the throw are this platform's own half rather than something on top of
            // the hit, the geyser's rule: a totem this platform may not break is left standing, not moved.
            if (BossAbilityDamageUtil.passesBy(victim, BossAbilityKind.PLATFORM)) {
                continue;
            }
            BossAbilityDamageUtil.hit(victim, BossAbilityKind.PLATFORM, pending.boss, pending.damage,
                    pending.effects, 0, 0.0D, 0.0D);
            // Given whether the damage landed or not, the cone strike's way: a platform set to no
            // damage still throws everyone off it, and a victim still in their hurt cooldown is not
            // left standing in the smoulder. Vanilla shoves against the vector it is handed, so the
            // way to the middle throws the victim straight out from it.
            BossConeRuntime.shove(victim, pending.knockback, ground.x - victim.getX(), ground.z - victim.getZ());
            BossGeyserScheduler.launch(victim, pending.launch);
        }
    }

    /**
     * One dose of the smoulder on everyone standing on the platform, by the same rule the blast
     * used. No shove and no throw: the smoulder is the floor, and the floor does not shove, the
     * arena hazard's rule.
     */
    private static void dose(ServerLevel level, TeleportPathController controller, Pending pending) {
        for (LivingEntity victim : controller.platformVictims(level, pending.box)) {
            BossAbilityDamageUtil.hit(victim, BossAbilityKind.PLATFORM, pending.boss, pending.damage,
                    pending.effects, 0, 0.0D, 0.0D);
        }
    }

    /** The flat distance from a platform's middle to one of its corners, which is what its wave reaches. */
    static double halfDiagonal(AABB box) {
        return 0.5D * Math.sqrt(box.getXsize() * box.getXsize() + box.getZsize() * box.getZsize());
    }

    /**
     * Decoration only, so a platform with nobody near enough to see it costs nothing. The
     * platform's own reach is added on: its edge can be a long way from its middle.
     */
    private static boolean hasAudience(ServerLevel level, Pending pending) {
        Vec3 centre = pending.box.getCenter();
        double reach = Math.max(pending.box.getXsize(), pending.box.getZsize()) * 0.5D;
        return level.getNearestPlayer(centre.x, pending.floorY, centre.z,
                BossTelegraphUtil.audienceRange(pending.boss) + reach, false) != null;
    }

    /**
     * The outline of the platform, on the floor inside it.
     *
     * <p>{@code lit} is the half of a flash the outline is there for. Dust that is not spat
     * out is gone by the next tick, so a flash used to be the absence of it; an outline drawn
     * as a band lives until it is replaced, so the dark half has to be sent as an empty
     * frame rather than as nothing at all.</p>
     */
    private static void outline(ServerLevel level, TeleportPathController controller,
                                Pending pending, float progress, boolean lit) {
        BossTelegraphPaint paint = BossTelegraphPaint.of(controller.settings(), pending.boss,
                BossTelegraphPaint.CHANNEL_PLATFORM, BossAbilityKind.PLATFORM, progress);
        if (!lit) {
            if (paint.lines()) {
                BossTelegraphFrames.blank(level, paint);
            }
            return;
        }
        AABB box = pending.box;
        BossTelegraphUtil.rectangle(level, box.minX, box.minZ, box.maxX, box.maxZ, pending.floorY, paint,
                pending.look.edgeSpacing());
    }

    /** The floor of a platform in square blocks, which its fill, its smoulder and its pops are counted off. */
    private static double floorArea(AABB box) {
        return box.getXsize() * box.getZsize();
    }

    /**
     * The pillar in each corner of the platform, one particle every half block from the floor
     * under that corner up to {@code height}.
     *
     * <p>The vertical the outline and the fill have not got: from across the arena a flat mark
     * is a thin line at the horizon, and four columns of fire are what say "that one" at a
     * glance. A corner with no floor under it gets no pillar rather than one hanging in the
     * air, the same rule the fill and the outline follow.</p>
     */
    private static void pillars(ServerLevel level, Pending pending, double height) {
        double[] heights = pillarHeights(height);
        if (heights.length == 0) {
            return;
        }
        BossParticleCue cue = pending.look.pillarParticles();
        for (Vec3 corner : pillarCorners(pending.box, pending.floorY)) {
            BlockPos floor = BossFloorUtil.findFloor(level, corner.x, corner.y, corner.z);
            if (floor == null) {
                continue;
            }
            double base = floor.getY() + 1.0D;
            for (double lift : heights) {
                cue.emitDust(level, corner.x, base + lift, corner.z, 0.0D, 0.0D, 0.0D, 0.0D,
                        BossAbilityKind.PLATFORM);
            }
        }
    }

    /** How far the fuse has burned, from the tick it was lit to the tick the platform goes. */
    private static float fuseProgress(Pending pending, long gameTime) {
        long fuse = pending.burn.blastAt() - pending.litAt;
        return fuse <= 0L ? BossTelegraphPaint.NO_END
                : Mth.clamp((float) (gameTime - pending.litAt) / fuse, 0.0F, 1.0F);
    }

    /**
     * Particles at random spots on the platform's floor, found the way its outline finds it, so
     * they come up out of the platform rather than hanging in the air over a gap in it.
     */
    private static void scatter(ServerLevel level, Pending pending, BossParticleCue cue, int points) {
        scatter(level, pending, cue, points, 0.0D);
    }

    /**
     * The same, each one sent up at {@code rise} blocks a tick; nought is the puff left where it
     * was put, which is what a flame wants and smoke does not.
     */
    private static void scatter(ServerLevel level, Pending pending, BossParticleCue cue, int points,
                                double rise) {
        AABB box = pending.box;
        RandomSource random = level.getRandom();
        for (int i = 0; i < points; i++) {
            double x = box.minX + random.nextDouble() * box.getXsize();
            double z = box.minZ + random.nextDouble() * box.getZsize();
            BlockPos floor = BossFloorUtil.findFloor(level, x, pending.floorY, z);
            if (floor == null) {
                continue;
            }
            double y = floor.getY() + 1.05D;
            if (rise > 0.0D) {
                cue.emitRising(level, x, y, z, rise, BossAbilityKind.PLATFORM);
            } else {
                cue.emitDust(level, x, y, z, 0.0D, 0.0D, 0.0D, 0.0D, BossAbilityKind.PLATFORM);
            }
        }
    }

    /**
     * The name and the time left, in the action bar of everyone this fight belongs to.
     *
     * <p>The arena hazard's line and its audience: the platforms are a problem set to the whole
     * party, so it goes to every participant and not only to whoever has a bar up. Rounded up,
     * so the last second reads as one rather than as none, and handed in through %s, the one
     * placeholder vanilla's translation formatter takes.</p>
     */
    private static void announceCountdown(ServerLevel level, TeleportPathController controller, Pending pending,
                                          long gameTime) {
        int seconds = (int) Math.max(1L, (pending.burn.blastAt() - gameTime + 19L) / 20L);
        Component line = Component.translatable("cnpcgeckoaddon.boss.platform_countdown",
                        Component.translatable(BossAbilityKind.LABELS[BossAbilityKind.PLATFORM]), seconds)
                .withStyle(style -> style.withColor(BossTelegraphUtil.textColor(BossAbilityKind.PLATFORM)));
        Set<ServerPlayer> audience = new LinkedHashSet<>(controller.timerBossEvent().getPlayers());
        for (UUID playerId : controller.encounterParticipants()) {
            if (level.getPlayerByUUID(playerId) instanceof ServerPlayer player) {
                audience.add(player);
            }
        }
        for (ServerPlayer player : audience) {
            player.displayClientMessage(line, true);
        }
    }

    private static TeleportPathController controllerOf(EntityNPCInterface boss) {
        return boss instanceof IBossController holder ? holder.cnpcgeckoaddon$getTeleportPathController() : null;
    }
}

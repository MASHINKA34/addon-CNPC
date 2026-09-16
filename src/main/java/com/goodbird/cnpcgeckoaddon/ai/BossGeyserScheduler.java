package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossEffectSet;
import com.goodbird.cnpcgeckoaddon.data.BossGeyserSettings;
import com.goodbird.cnpcgeckoaddon.data.BossParticleCue;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossSoundCue;
import com.goodbird.cnpcgeckoaddon.mixin.IBossController;
import com.goodbird.cnpcgeckoaddon.utils.BossFloorUtil;
import com.goodbird.cnpcgeckoaddon.utils.TickQueue;
import com.goodbird.cnpcgeckoaddon.world.TemporaryFluidStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    /** How often the mark is repainted. Every other tick reads as a steady shape. */
    private static final int MARK_INTERVAL_TICKS = 2;
    /** How the dots of a column slice scatter: the numbers the straight column was always drawn with. */
    private static final double COLUMN_SPREAD = 0.2D;
    private static final double COLUMN_SPEED = 0.08D;
    private static final double SMOKE_SPREAD = 0.25D;
    private static final double SMOKE_SPEED = 0.04D;
    /** Ceiling on the dots one puff spends over a circle, whatever the builder asks for: one packet each. */
    private static final int MAX_SCATTER_PARTICLES = 64;

    /**
     * How an eruption looks and sounds, taken off the settings on the tick the fuse was lit.
     *
     * <p>Frozen with the rest of the cast for the reason the damage is: the mark is a promise
     * made the moment it is drawn, and a builder editing the ability while it burns must not
     * change what the party is already answering. Split out of {@link Pending} so it can be
     * taken without a world to light a fuse in.</p>
     */
    static final class Look {
        private final int vfxTicks;
        private final int columnPerRadius;
        private final int columnMin;
        private final int columnMax;
        private final double boilMin;
        private final double boilMax;
        private final BossSoundCue eruptSound;
        /** The column's shape, its rise and its dots; see BossGeyserSettings.COLUMN_CONE. */
        private final int columnShape;
        private final double columnTopRadius;
        private final int columnRiseTicks;
        private final int columnPoints;
        private final BossParticleCue columnParticles;
        private final BossParticleCue columnSmoke;
        /** The strike from above, when the phase has one. */
        private final boolean skyEnabled;
        private final int skyDelayTicks;
        private final double skyHeight;
        private final int skyFallTicks;
        /** Nought for the geyser's own radius; resolved through {@link #skyRadius(double)}. */
        private final int skyRadius;
        private final double skyPress;
        private final BossEffectSet skyEffects;
        private final String skyVfx;
        private final BossParticleCue skyParticles;
        private final BossSoundCue skySound;
        private final BossSoundCue skyHitSound;
        private final BossParticleCue skyHitParticles;
        /** The residue left on the floor, when the phase leaves one. */
        private final boolean residueEnabled;
        private final int residueLifetimeTicks;
        private final int residueRadius;
        private final int residueIntervalTicks;
        private final int residueStackTicks;
        private final int residueMaxStacks;
        private final int residueDecayTicks;
        private final double residueHeight;
        private final BossEffectSet residueEffects;
        private final int residueSoundIntervalTicks;
        private final BossParticleCue residueParticles;
        private final BossSoundCue residueSound;
        private final BossParticleCue residueHitParticles;

        private Look(BossGeyserSettings geyser) {
            vfxTicks = geyser.getVfxTicks();
            columnPerRadius = geyser.getColumnPerRadiusTenths();
            columnMin = geyser.getColumnMinTenths();
            columnMax = geyser.getColumnMaxTenths();
            boilMin = geyser.getBoilMinHundredths() / 100.0D;
            boilMax = geyser.getBoilMaxHundredths() / 100.0D;
            eruptSound = geyser.getEruptSound().copy();
            columnShape = geyser.getColumnShape();
            columnTopRadius = geyser.getColumnTopRadius();
            columnRiseTicks = geyser.getColumnRiseTicks();
            columnPoints = geyser.getColumnPointsPerSlice();
            columnParticles = geyser.getColumnParticles().copy();
            columnSmoke = geyser.getColumnSmoke().copy();
            skyEnabled = geyser.isSkyEnabled();
            skyDelayTicks = geyser.getSkyDelayTicks();
            skyHeight = geyser.getSkyHeight();
            skyFallTicks = geyser.getSkyFallTicks();
            skyRadius = geyser.getSkyRadius();
            skyPress = geyser.getSkyPress();
            skyEffects = geyser.getSkyEffects();
            skyVfx = geyser.getSkyVfx();
            skyParticles = geyser.getSkyParticles().copy();
            skySound = geyser.getSkySound().copy();
            skyHitSound = geyser.getSkyHitSound().copy();
            skyHitParticles = geyser.getSkyHitParticles().copy();
            residueEnabled = geyser.isResidueEnabled();
            residueLifetimeTicks = geyser.getResidueLifetimeTicks();
            residueRadius = geyser.getResidueRadius();
            residueIntervalTicks = geyser.getResidueIntervalTicks();
            residueStackTicks = geyser.getResidueStackTicks();
            residueMaxStacks = geyser.getResidueMaxStacks();
            residueDecayTicks = geyser.getResidueDecayTicks();
            residueHeight = geyser.getResidueHeight();
            residueEffects = geyser.getResidueEffects();
            residueSoundIntervalTicks = geyser.getResidueSoundIntervalTicks();
            residueParticles = geyser.getResidueParticles().copy();
            residueSound = geyser.getResidueSound().copy();
            residueHitParticles = geyser.getResidueHitParticles().copy();
        }

        int vfxTicks() {
            return vfxTicks;
        }

        int columnShape() {
            return columnShape;
        }

        double columnTopRadius() {
            return columnTopRadius;
        }

        int columnRiseTicks() {
            return columnRiseTicks;
        }

        int columnPoints() {
            return columnPoints;
        }

        BossParticleCue columnParticles() {
            return columnParticles;
        }

        BossParticleCue columnSmoke() {
            return columnSmoke;
        }

        boolean skyEnabled() {
            return skyEnabled;
        }

        int skyDelayTicks() {
            return skyDelayTicks;
        }

        double skyHeight() {
            return skyHeight;
        }

        int skyFallTicks() {
            return skyFallTicks;
        }

        /** The strike's radius over a geyser of {@code radius}: its own, or the geyser's when it has none. */
        double skyRadius(double radius) {
            return skyRadius > 0 ? skyRadius : radius;
        }

        double skyPress() {
            return skyPress;
        }

        BossEffectSet skyEffects() {
            return skyEffects;
        }

        String skyVfx() {
            return skyVfx;
        }

        BossParticleCue skyParticles() {
            return skyParticles;
        }

        BossSoundCue skySound() {
            return skySound;
        }

        BossSoundCue skyHitSound() {
            return skyHitSound;
        }

        BossParticleCue skyHitParticles() {
            return skyHitParticles;
        }

        boolean residueEnabled() {
            return residueEnabled;
        }

        int residueLifetimeTicks() {
            return residueLifetimeTicks;
        }

        /** The residue's radius over a geyser of {@code radius}: its own, or the geyser's when it has none. */
        double residueRadius(double radius) {
            return residueRadius > 0 ? residueRadius : radius;
        }

        int residueIntervalTicks() {
            return residueIntervalTicks;
        }

        int residueStackTicks() {
            return residueStackTicks;
        }

        int residueMaxStacks() {
            return residueMaxStacks;
        }

        int residueDecayTicks() {
            return residueDecayTicks;
        }

        double residueHeight() {
            return residueHeight;
        }

        BossEffectSet residueEffects() {
            return residueEffects;
        }

        int residueSoundIntervalTicks() {
            return residueSoundIntervalTicks;
        }

        BossParticleCue residueParticles() {
            return residueParticles;
        }

        BossSoundCue residueSound() {
            return residueSound;
        }

        BossParticleCue residueHitParticles() {
            return residueHitParticles;
        }

        /** How tall the column comes up over a circle of this radius; nought draws none at all. */
        double columnHeight(double radius) {
            return Mth.clamp(radius * columnPerRadius / 10.0D, columnMin / 10.0D, columnMax / 10.0D);
        }

        /**
         * The column that comes up out of a circle of this radius, ready to be drawn from the
         * floor: a cone off the whole circle, or the line at the middle every older boss has.
         */
        BossGeyserColumn column(double radius) {
            boolean cone = columnShape == BossGeyserSettings.COLUMN_CONE;
            return new BossGeyserColumn(cone ? radius : 0.0D, cone ? columnTopRadius : 0.0D,
                    columnHeight(radius), columnRiseTicks, cone ? columnPoints : 1,
                    countOf(columnParticles), countOf(columnSmoke), false);
        }

        /**
         * The column that falls back onto a circle of this radius, drawn from its narrow top
         * down: the cone the other way up, in the strike's own particle over the column's smoke.
         */
        BossGeyserColumn skyColumn(double radius) {
            boolean cone = columnShape == BossGeyserSettings.COLUMN_CONE;
            return new BossGeyserColumn(cone ? radius : 0.0D, cone ? columnTopRadius : 0.0D,
                    skyHeight, skyFallTicks, cone ? columnPoints : 1,
                    countOf(skyParticles), countOf(columnSmoke), true);
        }

        /** The residue's clock numbers, as they stood on the cast. */
        BossGeyserResidue.Rules residueRules() {
            return new BossGeyserResidue.Rules(residueLifetimeTicks, residueIntervalTicks, residueStackTicks,
                    residueMaxStacks, residueDecayTicks, residueSoundIntervalTicks);
        }

        /** What a cue spends per dot, or nothing at all while it is switched off. */
        private static int countOf(BossParticleCue cue) {
            return cue.isEnabled() ? cue.getCount() : 0;
        }

        /** How hard the boil spits this far into the fuse. */
        double boilSpeed(double burned) {
            return Mth.lerp(burned, boilMin, boilMax);
        }

        BossSoundCue eruptSound() {
            return eruptSound;
        }
    }

    /** What this eruption will look and sound like, whatever the builder does next. */
    static Look look(BossGeyserSettings geyser) {
        return new Look(geyser);
    }

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
        /** What the strike from above and a dose of the residue hit for, enrage already counted in. */
        private final int skyDamage;
        private final int residueDamage;
        private final BossEffectSet effects;
        private final String vfx;
        /** How the boss had its waves tuned when this was lit; see BossWaveTuning. */
        private final BossWaveTuning wave;
        /** The same for the strike from above, whose wave may be drawn in another style. */
        private final BossWaveTuning skyWave;
        /** How the boss was drawing its warnings when this was lit; see BossTelegraphPaint. */
        private final BossTelegraphPaint.Settings telegraph;
        private final boolean blockWave;
        /** How it looks and sounds, taken off the settings on the same tick. */
        private final Look look;
        /** null when the eruption leaves nothing behind. */
        private final BlockState fluid;
        private final int fluidLifetimeTicks;
        private final long litAt;
        private final long eruptsAt;
        /** Where the column comes up; moves under a followed victim, otherwise fixed. */
        private Vec3 pos;

        private Pending(ResourceKey<Level> dimension, EntityNPCInterface boss, int followId,
                        double radius, int damage, int launch, int skyDamage, int residueDamage,
                        BossEffectSet effects, String vfx,
                        BossWaveTuning wave, BossWaveTuning skyWave, BossTelegraphPaint.Settings telegraph,
                        boolean blockWave, Look look, BlockState fluid,
                        int fluidLifetimeTicks, long litAt, long eruptsAt, Vec3 pos) {
            this.dimension = dimension;
            this.boss = boss;
            this.followId = followId;
            this.radius = radius;
            this.damage = damage;
            this.launch = launch;
            this.skyDamage = skyDamage;
            this.residueDamage = residueDamage;
            this.effects = effects;
            this.vfx = vfx;
            this.wave = wave;
            this.skyWave = skyWave;
            this.telegraph = telegraph;
            this.blockWave = blockWave;
            this.look = look;
            this.fluid = fluid;
            this.fluidLifetimeTicks = fluidLifetimeTicks;
            this.litAt = litAt;
            this.eruptsAt = eruptsAt;
            this.pos = pos;
        }
    }

    /**
     * One column being drawn, from the tick it started coming up until its last slice.
     *
     * <p>Decoration and nothing else: it is neither the cast the stay rule waits on nor a
     * thing the boss owes the party, so a fight goes on round it and only the boss' death or
     * the level going away takes it down early.</p>
     */
    private static final class Column {
        private final ResourceKey<Level> dimension;
        private final EntityNPCInterface boss;
        /** Where the column's floor slice lies. */
        private final Vec3 pos;
        private final BossGeyserColumn shape;
        private final BossParticleCue particles;
        private final BossParticleCue smoke;

        private Column(ResourceKey<Level> dimension, EntityNPCInterface boss, Vec3 pos,
                       BossGeyserColumn shape, BossParticleCue particles, BossParticleCue smoke) {
            this.dimension = dimension;
            this.boss = boss;
            this.pos = pos;
            this.shape = shape;
            this.particles = particles;
            this.smoke = smoke;
        }
    }

    /**
     * One strike from above, from the tick the ground opened until it lands.
     *
     * <p>Part of the cast, unlike a column: the stay rule, the finish gate and the chains
     * wait for it the way they wait for the fuse, since a boss that has thrown the party up
     * still owes them what comes down.</p>
     */
    private static final class Sky {
        private final ResourceKey<Level> dimension;
        private final EntityNPCInterface boss;
        /** Where the ground opened: the strike lands there whatever the victim did since. */
        private final Vec3 pos;
        private final double radius;
        /** What the landing hits for, enrage already counted in. */
        private final int damage;
        private final Look look;
        private final BossWaveTuning wave;
        private final BossTelegraphPaint.Settings telegraph;
        private final BossGeyserSky clock;
        private final BossGeyserColumn column;

        private Sky(ResourceKey<Level> dimension, EntityNPCInterface boss, Vec3 pos, double radius,
                    int damage, Look look, BossWaveTuning wave, BossTelegraphPaint.Settings telegraph,
                    BossGeyserSky clock, BossGeyserColumn column) {
            this.dimension = dimension;
            this.boss = boss;
            this.pos = pos;
            this.radius = radius;
            this.damage = damage;
            this.look = look;
            this.wave = wave;
            this.telegraph = telegraph;
            this.clock = clock;
            this.column = column;
        }
    }

    /**
     * One residue, from the eruption that left it until it dries up.
     *
     * <p>A remainder rather than the cast: the stay rule, the finish gate and the chains do
     * not wait for it, and the boss is back on its rotation while it lies there. It still
     * goes with the boss, since it is the boss' and not the arena's.</p>
     */
    private static final class Residue {
        private final ResourceKey<Level> dimension;
        private final EntityNPCInterface boss;
        private final Vec3 pos;
        private final double radius;
        /** What a dose hits for, enrage already counted in; nought is the potions alone. */
        private final int damage;
        private final Look look;
        private final BossTelegraphPaint.Settings telegraph;
        private final BossGeyserResidue clock;

        private Residue(ResourceKey<Level> dimension, EntityNPCInterface boss, Vec3 pos, double radius,
                        int damage, Look look, BossTelegraphPaint.Settings telegraph, BossGeyserResidue clock) {
            this.dimension = dimension;
            this.boss = boss;
            this.pos = pos;
            this.radius = radius;
            this.damage = damage;
            this.look = look;
            this.telegraph = telegraph;
            this.clock = clock;
        }
    }

    private static final TickQueue<Pending> PENDING = new TickQueue<>("boss geysers", MAX_PER_TICK);
    private static final TickQueue<Column> COLUMNS = new TickQueue<>("boss geyser columns", MAX_PER_TICK);
    private static final TickQueue<Sky> SKIES = new TickQueue<>("boss geyser sky strikes", MAX_PER_TICK);
    private static final TickQueue<Residue> RESIDUES = new TickQueue<>("boss geyser residues", MAX_PER_TICK);

    private BossGeyserScheduler() {
    }

    /**
     * Lights one geyser under {@code victim}.
     *
     * @param fluid         the pool the eruption leaves behind, or null for none
     * @param damage        what the eruption hits for, with the enrage bonus already in it
     * @param launch        how hard it throws, in the same already-scaled terms
     * @param skyDamage     what the strike from above hits for, in the same terms
     * @param residueDamage what a dose of the residue hits for, in the same terms
     * @return whether a mark was really lit, i.e. whether there was floor to lay it on
     */
    public static boolean schedule(ServerLevel level, EntityNPCInterface boss, LivingEntity victim,
                                   BossPhaseData phase, BlockState fluid, int damage, int launch,
                                   int skyDamage, int residueDamage, long gameTime) {
        Vec3 point = groundUnder(level, victim);
        if (point == null) {
            return false;
        }
        PENDING.add(new Pending(level.dimension(), boss,
                phase.geyser().isFollowTarget() ? victim.getId() : -1,
                phase.geyser().getRadius(), damage, launch, skyDamage, residueDamage,
                phase.geyser().getEffects(),
                phase.geyser().getVfx(), BossWaveTuning.of(boss, phase.geyser().getVfx()),
                BossWaveTuning.of(boss, phase.geyser().getSkyVfx()),
                BossTelegraphPaint.Settings.of(boss),
                phase.geyser().isBlockWave(), look(phase.geyser()), fluid,
                phase.geyser().getFluidLifetimeTicks(), gameTime,
                gameTime + phase.geyser().getFuseTicks(), point));
        // One hiss as the ground opens, for the player who is not looking down.
        phase.geyser().getLitSound().play(level, point.x, point.y, point.z, SoundSource.HOSTILE);
        return true;
    }

    public static boolean hasPending() {
        return !PENDING.isEmpty() || !COLUMNS.isEmpty() || !SKIES.isEmpty() || !RESIDUES.isEmpty();
    }

    /**
     * Whether this boss still has a geyser on the way - a fuse burning or a strike from above
     * still to land; what a cast spot's stay rule, the finish gate and the chains wait on.
     *
     * <p>Neither a column still being drawn nor a residue lying there is counted: the one is
     * what the eruption looked like and the other what it left, not something the boss is
     * still doing.</p>
     */
    public static boolean hasPending(EntityNPCInterface boss) {
        return !PENDING.isEmpty() && PENDING.find(pending -> pending.boss == boss) != null
                || !SKIES.isEmpty() && SKIES.find(sky -> sky.boss == boss) != null;
    }

    public static void tick(ServerLevel level) {
        long gameTime = level.getGameTime();
        PENDING.sweep(pending -> pending.dimension.equals(level.dimension()),
                pending -> tickFuse(level, pending, gameTime));
        // After the fuses, so a column started by an eruption this tick draws its first
        // slices on the tick the ground opened rather than one behind it, and a strike
        // scheduled by it warns from that same tick.
        COLUMNS.sweep(column -> column.dimension.equals(level.dimension()),
                column -> tickColumn(level, column));
        SKIES.sweep(sky -> sky.dimension.equals(level.dimension()),
                sky -> tickSky(level, sky, gameTime));
        RESIDUES.sweep(residue -> residue.dimension.equals(level.dimension()),
                residue -> tickResidue(level, residue, gameTime));
    }

    /** Drops anything still waiting in a level that is going away. */
    public static void clear(ServerLevel level) {
        PENDING.removeIf(pending -> pending.dimension.equals(level.dimension()));
        COLUMNS.removeIf(column -> column.dimension.equals(level.dimension()));
        SKIES.removeIf(sky -> sky.dimension.equals(level.dimension()));
        RESIDUES.removeIf(residue -> residue.dimension.equals(level.dimension()));
    }

    /**
     * Drops the fuses one boss lit, for its death and for the end of its fight, with the
     * columns it still had coming up, the strikes it still had coming down and the residues
     * it left.
     *
     * <p>A geyser is the boss doing something, not a mine left in the floor: killing it while
     * the ground is still smoking is a win, and the arena owes the party nothing more.</p>
     */
    public static void clearBoss(EntityNPCInterface boss) {
        if (!PENDING.isEmpty()) {
            PENDING.removeIf(pending -> pending.boss == boss);
        }
        if (!COLUMNS.isEmpty()) {
            COLUMNS.removeIf(column -> column.boss == boss);
        }
        if (!SKIES.isEmpty()) {
            SKIES.removeIf(sky -> sky.boss == boss);
        }
        if (!RESIDUES.isEmpty()) {
            RESIDUES.removeIf(residue -> residue.boss == boss);
        }
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
        erupt(level, pending, gameTime);
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
        BlockPos floor = BossFloorUtil.findFloor(level, victim.getX(), victim.getY(), victim.getZ());
        return floor == null ? null : new Vec3(victim.getX(), floor.getY() + 1.0D, victim.getZ());
    }

    /**
     * Paints the fuse: the circle that is about to go off, and the boil in the middle of it.
     *
     * <p>The boil spits harder the closer the eruption gets, so the mark says how long is
     * left as well as where not to be standing.</p>
     */
    private static void markFuse(ServerLevel level, Pending pending, long gameTime) {
        if (gameTime % MARK_INTERVAL_TICKS != 0L || !seen(level, pending.boss, pending.pos)) {
            return;
        }
        double burned = fuseProgress(pending, gameTime);
        markRing(level, pending.boss, pending.telegraph, pending.pos, pending.radius, burned);
        double speed = pending.look.boilSpeed(burned);
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

    /**
     * The circle of what is about to happen at this spot, in the boss' colour and on the
     * geyser's channel, with how far along it is: the fuse's ring, and the strike's after it.
     */
    private static void markRing(ServerLevel level, EntityNPCInterface boss, BossTelegraphPaint.Settings telegraph,
                                 Vec3 pos, double radius, double progress) {
        BossTelegraphUtil.ring(level, pos, radius, BossTelegraphPaint.of(telegraph, boss,
                BossTelegraphPaint.CHANNEL_GEYSER, BossAbilityKind.GEYSER, (float) progress));
    }

    private static void erupt(ServerLevel level, Pending pending, long gameTime) {
        Vec3 pos = pending.pos;
        // Both started before the hits, so what a player sees leaves at the same moment the
        // damage lands rather than a tick behind it.
        BossAreaVfxScheduler.schedule(level, pos, pending.vfx, pending.radius,
                pending.look.vfxTicks(), pending.blockWave, pending.wave);
        startColumn(level, pending);
        if (pending.fluid != null && seen(level, pending.boss, pos)) {
            // A geyser of something shows what it is throwing before the puddle says so.
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, pending.fluid),
                    pos.x, pos.y + 0.5D, pos.z, 16, 0.4D, 0.6D, 0.4D, 0.15D);
        }
        pending.look.eruptSound().play(level, pos.x, pos.y, pos.z, SoundSource.HOSTILE);

        for (LivingEntity victim : victims(level, pending.boss, pos, pending.radius)) {
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
        if (pending.look.skyEnabled()) {
            // Nailed to where the ground opened rather than to the victim: the mark stopped
            // following them the moment it went off, and what falls lands on the same circle.
            double radius = pending.look.skyRadius(pending.radius);
            SKIES.add(new Sky(level.dimension(), pending.boss, pos, radius, pending.skyDamage, pending.look,
                    pending.skyWave, pending.telegraph,
                    new BossGeyserSky(gameTime, pending.look.skyDelayTicks(), pending.look.skyFallTicks()),
                    pending.look.skyColumn(radius)));
        }
        if (pending.look.residueEnabled()) {
            // Beside the fluid, not instead of it: the puddle is blocks the arena gets back,
            // the residue is the doses and the stacks, and a phase may want both.
            RESIDUES.add(new Residue(level.dimension(), pending.boss, pos, pending.look.residueRadius(pending.radius),
                    pending.residueDamage, pending.look, pending.telegraph,
                    new BossGeyserResidue(pending.look.residueRules(), gameTime)));
        }
    }

    /** @return whether this residue still lies there and belongs back in the queue */
    private static boolean tickResidue(ServerLevel level, Residue residue, long gameTime) {
        if (!residue.boss.isAlive() || residue.boss.isRemoved() || residue.clock.isOver(gameTime)) {
            return false;
        }
        Look look = residue.look;
        Vec3 pos = residue.pos;
        // Who is in it: within the circle, and no lower than a block under its floor - a
        // residue on a step - nor higher than it reaches, so a player thrown clear is out.
        Map<UUID, LivingEntity> inside = new LinkedHashMap<>();
        for (LivingEntity victim : victims(level, residue.boss, pos, residue.radius)) {
            double y = victim.getY();
            if (y >= pos.y - 1.0D && y <= pos.y + look.residueHeight()) {
                inside.put(victim.getUUID(), victim);
            }
        }
        boolean seen = seen(level, residue.boss, pos);
        for (BossGeyserResidue.Hit hit : residue.clock.tick(gameTime, inside.keySet())) {
            LivingEntity victim = inside.get(hit.victim());
            if (victim == null || BossAbilityDamageUtil.passesBy(victim, BossAbilityKind.GEYSER)) {
                continue;
            }
            // The damage and the potions go separately: the potions carry the stacks, and a
            // residue of no damage is the potions alone rather than a hurt() spent on nothing.
            if (residue.damage > 0) {
                BossAbilityDamageUtil.hit(victim, BossAbilityKind.GEYSER, residue.boss, residue.damage, null,
                        0, 0.0D, 0.0D);
            }
            BossAbilityDamageUtil.applyEffects(victim, BossAbilityKind.GEYSER, residue.boss, look.residueEffects(),
                    hit.stacks());
            if (seen) {
                look.residueHitParticles().emitDust(level, victim.getX(), victim.getY() + 0.3D, victim.getZ(),
                        0.3D, 0.2D, 0.3D, 0.02D, BossAbilityKind.GEYSER);
            }
        }
        if (residue.clock.soundDue(gameTime)) {
            look.residueSound().play(level, pos.x, pos.y, pos.z, SoundSource.HOSTILE);
        }
        if (seen && gameTime % MARK_INTERVAL_TICKS == 0L) {
            // The outline says where the residue lies and how long is left of it, the way the
            // fuse's ring says how long is left of the fuse; the bubbles over the whole
            // circle say it is still wet.
            markRing(level, residue.boss, residue.telegraph, pos, residue.radius, residue.clock.progress(gameTime));
            scatter(level, pos, residue.radius, look.residueParticles());
        }
        return true;
    }

    /**
     * Everyone a circle of the geyser's may catch.
     *
     * <p>Asked of the boss that lit the fuse rather than worked out here, so a geyser and an
     * area slam can never end up with different ideas of who counts as an enemy.</p>
     */
    private static List<LivingEntity> victims(ServerLevel level, EntityNPCInterface boss, Vec3 centre,
                                              double radius) {
        TeleportPathController controller = boss instanceof IBossController holder
                ? holder.cnpcgeckoaddon$getTeleportPathController() : null;
        return controller == null ? List.of() : controller.geyserVictims(level, centre, radius);
    }

    /** @return whether this strike is still on its way and belongs back in the queue */
    private static boolean tickSky(ServerLevel level, Sky sky, long gameTime) {
        if (!sky.boss.isAlive() || sky.boss.isRemoved()) {
            return false;
        }
        BossGeyserSky clock = sky.clock;
        if (clock.takeStart(gameTime)) {
            // One splash from up where the column starts, for the player who is not looking up.
            sky.look.skySound().play(level, sky.pos.x, sky.pos.y + sky.look.skyHeight(), sky.pos.z,
                    SoundSource.HOSTILE);
        }
        if (clock.takeStrike(gameTime)) {
            // Whatever the particle ceiling still held back of the column lands with it.
            drawSkyColumn(level, sky);
            strike(level, sky);
            return false;
        }
        if (clock.isFalling(gameTime)) {
            drawSkyColumn(level, sky);
        }
        // Warned for the whole way, from the eruption to the landing: a player the eruption
        // threw up reads what is about to come down on the spot they will land on.
        if (gameTime % MARK_INTERVAL_TICKS == 0L && seen(level, sky.boss, sky.pos)) {
            markRing(level, sky.boss, sky.telegraph, sky.pos, sky.radius, clock.warnProgress(gameTime));
        }
        return true;
    }

    /** The falling column's next slices, top down; its clock runs whether or not anyone is watching. */
    private static void drawSkyColumn(ServerLevel level, Sky sky) {
        if (sky.column.isDone()) {
            return;
        }
        boolean seen = seen(level, sky.boss, sky.pos);
        ParticleOptions particles = seen ? options(sky.look.skyParticles()) : null;
        ParticleOptions smoke = seen ? options(sky.look.columnSmoke()) : null;
        sky.column.advance(step -> drawSlice(level, sky.pos, sky.column, step,
                particles, sky.look.skyParticles().getCount(), smoke, sky.look.columnSmoke().getCount()));
    }

    /**
     * The strike landing: its wave and its splash, then whoever stands in the circle hit, and
     * whoever is still off the floor - thrown by the eruption, most likely - pressed back down
     * onto it.
     */
    private static void strike(ServerLevel level, Sky sky) {
        Vec3 pos = sky.pos;
        Look look = sky.look;
        // Started before the hits, so what a player sees leaves at the same moment the damage
        // lands. No block wave: that was the eruption's, and the floor has been thrown once.
        BossAreaVfxScheduler.schedule(level, pos, look.skyVfx(), sky.radius, look.vfxTicks(), false, sky.wave);
        if (seen(level, sky.boss, pos)) {
            scatter(level, pos, sky.radius, look.skyHitParticles());
        }
        look.skyHitSound().play(level, pos.x, pos.y, pos.z, SoundSource.HOSTILE);
        for (LivingEntity victim : victims(level, sky.boss, pos, sky.radius)) {
            if (BossAbilityDamageUtil.passesBy(victim, BossAbilityKind.GEYSER)) {
                continue;
            }
            BossAbilityDamageUtil.hit(victim, BossAbilityKind.GEYSER, sky.boss, sky.damage, look.skyEffects(),
                    0, 0.0D, 0.0D);
            // Only the airborne are pressed: somebody standing on the floor has nowhere to be
            // pressed to, and the hit alone is what the strike does to them.
            if (look.skyPress() > 0.0D && !victim.onGround()) {
                press(victim, look.skyPress());
            }
        }
    }

    /**
     * Straight down, the launch's opposite, for whoever the strike caught still in the air.
     *
     * <p>The fall they were in is kept: being pressed into the floor from up there is the
     * point, and the fall damage is the strike's second half.</p>
     */
    private static void press(LivingEntity victim, double down) {
        Vec3 movement = victim instanceof ServerPlayer player ? player.getKnownMovement() : victim.getDeltaMovement();
        victim.setDeltaMovement(BossGeyserSky.pressVelocity(movement, down, victim instanceof ServerPlayer));
        // Players simulate their own movement, so the server has to push the new velocity to
        // them explicitly. hurtMarked is what makes ServerEntity send it.
        victim.hurtMarked = true;
    }

    /**
     * A cue's count spent over the whole circle, one dot at a random spot on it each, so the
     * puff reads as the circle rather than as a blob at its middle.
     */
    private static void scatter(ServerLevel level, Vec3 pos, double radius, BossParticleCue cue) {
        ParticleOptions options = options(cue);
        if (options == null) {
            return;
        }
        RandomSource random = level.getRandom();
        int count = Math.min(cue.getCount(), MAX_SCATTER_PARTICLES);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Mth.TWO_PI;
            // The root keeps the dots spread evenly over the area rather than crowding the middle.
            double distance = radius * Math.sqrt(random.nextDouble());
            level.sendParticles(options, pos.x + Math.cos(angle) * distance, pos.y + 0.2D,
                    pos.z + Math.sin(angle) * distance, 1, 0.1D, 0.1D, 0.1D, 0.05D);
        }
    }

    /**
     * Straight up, which is the one push {@code knockback} cannot be asked for. Shared with the
     * cone strike's throw, so the two go up the same way for the same number.
     */
    static void launch(LivingEntity victim, int strength) {
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

    /** Whether anyone is near enough to see what happens at this spot; decoration is skipped otherwise. */
    private static boolean seen(ServerLevel level, EntityNPCInterface boss, Vec3 pos) {
        return level.getNearestPlayer(pos.x, pos.y, pos.z, BossTelegraphUtil.audienceRange(boss), false) != null;
    }

    /**
     * Starts the column itself: what a player watching sees come up out of the mark.
     *
     * <p>Queued rather than drawn here, because it may take a while to come up: its first
     * slices go out on this same tick, the rest at the pace the phase set. A column of no
     * height is an eruption drawn without one, the way a particle count of nought is no
     * particles: the puff of what it throws is a separate thing.</p>
     */
    private static void startColumn(ServerLevel level, Pending pending) {
        BossGeyserColumn shape = pending.look.column(pending.radius);
        if (shape.slices() <= 0) {
            return;
        }
        COLUMNS.add(new Column(level.dimension(), pending.boss, pending.pos, shape,
                pending.look.columnParticles(), pending.look.columnSmoke()));
    }

    /** @return whether this column still has slices to come and belongs back in the queue */
    private static boolean tickColumn(ServerLevel level, Column column) {
        if (!column.boss.isAlive() || column.boss.isRemoved()) {
            return false;
        }
        // The clock runs whether or not anyone is watching: a column nobody saw come up is
        // not held back for the first player to arrive.
        boolean seen = seen(level, column.boss, column.pos);
        ParticleOptions particles = seen ? options(column.particles) : null;
        ParticleOptions smoke = seen ? options(column.smoke) : null;
        column.shape.advance(step -> drawSlice(level, column.pos, column.shape, step,
                particles, column.particles.getCount(), smoke, column.smoke.getCount()));
        return !column.shape.isDone();
    }

    /** What a cue spits, or null while it is switched off, set to nothing or names no particle. */
    private static ParticleOptions options(BossParticleCue cue) {
        return cue.isEnabled() && cue.getCount() > 0 ? cue.resolve(BossAbilityKind.GEYSER) : null;
    }

    /**
     * One slice of a column: its dots spread evenly round the slice's circle, the whole
     * circle turned by a random amount so the dots of one slice do not line up with the
     * next's. A straight column's one dot at the middle is the emit it always was.
     */
    private static void drawSlice(ServerLevel level, Vec3 pos, BossGeyserColumn shape, int step,
                                  ParticleOptions particles, int perPoint, ParticleOptions smoke, int smokePerPoint) {
        if (particles == null && smoke == null) {
            return;
        }
        double y = pos.y + shape.yAt(step);
        double radius = shape.radiusAt(step);
        int points = shape.points();
        double turn = level.getRandom().nextDouble() * Mth.TWO_PI;
        boolean smokeOn = smoke != null && shape.smokeOn(step);
        for (int i = 0; i < points; i++) {
            double angle = turn + i * Mth.TWO_PI / points;
            double x = pos.x + Math.cos(angle) * radius;
            double z = pos.z + Math.sin(angle) * radius;
            if (particles != null) {
                level.sendParticles(particles, x, y, z, perPoint, COLUMN_SPREAD, 0.1D, COLUMN_SPREAD, COLUMN_SPEED);
            }
            // The smoke thins out to every other slice, which keeps a tall column inside a
            // packet budget a boss fight can afford.
            if (smokeOn) {
                level.sendParticles(smoke, x, y, z, smokePerPoint, SMOKE_SPREAD, 0.1D, SMOKE_SPREAD, SMOKE_SPEED);
            }
        }
    }

    /**
     * Leaves the pool behind, on loan.
     *
     * <p>A flat disc for the reason the fluid spit's puddle is one, and through the same
     * store, so the arena comes out of the fight exactly as it went in.</p>
     *
     * <p>As wide as the circle the eruption hit, and no wider. It used to be quietly held to
     * four blocks whatever the screen said, which made every radius above that a setting that
     * lied about what it did.</p>
     */
    private static void pool(ServerLevel level, Pending pending) {
        if (pending.fluid == null) {
            return;
        }
        TemporaryFluidStore store = TemporaryFluidStore.get(level);
        BlockPos centre = BlockPos.containing(pending.pos);
        int radius = (int) pending.radius;
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

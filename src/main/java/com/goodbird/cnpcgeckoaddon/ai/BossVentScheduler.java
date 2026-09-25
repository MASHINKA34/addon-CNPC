package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossEffectSet;
import com.goodbird.cnpcgeckoaddon.data.BossParticleCue;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossSoundCue;
import com.goodbird.cnpcgeckoaddon.data.BossVentSettings;
import com.goodbird.cnpcgeckoaddon.mixin.IBossController;
import com.goodbird.cnpcgeckoaddon.utils.TickQueue;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs the vents' timers: on each beat the vents the pattern picks are outlined and hiss for
 * their warning, and then go - a blast, a flame held for a while, or a wall.
 *
 * <p>The cast only starts the timer; the boss is back on its rotation the moment it lands and
 * the vents keep their own beat meanwhile. So everything a timer needs is taken off the settings
 * on the cast - the enrage bonus included - and driven from the level tick, the way a seismic
 * series is. The beat itself lives in {@link BossVentPlan}, which knows nothing of the world;
 * this is the half that does.</p>
 *
 * <p>A timer runs until its cycles are spent, the boss casts again with a rule that stops it, or
 * its death, the end of its fight, a change of phase or the level going away drop it - all of
 * which come through {@link #clearBoss} or {@link #clear}. Nothing here is persisted: a server
 * stopped mid timer should not have the walls go off round whoever logs in first.</p>
 *
 * <p>A vent hits and spawns waves as it goes, so it never runs while the queue is being walked:
 * {@link TickQueue} takes the tick's entries out first and runs them after.</p>
 */
public final class BossVentScheduler {

    /** How many timers one level tick works on; a fight has one per boss, so this only stops a runaway. */
    private static final int MAX_PER_TICK = 32;
    /** How far out of its face a vent's particles start, so they come out of the grate and not inside it. */
    private static final double FACE_SKIN = 0.1D;

    /**
     * What a cast hands the timer: the vents it resolved and the numbers the enrage and the
     * copies' stacks turn up, already turned up.
     *
     * @param damage     what a blast, and each hit of a flame, hits for
     * @param wallDamage what a wall hits whoever it pins for, on the interval
     * @param knockback  how hard a blast throws everyone away from the face
     * @param wallPush   tenths of a block per tick a wall carries whoever is in front of it along
     */
    record Snapshot(List<BossVentGeometry.Vent> vents, int damage, int wallDamage, int knockback, int wallPush) {
        Snapshot {
            vents = List.copyOf(vents);
        }
    }

    /**
     * What a timer does and looks like, taken off the settings on the tick it was cast.
     *
     * <p>Frozen with the vents for the reason a platform's fuse is: a warning is a promise made the
     * moment it goes up, and a builder editing the ability while the timer runs must not change
     * what the party is already answering.</p>
     */
    static final class Look {
        final int damage;
        final int wallDamage;
        final int knockback;
        final int hitIntervalTicks;
        final BossEffectSet effects;
        final String burstVfx;
        final int burstVfxTicks;
        /** Blocks a tick, enrage already counted in. */
        final double wallPush;
        final double wallLift;
        final int wallMode;
        final int particleBudget;
        final int flameSoundIntervalTicks;
        final BossSoundCue hissSound;
        final BossSoundCue burstSound;
        final BossSoundCue flameSound;
        final BossSoundCue wallSound;
        final BossParticleCue flameParticles;
        final BossParticleCue smokeParticles;
        final BossParticleCue burstParticles;
        final BossParticleCue wallParticles;
        final BossParticleCue warnParticles;
        /** How the boss had its waves tuned when this was cast; see BossWaveTuning. */
        final BossWaveTuning wave;
        /** How the boss was drawing its warnings when this was cast; see BossTelegraphPaint. */
        final BossTelegraphPaint.Settings telegraph;

        private Look(BossVentSettings vent, EntityNPCInterface boss, Snapshot snapshot) {
            damage = snapshot.damage();
            wallDamage = snapshot.wallDamage();
            knockback = snapshot.knockback();
            hitIntervalTicks = vent.getHitIntervalTicks();
            effects = vent.getEffects();
            burstVfx = vent.getBurstVfx();
            burstVfxTicks = vent.getBurstVfxTicks();
            wallPush = snapshot.wallPush() / 10.0D;
            wallLift = vent.getWallLift() / 10.0D;
            wallMode = vent.getWallMode();
            particleBudget = vent.getParticleBudget();
            flameSoundIntervalTicks = vent.getFlameSoundIntervalTicks();
            hissSound = vent.getHissSound().copy();
            burstSound = vent.getBurstSound().copy();
            flameSound = vent.getFlameSound().copy();
            wallSound = vent.getWallSound().copy();
            flameParticles = vent.getFlameParticles().copy();
            smokeParticles = vent.getSmokeParticles().copy();
            burstParticles = vent.getBurstParticles().copy();
            wallParticles = vent.getWallParticles().copy();
            warnParticles = vent.getWarnParticles().copy();
            wave = BossWaveTuning.of(boss, burstVfx);
            telegraph = BossTelegraphPaint.Settings.of(boss);
        }
    }

    /** One cast's timer, from the cast until it is spent or dropped. */
    private static final class Run {
        private final ResourceKey<Level> dimension;
        private final EntityNPCInterface boss;
        private final Look look;
        /** The vents in the world, by the place the clock knows each one by. */
        private final List<BossVentGeometry.Vent> vents;
        private final BossVentPlan clock;

        private Run(ResourceKey<Level> dimension, EntityNPCInterface boss, Look look,
                    List<BossVentGeometry.Vent> vents, BossVentPlan clock) {
            this.dimension = dimension;
            this.boss = boss;
            this.look = look;
            this.vents = vents;
            this.clock = clock;
        }
    }

    private static final TickQueue<Run> RUNS = new TickQueue<>("boss vent timers", MAX_PER_TICK);

    private BossVentScheduler() {
    }

    /**
     * Starts the timer a cast has just wound up, in place of any this boss still had running.
     *
     * <p>The first beat comes on this very tick: the cast is the first volley's warning going up,
     * and a timer that waited a whole cycle first would read as a cast that did nothing.</p>
     */
    public static void start(ServerLevel level, EntityNPCInterface boss, BossPhaseData phase, Snapshot snapshot,
                             long gameTime) {
        clearBoss(boss);
        if (snapshot.vents().isEmpty()) {
            return;
        }
        BossVentSettings vent = phase.vent();
        List<BossVentPlan.Slot> slots = new ArrayList<>();
        for (BossVentGeometry.Vent placed : snapshot.vents()) {
            slots.add(new BossVentPlan.Slot(placed.weight(), placed.delayTicks(), placed.mode()));
        }
        RUNS.add(new Run(level.dimension(), boss, new Look(vent, boss, snapshot), snapshot.vents(),
                new BossVentPlan(slots, BossVentPlan.Rules.of(vent), gameTime, level.getRandom())));
    }

    public static boolean hasPending() {
        return !RUNS.isEmpty();
    }

    /**
     * Whether this boss has a timer running, or a vent of its last one still going: what a second
     * cast asks its rule about, and what the finish gate, the chains and a cast spot's stay wait on.
     */
    public static boolean hasPending(EntityNPCInterface boss) {
        return !RUNS.isEmpty() && RUNS.find(run -> run.boss == boss) != null;
    }

    /** The diagnostic command's line. */
    public static String status(EntityNPCInterface boss, long gameTime) {
        Run run = RUNS.isEmpty() ? null : RUNS.find(candidate -> candidate.boss == boss);
        return run == null ? "Vents: idle" : "Vents: " + run.clock.status(gameTime);
    }

    public static void tick(ServerLevel level) {
        long gameTime = level.getGameTime();
        RUNS.sweep(run -> run.dimension.equals(level.dimension()), run -> tickRun(level, run, gameTime));
    }

    /** Drops every timer in a level that is going away. */
    public static void clear(ServerLevel level) {
        RUNS.removeIf(run -> run.dimension.equals(level.dimension()));
    }

    /**
     * Drops the timer one boss started, for its death, the end of its fight, a phase that is over
     * and a cast that stops or restarts it: the vents are the boss doing something, not a fault in
     * the arena, and owe the party nothing once the boss has stopped.
     */
    public static void clearBoss(EntityNPCInterface boss) {
        if (RUNS.isEmpty()) {
            return;
        }
        Run run = RUNS.find(candidate -> candidate.boss == boss);
        if (run != null) {
            run.clock.stop();
        }
        RUNS.removeIf(candidate -> candidate.boss == boss);
    }

    /** @return whether this timer is still running and belongs back in the queue */
    private static boolean tickRun(ServerLevel level, Run run, long gameTime) {
        TeleportPathController controller = controllerOf(run.boss);
        if (controller == null || !run.boss.isAlive() || run.boss.isRemoved() || run.boss.level() != level) {
            return false;
        }
        run.clock.tick(gameTime, new WorldSink(level, run));
        if (gameTime % controller.telegraphIntervalTicks() == 0L) {
            paintWarnings(level, run, gameTime);
        }
        return !run.clock.isOver();
    }

    /**
     * Outlines every vent that is about to go: the volume it acts on, in the ability's colour and
     * counting down, and its warning dots over the face it fires out of. Unconditionally, the way
     * a platform's fuse is drawn: a vent nobody can see coming is a trap, not a mechanic.
     */
    private static void paintWarnings(ServerLevel level, Run run, long gameTime) {
        for (BossVentPlan.Activation activation : run.clock.activations()) {
            if (!activation.isWarning(gameTime)) {
                continue;
            }
            BossVentGeometry.Vent vent = run.vents.get(activation.vent());
            if (!seen(level, run, vent)) {
                continue;
            }
            BossTelegraphPaint paint = BossTelegraphPaint.of(run.look.telegraph, run.boss,
                    BossTelegraphPaint.CHANNEL_VENT, BossAbilityKind.VENT, activation.warnProgress(gameTime));
            BossTelegraphUtil.box(level, vent.volume(), paint);
            ParticleOptions dots = particle(run.look.warnParticles);
            if (dots != null) {
                int count = BossVentGeometry.shareBudget(run.look.particleBudget,
                        run.look.warnParticles.getCount())[0];
                scatter(level, vent, dots, count, FACE_SKIN, null);
            }
        }
    }

    /** The clock's hands in the world. */
    private static final class WorldSink implements BossVentPlan.Sink {
        private final ServerLevel level;
        private final Run run;

        private WorldSink(ServerLevel level, Run run) {
            this.level = level;
            this.run = run;
        }

        @Override
        public void warn(long now, BossVentPlan.Activation activation) {
            // One hiss as the warning goes up, for the player who is not looking at the wall.
            Vec3 face = faceCentre(run.vents.get(activation.vent()));
            run.look.hissSound.play(level, face.x, face.y, face.z, SoundSource.HOSTILE);
        }

        @Override
        public void fire(long now, BossVentPlan.Activation activation) {
        }

        @Override
        public void act(long now, BossVentPlan.Activation activation) {
        }

        @Override
        public void end(long now, BossVentPlan.Activation activation) {
        }
    }

    /** The middle of a vent's face, just out of the grate: where its noises come from. */
    private static Vec3 faceCentre(BossVentGeometry.Vent vent) {
        return BossVentGeometry.planeCentre(vent.box(), vent.face(), FACE_SKIN);
    }

    /** The particle a cue spends, or null when it is off or spends none. */
    private static ParticleOptions particle(BossParticleCue cue) {
        return cue.isEnabled() && cue.getCount() > 0 ? cue.resolve(BossAbilityKind.VENT) : null;
    }

    /**
     * {@code count} particles, each at its own random spot of the plane {@code out} blocks from the
     * vent's face: left where they are put when {@code velocity} is null, sent off at exactly it
     * otherwise - vanilla only takes an exact velocity for a count of nought, so the count is spent
     * one packet at a time.
     */
    private static void scatter(ServerLevel level, BossVentGeometry.Vent vent, ParticleOptions particle, int count,
                                double out, Vec3 velocity) {
        RandomSource random = level.getRandom();
        for (int i = 0; i < count; i++) {
            Vec3 at = BossVentGeometry.planePoint(vent.box(), vent.face(), out, random.nextDouble(), random.nextDouble());
            if (velocity == null) {
                level.sendParticles(particle, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            } else {
                level.sendParticles(particle, at.x, at.y, at.z, 0, velocity.x, velocity.y, velocity.z, 1.0D);
            }
        }
    }

    /**
     * Decoration only, so a vent with nobody near enough to see it costs nothing. The volume's own
     * size is added on: a vent can reach a long way from its middle.
     */
    private static boolean seen(ServerLevel level, Run run, BossVentGeometry.Vent vent) {
        AABB volume = vent.volume();
        Vec3 centre = volume.getCenter();
        double reach = 0.5D * Math.max(volume.getXsize(), Math.max(volume.getYsize(), volume.getZsize()));
        return level.getNearestPlayer(centre.x, centre.y, centre.z,
                BossTelegraphUtil.audienceRange(run.boss) + reach, false) != null;
    }

    private static TeleportPathController controllerOf(EntityNPCInterface boss) {
        return boss instanceof IBossController holder ? holder.cnpcgeckoaddon$getTeleportPathController() : null;
    }
}

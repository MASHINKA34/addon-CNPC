package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossEffectSet;
import com.goodbird.cnpcgeckoaddon.data.BossParticleCue;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossSoundCue;
import com.goodbird.cnpcgeckoaddon.data.BossVentSettings;
import com.goodbird.cnpcgeckoaddon.data.BossVentZone;
import com.goodbird.cnpcgeckoaddon.mixin.IBossController;
import com.goodbird.cnpcgeckoaddon.utils.TickQueue;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
     * How many ticks' worth of its speed a flame out of a vent travels before it goes out.
     *
     * <p>A flame keeps 0.96 of its speed a tick and lives twenty ticks on average, which carries it
     * about fourteen ticks' worth; sent at the reach over twelve, a stream reads as reaching about
     * as far as the vent really burns, where the reach over ten overshot it by nearly half.</p>
     */
    private static final double FLAME_TRAVEL_TICKS = 12.0D;
    /** How far a flame of the stream strays sideways, as a share of its speed: fire, not rails. */
    private static final double FLAME_SPREAD = 0.15D;
    /**
     * Blocks a tick a blast out of the floor throws a victim up at, or one out of the ceiling slams
     * them down at, per point of knockback: one is a hop, three sends them a dozen blocks high.
     */
    private static final double BLAST_LIFT_PER_KNOCKBACK = 0.5D;
    /**
     * What the server's own pass over a player takes off a vertical speed before the tracker sends
     * it: drag, and one tick of gravity before that. See BossGravityScheduler for the same sum.
     */
    private static final double VERTICAL_DRAG = 0.98D;
    private static final double GRAVITY = 0.08D;
    /** How long after somebody a vent threw up has come down their landing is still forgiven. */
    private static final int LANDING_GRACE_TICKS = 40;
    /** A throw that never lands - into water, off the map - is forgotten after this. */
    private static final int LANDING_TIMEOUT_TICKS = 600;

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

    /** Somebody a vent threw or carried up, until they come down: their landing is the vent's, not a fall. */
    private static final class Landing {
        private final ResourceKey<Level> dimension;
        /** The last tick a vent sent them up. */
        private long sentAt;
        /** Seen off the floor since; only then does being down again count as landing. */
        private boolean airborne;
        /** When they were seen down, or -1 while still in the air. */
        private long landedAt = -1L;

        private Landing(ResourceKey<Level> dimension, long sentAt) {
            this.dimension = dimension;
            this.sentAt = sentAt;
        }
    }

    private static final TickQueue<Run> RUNS = new TickQueue<>("boss vent timers", MAX_PER_TICK);
    private static final Map<UUID, Landing> LANDINGS = new HashMap<>();

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
        return !RUNS.isEmpty() || !LANDINGS.isEmpty();
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
        tickLandings(level, gameTime);
    }

    /** Drops every timer in a level that is going away, and the landings it was waiting on there. */
    public static void clear(ServerLevel level) {
        RUNS.removeIf(run -> run.dimension.equals(level.dimension()));
        LANDINGS.values().removeIf(landing -> landing.dimension.equals(level.dimension()));
    }

    /**
     * Whether this landing is somebody a vent sent up coming down, and so not a fall to be hurt by.
     *
     * <p>Called from the fall event. The record is taken out here, so the fall after this one is
     * their own again. The landings outlive the timer that sent them up: whoever a blast threw
     * as the boss died still comes down on its account.</p>
     */
    public static boolean forgiveFall(LivingEntity victim) {
        // Server state; the fall event fires on the client too, and on an integrated server that
        // is the same static map from another thread.
        if (victim.level().isClientSide || LANDINGS.isEmpty()) {
            return false;
        }
        return LANDINGS.remove(victim.getUUID()) != null;
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
        run.clock.tick(gameTime, new WorldSink(level, controller, run));
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
        private final TeleportPathController controller;
        private final Run run;

        private WorldSink(ServerLevel level, TeleportPathController controller, Run run) {
            this.level = level;
            this.controller = controller;
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
            if (activation.mode() == BossVentSettings.MODE_BURST) {
                blast(level, controller, run, run.vents.get(activation.vent()), now);
            }
        }

        @Override
        public void act(long now, BossVentPlan.Activation activation) {
            if (activation.mode() == BossVentSettings.MODE_FLAME) {
                flame(level, controller, run, run.vents.get(activation.vent()), activation.elapsed(now));
            }
        }

        @Override
        public void end(long now, BossVentPlan.Activation activation) {
        }
    }

    /**
     * A vent going off at once: the wave across the floor and the lava off the face with the bang,
     * and then everyone in front of it hit and thrown away from the face along the way it fires.
     *
     * <p>Everything seen and heard goes out before the hits, so it leaves at the moment the damage
     * lands rather than a tick behind it. The wave is a floor's: a floor vent's spreads from its
     * grate, a ceiling vent's across the floor under its reach, and a wall's has no floor of its own
     * to run on, so the lava alone says it went.</p>
     */
    private static void blast(ServerLevel level, TeleportPathController controller, Run run,
                              BossVentGeometry.Vent vent, long gameTime) {
        Look look = run.look;
        Vec3 face = faceCentre(vent);
        if (vent.face() == BossVentZone.FACE_FLOOR || vent.face() == BossVentZone.FACE_CEILING) {
            double out = vent.face() == BossVentZone.FACE_FLOOR ? 0.0D : vent.reach();
            BossAreaVfxScheduler.schedule(level, BossVentGeometry.planeCentre(vent.box(), vent.face(), out),
                    look.burstVfx, vent.reach(), look.burstVfxTicks, false, look.wave);
        }
        ParticleOptions lava = particle(look.burstParticles);
        if (lava != null && seen(level, run, vent)) {
            scatter(level, vent, lava,
                    BossVentGeometry.shareBudget(look.particleBudget, look.burstParticles.getCount())[0],
                    FACE_SKIN, null);
        }
        look.burstSound.play(level, face.x, face.y, face.z, SoundSource.HOSTILE);
        for (LivingEntity victim : controller.ventVictims(level, vent.volume())) {
            // The throw is this blast's own half rather than something on top of the hit, the
            // platform's rule: a totem this vent may not break is left standing, not thrown.
            if (BossAbilityDamageUtil.passesBy(victim, BossAbilityKind.VENT)) {
                continue;
            }
            BossAbilityDamageUtil.hit(victim, BossAbilityKind.VENT, run.boss, look.damage, look.effects,
                    0, 0.0D, 0.0D);
            throwOff(level, victim, vent, look.knockback, gameTime);
        }
    }

    /**
     * A blast's throw, whether its damage landed or not: out of a wall along the way it fires, the
     * way a knockback shoves; out of the floor straight up, with the landing forgiven; out of the
     * ceiling straight down.
     */
    private static void throwOff(ServerLevel level, LivingEntity victim, BossVentGeometry.Vent vent, int knockback,
                                 long gameTime) {
        if (knockback <= 0) {
            return;
        }
        Vec3 dir = vent.dir();
        if (BossVentGeometry.axis(vent.face()) != Direction.Axis.Y) {
            // Vanilla shoves against the vector it is handed, so the way back into the wall
            // throws the victim out along the way the vent fires.
            BossConeRuntime.shove(victim, knockback, -dir.x, -dir.z);
            return;
        }
        double speed = dir.y * knockback * BLAST_LIFT_PER_KNOCKBACK;
        // Their own run is kept: a player's as their client reported it, a mob's as it moves.
        Vec3 own = victim instanceof ServerPlayer player ? player.getKnownMovement() : victim.getDeltaMovement();
        // For a player the server's own pass runs before the send and takes a tick of gravity off
        // the throw, so that tick is put back in advance; a mob moves by exactly what it is handed.
        double y = victim instanceof ServerPlayer ? speed / VERTICAL_DRAG + GRAVITY : speed;
        victim.setDeltaMovement(own.x, y, own.z);
        if (speed > 0.0D) {
            victim.fallDistance = 0.0F;
            owesLanding(level, victim, gameTime);
        }
        // Players simulate their own movement, so the server has to push the new velocity to them
        // explicitly. hurtMarked is what makes ServerEntity send it.
        victim.hurtMarked = true;
    }

    /**
     * One tick of a vent's flame: everyone in front of it hit on the interval from the tick it
     * went, the roar on its own interval, and the stream itself - fire out of the face along the
     * way the vent fires, and smoke where it gives out at the far side - within the budget.
     */
    private static void flame(ServerLevel level, TeleportPathController controller, Run run,
                              BossVentGeometry.Vent vent, long elapsed) {
        Look look = run.look;
        if (elapsed % look.hitIntervalTicks == 0L) {
            for (LivingEntity victim : controller.ventVictims(level, vent.volume())) {
                // No throw: a flame burns whoever stands in it, it does not shove them out of it.
                BossAbilityDamageUtil.hit(victim, BossAbilityKind.VENT, run.boss, look.damage, look.effects,
                        0, 0.0D, 0.0D);
            }
        }
        if (elapsed % look.flameSoundIntervalTicks == 0L) {
            Vec3 face = faceCentre(vent);
            look.flameSound.play(level, face.x, face.y, face.z, SoundSource.HOSTILE);
        }
        if (!seen(level, run, vent)) {
            return;
        }
        ParticleOptions fire = particle(look.flameParticles);
        ParticleOptions smoke = particle(look.smokeParticles);
        int[] counts = BossVentGeometry.shareBudget(look.particleBudget,
                fire == null ? 0 : look.flameParticles.getCount(), smoke == null ? 0 : look.smokeParticles.getCount());
        if (fire != null) {
            stream(level, vent, fire, counts[0]);
        }
        if (smoke != null) {
            scatter(level, vent, smoke, counts[1], vent.reach(), null);
        }
    }

    /** {@code count} flames out of random spots of the face, each sent off along the way the vent fires. */
    private static void stream(ServerLevel level, BossVentGeometry.Vent vent, ParticleOptions fire, int count) {
        RandomSource random = level.getRandom();
        Vec3 along = vent.dir().scale(vent.reach() / FLAME_TRAVEL_TICKS);
        double spread = along.length() * FLAME_SPREAD;
        for (int i = 0; i < count; i++) {
            Vec3 at = BossVentGeometry.planePoint(vent.box(), vent.face(), FACE_SKIN,
                    random.nextDouble(), random.nextDouble());
            Vec3 velocity = along.add((random.nextDouble() - 0.5D) * spread, (random.nextDouble() - 0.5D) * spread,
                    (random.nextDouble() - 0.5D) * spread);
            level.sendParticles(fire, at.x, at.y, at.z, 0, velocity.x, velocity.y, velocity.z, 1.0D);
        }
    }

    /** Remembers that somebody a vent sent up owes the vent their landing, not a fall. */
    private static void owesLanding(ServerLevel level, LivingEntity victim, long gameTime) {
        Landing landing = LANDINGS.get(victim.getUUID());
        if (landing == null || !landing.dimension.equals(level.dimension())) {
            LANDINGS.put(victim.getUUID(), new Landing(level.dimension(), gameTime));
            return;
        }
        landing.sentAt = gameTime;
        landing.landedAt = -1L;
    }

    /**
     * Landings the fall event never reports - a splash, a slow slide down a ledge - are watched for
     * from the tick instead: down again after having been seen off the floor, and forgotten a little
     * after that, or after long enough whatever happened.
     */
    private static void tickLandings(ServerLevel level, long gameTime) {
        if (LANDINGS.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, Landing> entry : List.copyOf(LANDINGS.entrySet())) {
            Landing landing = entry.getValue();
            if (!landing.dimension.equals(level.dimension())) {
                continue;
            }
            LivingEntity victim = level.getEntity(entry.getKey()) instanceof LivingEntity found ? found : null;
            if (victim == null || !victim.isAlive() || victim.isRemoved()
                    || gameTime - landing.sentAt > LANDING_TIMEOUT_TICKS
                    || landing.landedAt >= 0L && gameTime - landing.landedAt > LANDING_GRACE_TICKS) {
                LANDINGS.remove(entry.getKey(), landing);
                continue;
            }
            boolean down = victim.onGround() || victim.isInWater() || victim.isInLava();
            if (!down) {
                landing.airborne = true;
            } else if (landing.airborne && landing.landedAt < 0L) {
                landing.landedAt = gameTime;
            }
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

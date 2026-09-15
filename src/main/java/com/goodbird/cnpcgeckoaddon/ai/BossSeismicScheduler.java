package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.ai.BossSeismicPlan.Ring;
import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossEffectSet;
import com.goodbird.cnpcgeckoaddon.data.BossParticleCue;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossSeismicSettings;
import com.goodbird.cnpcgeckoaddon.data.BossSoundCue;
import com.goodbird.cnpcgeckoaddon.mixin.IBossController;
import com.goodbird.cnpcgeckoaddon.utils.BossFloorUtil;
import com.goodbird.cnpcgeckoaddon.utils.TickQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs the seismic waves: rings of the arena floor round the boss, outlined and then landed
 * on whoever still stands in them, one pulse after another until the plan is spent, and the
 * whole series again as many times as the phase asked for.
 *
 * <p>The cast is over the moment the wind-up lands and the boss is back on its rotation while
 * the rings are still coming, so everything a series needs is taken off the settings then -
 * the enrage bonus included - and driven from the level tick, the way the geyser's fuse is.
 * The pace itself lives in {@link BossSeismicSeries}, which knows nothing of the world; this
 * is the half that does: the floor under the boss, the outlines, the hits and the waves.</p>
 *
 * <p>A series is the boss doing something for as long as it runs: nothing the boss does
 * meanwhile stops it - not a hit it takes, not another cast, not a stun - only its death, the
 * end of its fight, a change of phase and the level going away, all of which come through
 * {@link #clearBoss} or {@link #clear}. Nothing here is persisted: a series lives for seconds,
 * and a server stopped inside them should not have the floor come up under whoever logs in first.</p>
 *
 * <p>A ring damages, throws and spawns a wave as it hits, so it never runs while the queue is
 * being walked: {@link TickQueue} takes the tick's entries out first and runs them after.</p>
 */
public final class BossSeismicScheduler {

    /** How many series one level tick works on; a fight has one per boss, so this only stops a runaway. */
    private static final int MAX_PER_TICK = 32;
    /** How often an outlined ring is repainted. Every other tick reads as a steady shape, the geyser's rate. */
    private static final int MARK_INTERVAL_TICKS = 2;
    /** Ceiling on the dots one ring's pulse cue spends as it hits, whatever the builder asks for. */
    private static final int MAX_RING_PARTICLES = 64;

    /**
     * What a series does and looks like, taken off the settings on the tick it was cast.
     *
     * <p>Frozen with the plan for the reason the geyser's numbers are: the outline is a promise
     * made the moment it is drawn, and a builder editing the ability while the rings are coming
     * must not change what the party is already answering.</p>
     */
    static final class Look {
        final String animation;
        final boolean followBoss;
        final boolean rootWhileRunning;
        final double height;
        /** What a ring hits for, enrage already counted in. */
        final int damage;
        final int knockback;
        final BossEffectSet effects;
        final int hitMode;
        /** Upward throw in tenths of a block per tick, enrage already counted in. */
        final int launch;
        final int slamDelayTicks;
        final double slamStrength;
        /** What the landing after a slam hits for, enrage already counted in. */
        final int slamDamage;
        final boolean slamFallDamage;
        final int slamTimeoutTicks;
        final String vfx;
        final boolean blockWave;
        final int vfxTicks;
        final BossSoundCue pulseSound;
        final BossSoundCue hitSound;
        final BossSoundCue launchSound;
        final BossSoundCue slamSound;
        final BossParticleCue pulseParticles;
        final BossParticleCue hitParticles;
        final BossParticleCue slamParticles;
        /** How the boss had its waves tuned when this was cast; see BossWaveTuning. */
        final BossWaveTuning wave;
        /** How the boss was drawing its warnings when this was cast; see BossTelegraphPaint. */
        final BossTelegraphPaint.Settings telegraph;

        private Look(BossSeismicSettings seismic, EntityNPCInterface boss, int damage, int slamDamage, int launch) {
            animation = seismic.getAnimation();
            followBoss = seismic.isFollowBoss();
            rootWhileRunning = seismic.isRootWhileRunning();
            height = seismic.getHeight();
            this.damage = damage;
            knockback = seismic.getKnockback();
            effects = seismic.getEffects();
            hitMode = seismic.getHitMode();
            this.launch = launch;
            slamDelayTicks = seismic.getSlamDelayTicks();
            slamStrength = seismic.getSlamStrength();
            this.slamDamage = slamDamage;
            slamFallDamage = seismic.isSlamFallDamage();
            slamTimeoutTicks = seismic.getSlamTimeoutTicks();
            vfx = seismic.getVfx();
            blockWave = seismic.isBlockWave();
            vfxTicks = seismic.getVfxTicks();
            pulseSound = seismic.getPulseSound().copy();
            hitSound = seismic.getHitSound().copy();
            launchSound = seismic.getLaunchSound().copy();
            slamSound = seismic.getSlamSound().copy();
            pulseParticles = seismic.getPulseParticles().copy();
            hitParticles = seismic.getHitParticles().copy();
            slamParticles = seismic.getSlamParticles().copy();
            wave = BossWaveTuning.of(boss, seismic.getVfx());
            telegraph = BossTelegraphPaint.Settings.of(boss);
        }
    }

    /** One cast, mid-series. */
    private static final class Series {
        private final ResourceKey<Level> dimension;
        private final EntityNPCInterface boss;
        private final Look look;
        /** The floor under the boss on the cast: what every pulse is centred on unless the rings follow it. */
        private final Vec3 centre;
        private final BossSeismicSeries clock;

        private Series(ResourceKey<Level> dimension, EntityNPCInterface boss, Look look, Vec3 centre,
                       BossSeismicSeries clock) {
            this.dimension = dimension;
            this.boss = boss;
            this.look = look;
            this.centre = centre;
            this.clock = clock;
        }
    }

    /** One victim thrown up by a ring, owed the yank back down a moment later. */
    private static final class Slam {
        private final ResourceKey<Level> dimension;
        private final EntityNPCInterface boss;
        private final UUID victimId;
        private final Look look;
        private final long at;

        private Slam(ResourceKey<Level> dimension, EntityNPCInterface boss, UUID victimId, Look look, long at) {
            this.dimension = dimension;
            this.boss = boss;
            this.victimId = victimId;
            this.look = look;
            this.at = at;
        }
    }

    /** One slammed victim, still in the air as far as this knows. */
    private static final class Landing {
        private final ResourceKey<Level> dimension;
        private final EntityNPCInterface boss;
        private final Look look;
        private final long thrownAt;
        /** Set once the server has seen them off the floor, so the slam tick itself is not a landing. */
        private boolean airborne;

        private Landing(ResourceKey<Level> dimension, EntityNPCInterface boss, Look look, long thrownAt) {
            this.dimension = dimension;
            this.boss = boss;
            this.look = look;
            this.thrownAt = thrownAt;
        }
    }

    private static final TickQueue<Series> SERIES = new TickQueue<>("boss seismic series", MAX_PER_TICK);
    /** Ceiling on the slams one tick works on: a ring can throw a party, but not more than this. */
    private static final TickQueue<Slam> SLAMS = new TickQueue<>("boss seismic slams", 64);
    private static final Map<UUID, Landing> LANDINGS = new HashMap<>();

    private BossSeismicScheduler() {
    }

    /**
     * Starts the series a cast has just wound up.
     *
     * @param damage     what a ring hits for, with the enrage bonus already in it
     * @param slamDamage what the landing after a slam hits for, in the same already-scaled terms
     * @param launch     how hard a ring throws, in the same terms
     * @return whether a series really started: a boss whose series is still running gets none
     */
    public static boolean start(ServerLevel level, EntityNPCInterface boss, BossPhaseData phase, int damage,
                                int slamDamage, int launch, long gameTime) {
        if (hasPending(boss)) {
            return false;
        }
        BossSeismicSettings seismic = phase.seismic();
        Look look = new Look(seismic, boss, damage, slamDamage, launch);
        Vec3 centre = floorUnder(level, boss, look);
        // A boss over nothing within reach runs the rings from its own feet: the outlines and
        // the waves look for the floor point by point anyway, and give up on the same holes.
        if (centre == null) {
            centre = boss.position();
        }
        List<Ring> plan = BossSeismicPlan.rings(seismic.getCoreRadius(), seismic.getRingWidth(),
                seismic.getRingGap(), seismic.getMaxRadius());
        SERIES.add(new Series(level.dimension(), boss, look, centre,
                new BossSeismicSeries(plan, BossSeismicSeries.Rules.of(seismic), gameTime, level.getRandom())));
        return true;
    }

    /** The boss' feet on the floor, or null when there is none within the wave's reach. */
    private static Vec3 floorUnder(ServerLevel level, EntityNPCInterface boss, Look look) {
        BlockPos floor = BossFloorUtil.findFloor(level, boss.getX(), boss.getY(), boss.getZ(),
                look.wave.floorSearchDepth());
        return floor == null ? null : new Vec3(boss.getX(), floor.getY() + 1.0D, boss.getZ());
    }

    public static boolean hasPending() {
        return !SERIES.isEmpty() || !SLAMS.isEmpty() || !LANDINGS.isEmpty();
    }

    /** Whether this boss has a series running; what a second cast, a chain and a finish hold wait on. */
    public static boolean hasPending(EntityNPCInterface boss) {
        return !SERIES.isEmpty() && SERIES.find(series -> series.boss == boss) != null;
    }

    /** Whether this boss is to stand still right now: a series running, and the phase asked it to. */
    public static boolean isRooting(EntityNPCInterface boss) {
        if (SERIES.isEmpty()) {
            return false;
        }
        Series series = SERIES.find(candidate -> candidate.boss == boss);
        return series != null && series.look.rootWhileRunning;
    }

    /** The diagnostic command's line. */
    public static String status(EntityNPCInterface boss, long gameTime) {
        Series series = SERIES.isEmpty() ? null : SERIES.find(candidate -> candidate.boss == boss);
        return series == null ? "Seismic: idle" : "Seismic: " + series.clock.status(gameTime);
    }

    public static void tick(ServerLevel level) {
        long gameTime = level.getGameTime();
        SERIES.sweep(series -> series.dimension.equals(level.dimension()),
                series -> tickSeries(level, series, gameTime));
        SLAMS.sweep(slam -> slam.dimension.equals(level.dimension()),
                slam -> tickSlam(level, slam, gameTime));
        tickLandings(level, gameTime);
    }

    /** Drops everything still running in a level that is going away. */
    public static void clear(ServerLevel level) {
        SERIES.removeIf(series -> series.dimension.equals(level.dimension()));
        SLAMS.removeIf(slam -> slam.dimension.equals(level.dimension()));
        LANDINGS.values().removeIf(landing -> landing.dimension.equals(level.dimension()));
    }

    /**
     * Drops the series one boss cast, for its death, the end of its fight and a phase that is
     * over: the rings are the boss doing something, not a fault in the floor, and the arena owes
     * the party nothing more once the boss has stopped. The slams it still owed and the landings
     * it was waiting on go with it: whoever is in the air comes down as they would from any jump.
     */
    public static void clearBoss(EntityNPCInterface boss) {
        if (!SERIES.isEmpty()) {
            SERIES.removeIf(series -> series.boss == boss);
        }
        if (!SLAMS.isEmpty()) {
            SLAMS.removeIf(slam -> slam.boss == boss);
        }
        if (!LANDINGS.isEmpty()) {
            LANDINGS.values().removeIf(landing -> landing.boss == boss);
        }
    }

    /** @return whether this series is still running and belongs back in the queue */
    private static boolean tickSeries(ServerLevel level, Series series, long gameTime) {
        if (!series.boss.isAlive() || series.boss.isRemoved()) {
            return false;
        }
        series.clock.tick(gameTime, new WorldSink(level, series));
        paintWarnings(level, series, gameTime);
        return !series.clock.isOver();
    }

    /**
     * Outlines every ring that is waiting to hit: its inner and its outer edge, in the boss'
     * colour, the way the geyser's fuse is drawn - unconditionally, because a ring nobody can
     * see coming is not a mechanic but a trap.
     */
    private static void paintWarnings(ServerLevel level, Series series, long gameTime) {
        List<BossSeismicSeries.Waiting> waiting = series.clock.waiting();
        if (waiting.isEmpty() || gameTime % MARK_INTERVAL_TICKS != 0L) {
            return;
        }
        for (BossSeismicSeries.Waiting outlined : waiting) {
            Vec3 centre = (Vec3) outlined.anchor();
            if (level.getNearestPlayer(centre.x, centre.y, centre.z,
                    BossTelegraphUtil.audienceRange(series.boss), false) == null) {
                continue;
            }
            BossTelegraphPaint paint = BossTelegraphPaint.of(series.look.telegraph, series.boss,
                    BossTelegraphPaint.CHANNEL_SEISMIC, BossAbilityKind.SEISMIC, outlined.progress(gameTime));
            Ring ring = outlined.ring();
            BossTelegraphUtil.edgeRing(level, centre, ring.outer(), paint);
            // The core circle has no inner edge to read: a ring of no radius is a point.
            if (!ring.isCore()) {
                BossTelegraphUtil.edgeRing(level, centre, ring.inner(), paint);
            }
        }
    }

    /** The clock's hands in the world: where a pulse lands, what a hit does, and the swing on a repeat. */
    private static final class WorldSink implements BossSeismicSeries.Sink {
        private final ServerLevel level;
        private final Series series;

        private WorldSink(ServerLevel level, Series series) {
            this.level = level;
            this.series = series;
        }

        @Override
        public Object pulse(long gameTime, List<Ring> rings) {
            Vec3 centre = series.centre;
            if (series.look.followBoss) {
                // Under the boss as it stands now; over a hole there is no floor to pulse on.
                centre = floorUnder(level, series.boss, series.look);
                if (centre == null) {
                    return null;
                }
            }
            series.look.pulseSound.play(level, centre.x, centre.y, centre.z, SoundSource.HOSTILE);
            return centre;
        }

        @Override
        public void hit(long gameTime, BossSeismicSeries.Waiting waiting) {
            hitRing(level, series, (Vec3) waiting.anchor(), waiting.ring(), gameTime);
        }

        @Override
        public void animation(long gameTime) {
            TeleportPathController controller = controllerOf(series.boss);
            if (controller != null) {
                controller.playAnimation(series.look.animation);
            }
        }
    }

    /** One ring landing: its wave and its dots first, then whoever still stands in it. */
    private static void hitRing(ServerLevel level, Series series, Vec3 centre, Ring ring, long gameTime) {
        Look look = series.look;
        // Both started before the hits, so what a player sees leaves at the same moment the
        // damage lands rather than a tick behind it.
        BossAreaVfxScheduler.scheduleBand(level, centre, look.vfx, ring.inner(), ring.outer(),
                look.vfxTicks, look.blockWave, look.wave);
        dotRing(level, series, centre, ring);
        for (LivingEntity victim : victims(level, series, centre, ring)) {
            // The throw below is this ring's own knockback rather than something on top of it,
            // so it goes with the hit: a totem whose list this ring is not on has to be left
            // standing, not thrown while taking nothing.
            if (BossAbilityDamageUtil.passesBy(victim, BossAbilityKind.SEISMIC)) {
                continue;
            }
            BossAbilityDamageUtil.hit(victim, BossAbilityKind.SEISMIC, series.boss, look.damage, look.effects,
                    look.knockback, centre.x - victim.getX(), centre.z - victim.getZ());
            look.hitSound.play(level, victim.getX(), victim.getY(), victim.getZ(), SoundSource.HOSTILE);
            look.hitParticles.emitDust(level, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5D,
                    victim.getZ(), 0.3D, 0.4D, 0.3D, 0.05D, BossAbilityKind.SEISMIC);
            if (look.hitMode >= BossSeismicSettings.HIT_LAUNCH) {
                // The geyser's throw, so a ring and a column go up the same way for the same number.
                BossGeyserScheduler.launch(victim, look.launch);
                look.launchSound.play(level, victim.getX(), victim.getY(), victim.getZ(), SoundSource.HOSTILE);
            }
            if (look.hitMode == BossSeismicSettings.HIT_SLAM) {
                // One slam owed per victim: a second ring catching them on the way up moves the
                // yank rather than adding a second one on top of it.
                UUID victimId = victim.getUUID();
                SLAMS.removeIf(slam -> slam.victimId.equals(victimId));
                SLAMS.add(new Slam(level.dimension(), series.boss, victimId, look, gameTime + look.slamDelayTicks));
            }
        }
    }

    /** @return whether this slam is still owed and belongs back in the queue */
    private static boolean tickSlam(ServerLevel level, Slam slam, long gameTime) {
        if (gameTime < slam.at) {
            return true;
        }
        LivingEntity victim = level.getEntity(slam.victimId) instanceof LivingEntity found ? found : null;
        if (victim == null || !victim.isAlive() || victim.isRemoved() || !slam.boss.isAlive() || slam.boss.isRemoved()) {
            return false;
        }
        // Their own run is kept, so somebody thrown while sprinting comes down along the same arc.
        Vec3 movement = victim instanceof ServerPlayer player ? player.getKnownMovement() : victim.getDeltaMovement();
        victim.setDeltaMovement(BossSeismicPlan.slamVelocity(movement, slam.look.slamStrength,
                victim instanceof ServerPlayer));
        // Players simulate their own movement, so the server has to push the new velocity to
        // them explicitly. hurtMarked is what makes ServerEntity send it.
        victim.hurtMarked = true;
        slam.look.slamSound.play(level, victim.getX(), victim.getY(), victim.getZ(), SoundSource.HOSTILE);
        LANDINGS.put(slam.victimId, new Landing(level.dimension(), slam.boss, slam.look, gameTime));
        return false;
    }

    /**
     * Lands the slam's own hit, from the fall event, before vanilla works out the fall's.
     *
     * @param fallDistance     what the fall event says they fell
     * @param damageMultiplier the fall event's multiplier, for the same sum vanilla is about to do
     * @return whether vanilla's own fall damage is to be called off: the phase forgave it
     */
    public static boolean onFall(LivingEntity victim, float fallDistance, float damageMultiplier) {
        // The map is server state and the event fires on the client too; on an integrated
        // server that is the same static map, from another thread.
        if (victim.level().isClientSide || LANDINGS.isEmpty()) {
            return false;
        }
        Landing landing = LANDINGS.remove(victim.getUUID());
        if (landing == null) {
            return false;
        }
        boolean fallHurts = landing.look.slamFallDamage && fallHurts(victim, fallDistance, damageMultiplier);
        land(victim, landing, fallHurts);
        return !landing.look.slamFallDamage;
    }

    /** Whether vanilla is about to hurt this fall at all: the sum {@code calculateFallDamage} does, without the rounding. */
    private static boolean fallHurts(LivingEntity victim, float fallDistance, float damageMultiplier) {
        if (victim.getType().is(EntityTypeTags.FALL_DAMAGE_IMMUNE)) {
            return false;
        }
        double past = fallDistance - victim.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
        return past * damageMultiplier * victim.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER) > 0.0D;
    }

    /**
     * The landing itself: the slam's hit, and the fall's own on top of it when the phase keeps
     * that - the gravity throw's trick, since two hits on one tick are one to vanilla until the
     * frames the first opened are closed again.
     */
    private static void land(LivingEntity victim, Landing landing, boolean fallHurts) {
        EntityNPCInterface boss = landing.boss;
        if (!boss.isAlive() || boss.isRemoved() || !(victim.level() instanceof ServerLevel level)) {
            return;
        }
        // No knockback and no potions: the potions went on with the ring, and a landing is a
        // thud, not a shove.
        boolean landed = BossAbilityDamageUtil.hit(victim, BossAbilityKind.SEISMIC, boss, landing.look.slamDamage,
                null, 0, 0.0D, 0.0D);
        if (landed && fallHurts) {
            victim.invulnerableTime = 0;
        }
        landing.look.slamParticles.emitDust(level, victim.getX(), victim.getY() + 0.2D, victim.getZ(),
                0.5D, 0.1D, 0.5D, 0.0D, BossAbilityKind.SEISMIC);
    }

    /**
     * Landings the fall event never reports: a splash, or an npc set to take no fall damage,
     * which never reaches the event at all. Watched for from the tick instead - down again
     * after having been seen off the floor - and forgotten after the phase's wait.
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
            if (victim == null || !victim.isAlive() || victim.isRemoved() || !landing.boss.isAlive()
                    || landing.boss.isRemoved()
                    || gameTime - landing.thrownAt > landing.look.slamTimeoutTicks) {
                LANDINGS.remove(entry.getKey(), landing);
                continue;
            }
            boolean down = victim.onGround() || victim.isInWater() || victim.isInLava();
            if (!down) {
                landing.airborne = true;
            } else if (landing.airborne) {
                LANDINGS.remove(entry.getKey(), landing);
                // Nothing for the frames trick to do: vanilla is not hurting this one.
                land(victim, landing, false);
            }
        }
    }

    /**
     * The pulse cue round the ring as it hits: its count spent one dot at a time along the
     * middle of the ring, so a builder's number reads as "this many dots round the circle".
     */
    private static void dotRing(ServerLevel level, Series series, Vec3 centre, Ring ring) {
        BossParticleCue cue = series.look.pulseParticles;
        int count = Math.min(cue.getCount(), MAX_RING_PARTICLES);
        ParticleOptions options = count > 0 && cue.isEnabled() ? cue.resolve(BossAbilityKind.SEISMIC) : null;
        if (options == null || level.getNearestPlayer(centre.x, centre.y, centre.z,
                BossTelegraphUtil.audienceRange(series.boss), false) == null) {
            return;
        }
        double radius = ring.middle();
        for (int i = 0; i < count; i++) {
            double angle = i * Mth.TWO_PI / count;
            double x = centre.x + Math.cos(angle) * radius;
            double z = centre.z + Math.sin(angle) * radius;
            BlockPos floor = BossFloorUtil.findFloor(level, x, centre.y, z, series.look.wave.floorSearchDepth());
            if (floor != null) {
                level.sendParticles(options, x, floor.getY() + 1.1D, z, 1, 0.1D, 0.1D, 0.1D, 0.02D);
            }
        }
    }

    /**
     * Everyone this ring catches.
     *
     * <p>Asked of the boss that cast the series rather than worked out here, so a ring and a
     * gravity field can never end up with different ideas of who counts as an enemy.</p>
     */
    private static List<LivingEntity> victims(ServerLevel level, Series series, Vec3 centre, Ring ring) {
        TeleportPathController controller = controllerOf(series.boss);
        return controller == null ? List.of()
                : controller.seismicVictims(level, centre, ring.inner(), ring.outer(), series.look.height);
    }

    private static TeleportPathController controllerOf(EntityNPCInterface boss) {
        return boss instanceof IBossController holder ? holder.cnpcgeckoaddon$getTeleportPathController() : null;
    }
}

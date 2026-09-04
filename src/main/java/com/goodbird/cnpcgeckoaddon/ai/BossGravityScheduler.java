package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossEffectSet;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.mixin.IBossController;
import com.goodbird.cnpcgeckoaddon.utils.TickQueue;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Holds a boss' gravity field: for a few seconds everyone around it is dragged in or shoved
 * out, or was thrown up once and is still owed the landing.
 *
 * <p>The pull is a tug of war. Every tick each victim on the ground is handed the speed
 * their own client was about to work out anyway, plus a steady push toward the boss - the
 * same arithmetic {@link BossTetherManager} pulls with, and pitched the same way: a walker
 * is held, a sprinter gets away, anyone standing still is reeled in and bitten. The push is
 * the same force turned round. Neither reaches anyone in the air, because the server's idea
 * of a jumping player's height runs a tick or two behind, and sending it back would cut the
 * jump short; the price is that a hop buys a moment of freedom, exactly as it does off a
 * leash.</p>
 *
 * <p>The field is centred on the boss on every tick, not on the spot it was cast from, so a
 * boss that walks drags the field along with it. Everything else - radius, force, damage with
 * the enrage bonus, effects - is snapshotted on the cast, the way a geyser's eruption is, and
 * driven from the level tick: the boss is back on its rotation the moment the cast lands.</p>
 *
 * <p>A throw is over the tick it happens, but its landing is not: whoever was thrown is
 * remembered by id, and the extra damage lands the moment they come down - before vanilla's
 * own fall damage, from the fall event - and on nobody who merely walked in afterwards.</p>
 *
 * <p>Nothing here is persisted. A field lives for seconds, and a server that shuts down
 * inside that window should not drag whoever logs in first toward a boss that is no longer
 * fighting anybody.</p>
 */
public final class BossGravityScheduler {

    /** Fields one level tick works on. One per boss is all a fight has, so this is a runaway stop. */
    private static final int MAX_PER_TICK = 16;
    /** Beyond this nobody can see the ring, so the field works without costing anything. */
    private static final double AUDIENCE_RANGE = 64.0D;
    /** How often the ring is repainted. Every other tick reads as a steady shape. */
    private static final int MARK_INTERVAL_TICKS = 2;
    /** Ticks between one dose of the held effects and the next. */
    private static final int EFFECT_INTERVAL_TICKS = 20;
    /** The least a victim held against the boss gets between one bite and the next. */
    private static final int BITE_INTERVAL_TICKS = 20;
    /** How long the opening wave runs for; the field has no length setting of its own. */
    private static final int VFX_DURATION_TICKS = 20;
    /**
     * What an entity keeps of its last tick's movement on plain ground: block friction times
     * the air drag every entity gets.
     *
     * <p>Two things are measured in it. A player's own client applies it to the step it last
     * reported before putting its input on top, which is the speed the force has to sit on;
     * and the server applies it once more to a player between this scheduler and the tracker
     * that sends the speed on, so what is set for a player is the step as reported plus the
     * force over this drag - worn once by the server, that arrives as exactly the sum the
     * client would have made itself, plus the force. A mob has had its own tick, drag and
     * all, before this runs, and moves on the next by exactly what it holds now.</p>
     */
    private static final double CLIENT_GROUND_DRAG = 0.6D * 0.91D;
    /** What every tick takes off a vertical speed, and what it takes off before that: vanilla's own two numbers. */
    private static final double VERTICAL_DRAG = 0.98D;
    private static final double GRAVITY = 0.08D;
    /** Inside this the pull lets go, or a victim already at the boss would twitch about it. */
    private static final double PULL_SLACK = 1.0D;
    /**
     * How long a thrown victim is waited for. A throw at full strength is down again inside
     * a hundred ticks; one that never comes down - flown off, teleported away - is forgotten
     * rather than bitten a minute later on some unrelated landing.
     */
    private static final int LANDING_TIMEOUT_TICKS = 400;
    /** Motes drifting with the field each tick, so it reads as a wind and not only as a ring. */
    private static final int STREAM_PARTICLES_PER_TICK = 3;
    /** The motes start no nearer the boss than this share of the radius, or they say nothing. */
    private static final double STREAM_INNER_SHARE = 0.35D;
    private static final double STREAM_HEIGHT = 2.0D;

    /** One field, pulling or pushing. */
    private static final class Field {
        private final ResourceKey<Level> dimension;
        private final EntityNPCInterface boss;
        private final int mode;
        private final double radius;
        /** Blocks per tick added every tick, sign already in the mode. */
        private final double force;
        private final double touchRadius;
        /** What the bite hits for, enrage already counted in. */
        private final int damage;
        private final BossEffectSet effects;
        private final long startedAt;
        private final long endsAt;
        /** Victim id -> earliest game time the field may bite them again. */
        private final Map<UUID, Long> nextBiteAt = new HashMap<>();

        private Field(ResourceKey<Level> dimension, EntityNPCInterface boss, BossPhaseData phase,
                      int damage, long gameTime) {
            this.dimension = dimension;
            this.boss = boss;
            this.mode = phase.getGravityMode();
            this.radius = phase.getGravityRadius();
            this.force = phase.getGravityStrength() / 100.0D;
            this.touchRadius = phase.getGravityTouchRadius();
            this.damage = damage;
            this.effects = phase.getGravityEffects();
            this.startedAt = gameTime;
            this.endsAt = gameTime + phase.getGravityDurationTicks();
        }
    }

    /** One thrown victim, still in the air as far as this knows. */
    private static final class Landing {
        private final ResourceKey<Level> dimension;
        private final EntityNPCInterface boss;
        /** What the landing hits for, enrage already counted in. */
        private final int damage;
        private final long thrownAt;
        /** Set once the server has seen them off the floor, so the throw tick itself is not a landing. */
        private boolean airborne;

        private Landing(ResourceKey<Level> dimension, EntityNPCInterface boss, int damage, long thrownAt) {
            this.dimension = dimension;
            this.boss = boss;
            this.damage = damage;
            this.thrownAt = thrownAt;
        }
    }

    private static final TickQueue<Field> FIELDS = new TickQueue<>("boss gravity fields", MAX_PER_TICK);
    private static final Map<UUID, Landing> LANDINGS = new HashMap<>();
    /**
     * The force owed to each victim this tick, summed over every field they stand in before
     * it is applied. Applied one field at a time, the second would overwrite the first, and
     * two bosses pulling the same player would only ever pull as one.
     */
    private static final Map<LivingEntity, Vec3> FORCES = new LinkedHashMap<>();

    private BossGravityScheduler() {
    }

    /**
     * Opens the field, or throws everyone in it.
     *
     * @param damage what the pull's bite or the throw's landing hits for, with the enrage
     *               bonus already in it
     */
    public static void start(ServerLevel level, EntityNPCInterface boss, BossPhaseData phase, int damage,
                             long gameTime) {
        Vec3 centre = boss.position();
        // Purely for show, and started before anything is moved, so what a player sees leaves
        // at the same moment the force lands rather than a tick behind it.
        BossAreaVfxScheduler.schedule(level, centre, phase.getGravityVfx(), phase.getGravityRadius(),
                VFX_DURATION_TICKS, false);
        if (phase.getGravityMode() == BossPhaseData.GRAVITY_MODE_LIFT) {
            fling(level, boss, phase, damage, gameTime);
            return;
        }
        FIELDS.add(new Field(level.dimension(), boss, phase, damage, gameTime));
        if (phase.getGravityMode() == BossPhaseData.GRAVITY_MODE_PULL) {
            // A low hum as the field opens; the wind that follows is the particles' job.
            level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.BEACON_ACTIVATE,
                    SoundSource.HOSTILE, 1.5F, 0.5F);
        } else {
            level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.WIND_CHARGE_BURST,
                    SoundSource.HOSTILE, 1.5F, 0.7F);
        }
    }

    /**
     * One throw straight up for everyone in the radius, and a landing owed to each of them.
     *
     * <p>Whoever is thrown is written down here and nowhere else: the landing damage goes to
     * exactly these people, not to whoever happens to fall over in the arena afterwards.</p>
     */
    private static void fling(ServerLevel level, EntityNPCInterface boss, BossPhaseData phase, int damage,
                              long gameTime) {
        double up = phase.getGravityStrength() / 10.0D;
        for (LivingEntity victim : victims(level, boss, boss.position(), phase.getGravityRadius())) {
            if (skips(victim)) {
                continue;
            }
            // Their own run is kept, so somebody sprinting through the field flies on in an
            // arc rather than stopping dead in the air above where they were.
            victim.setDeltaMovement(launch(victim, up));
            // Wipes the fall they were already in, so the landing is measured from the top of
            // this throw and the ride up cannot be what kills them.
            victim.fallDistance = 0.0F;
            // Players simulate their own movement, so the server has to push the new velocity
            // to them explicitly. hurtMarked is what makes ServerEntity send it.
            victim.hurtMarked = true;
            if (phase.getGravityEffects().isAnyEnabled()) {
                BossAbilityDamageUtil.applyEffects(victim, BossAbilityKind.GRAVITY, boss,
                        phase.getGravityEffects());
            }
            LANDINGS.put(victim.getUUID(), new Landing(level.dimension(), boss, damage, gameTime));
            level.sendParticles(ParticleTypes.CLOUD, victim.getX(), victim.getY() + 0.2D, victim.getZ(),
                    10, 0.3D, 0.1D, 0.3D, 0.12D);
            level.sendParticles(BossTelegraphUtil.dust(BossAbilityKind.GRAVITY), victim.getX(),
                    victim.getY() + victim.getBbHeight() * 0.5D, victim.getZ(), 8, 0.3D, 0.5D, 0.3D, 0.0D);
        }
        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.WIND_CHARGE_BURST,
                SoundSource.HOSTILE, 2.0F, 0.5F);
    }

    public static boolean hasPending() {
        return !FIELDS.isEmpty() || !LANDINGS.isEmpty();
    }

    /** Ticks the field one boss has open has left, or 0 when it has none. */
    public static long remainingTicks(EntityNPCInterface boss, long gameTime) {
        Field field = FIELDS.find(candidate -> candidate.boss == boss);
        return field == null ? 0L : Math.max(0L, field.endsAt - gameTime);
    }

    public static void tick(ServerLevel level) {
        long gameTime = level.getGameTime();
        FORCES.clear();
        FIELDS.sweep(field -> field.dimension.equals(level.dimension()),
                field -> tickField(level, field, gameTime));
        applyForces();
        FORCES.clear();
        tickLandings(level, gameTime);
    }

    /** Drops anything still open in a level that is going away. */
    public static void clear(ServerLevel level) {
        FIELDS.removeIf(field -> field.dimension.equals(level.dimension()));
        LANDINGS.values().removeIf(landing -> landing.dimension.equals(level.dimension()));
    }

    /**
     * Closes the field one boss has open and forgives the landings it is owed, for its death
     * and for the end of its fight.
     *
     * <p>A field is the boss doing something, not weather left over the arena: killing it
     * mid-pull is a win, and the arena owes the party nothing more - not even the landing.</p>
     */
    public static void clearBoss(EntityNPCInterface boss) {
        if (!FIELDS.isEmpty()) {
            FIELDS.removeIf(field -> field.boss == boss);
        }
        if (!LANDINGS.isEmpty()) {
            LANDINGS.values().removeIf(landing -> landing.boss == boss);
        }
    }

    /** @return whether this field is still open and belongs back in the queue */
    private static boolean tickField(ServerLevel level, Field field, long gameTime) {
        EntityNPCInterface boss = field.boss;
        if (!boss.isAlive() || boss.isRemoved() || boss.level() != level || gameTime >= field.endsAt) {
            return false;
        }
        // Read fresh every tick: the field is wherever the boss is now, not where it cast.
        Vec3 centre = boss.position();
        boolean dose = (gameTime - field.startedAt) % EFFECT_INTERVAL_TICKS == 0L && field.effects.isAnyEnabled();
        for (LivingEntity victim : victims(level, boss, centre, field.radius)) {
            // The force is the field, so it asks for itself: somebody whose totem list or
            // immunity changed under it must not keep being dragged.
            if (skips(victim)) {
                continue;
            }
            if (dose) {
                BossAbilityDamageUtil.applyEffects(victim, BossAbilityKind.GRAVITY, boss, field.effects);
            }
            Vec3 force = forceOn(field, centre, victim);
            if (force != null) {
                FORCES.merge(victim, force, Vec3::add);
            }
            if (field.mode == BossPhaseData.GRAVITY_MODE_PULL) {
                bite(field, boss, victim, gameTime);
            }
        }
        paint(level, field, centre, gameTime);
        return true;
    }

    /** Everyone the field simply does not reach, immune or held still by something else. */
    private static boolean skips(LivingEntity victim) {
        // A captured victim is pinned every tick by the capture, and a force under a pin
        // only tells the client one thing and the server another.
        return BossAbilityDamageUtil.passesBy(victim, BossAbilityKind.GRAVITY)
                || BossCaptureManager.isCaptured(victim.getUUID());
    }

    /**
     * The push this field owes one victim on this tick, or null for none.
     *
     * <p>Flat, and only while they stand on the ground: the height they are already moving
     * at is left alone, for the reason the class comment gives.</p>
     */
    private static Vec3 forceOn(Field field, Vec3 centre, LivingEntity victim) {
        if (!victim.onGround()) {
            return null;
        }
        Vec3 delta = centre.subtract(victim.position());
        Vec3 flat = new Vec3(delta.x, 0.0D, delta.z);
        double distance = flat.length();
        if (field.mode == BossPhaseData.GRAVITY_MODE_PULL) {
            return distance <= PULL_SLACK ? null : flat.scale(field.force / distance);
        }
        // Somebody standing exactly on the boss has no way out to be shoved along.
        return distance < 1.0E-4D ? null : flat.scale(-field.force / distance);
    }

    /**
     * Sends every victim the tick's summed force.
     *
     * <p>A player's own client is what moves them, and each tick it starts from the speed
     * the server last sent and puts its own input on top. So what is sent is the movement the
     * client itself reported, worn down by the ground drag it would have applied anyway, plus
     * the force: with no force that reproduces plain walking, and with one it reads as a
     * steady push the victim has to out-run. A mob has already been worn down by its own
     * tick, so its speed is taken as it is.</p>
     */
    private static void applyForces() {
        for (Map.Entry<LivingEntity, Vec3> entry : FORCES.entrySet()) {
            LivingEntity victim = entry.getKey();
            // Somebody else already gave them a speed this tick - a hit's shove, a hook, a
            // geyser - and the tracker is about to send it. That one wins: the field is the
            // steady push underneath everything, not a thing that cancels a yank. This is
            // also what lets a bite's own knockback through, so the pull can take them back.
            if (victim.hurtMarked) {
                continue;
            }
            Vec3 force = entry.getValue();
            Vec3 velocity;
            if (victim instanceof ServerPlayer player) {
                // The step as the client reported it, plus the force over the drag the server
                // is about to apply to both; see CLIENT_GROUND_DRAG for why that is the sum
                // the client ends up with.
                Vec3 step = player.getKnownMovement();
                velocity = new Vec3(step.x + force.x / CLIENT_GROUND_DRAG, victim.getDeltaMovement().y,
                        step.z + force.z / CLIENT_GROUND_DRAG);
            } else {
                Vec3 own = victim.getDeltaMovement();
                velocity = new Vec3(own.x + force.x, own.y, own.z + force.z);
            }
            victim.setDeltaMovement(velocity);
            // Players simulate their own movement, so the server has to push the new velocity
            // to them explicitly. hurtMarked is what makes ServerEntity send it.
            victim.hurtMarked = true;
        }
    }

    /**
     * The throw as it has to be set so that it arrives as {@code up} straight up, on top of
     * whatever run the victim had.
     *
     * <p>For a player the server's own pass runs before the send here too, and takes one
     * tick of gravity off the height, so that tick is put back in advance. A mob moves by
     * exactly what it is handed.</p>
     */
    private static Vec3 launch(LivingEntity victim, double up) {
        if (victim instanceof ServerPlayer player) {
            Vec3 step = player.getKnownMovement();
            return new Vec3(step.x, up / VERTICAL_DRAG + GRAVITY, step.z);
        }
        Vec3 own = victim.getDeltaMovement();
        return new Vec3(own.x, up, own.z);
    }

    /**
     * The pull's teeth: whoever is up against the boss is hit, and then not again for a
     * second however long they stay there.
     *
     * <p>Measured box to box rather than centre to centre, so a boss four blocks wide can
     * still be touched - centre to centre nobody would ever get near enough. Retried every
     * tick until a hit really lands, so a bite swallowed by somebody's invulnerability frames
     * is not a whole second forgiven.</p>
     */
    private static void bite(Field field, EntityNPCInterface boss, LivingEntity victim, long gameTime) {
        if (!boss.getBoundingBox().inflate(field.touchRadius).intersects(victim.getBoundingBox())) {
            return;
        }
        Long due = field.nextBiteAt.get(victim.getUUID());
        if (due != null && gameTime < due) {
            return;
        }
        // No knockback asked for: vanilla already shoves a hit victim off its attacker a
        // little, and the pull takes them straight back - which is the chewing.
        if (BossAbilityDamageUtil.hit(victim, BossAbilityKind.GRAVITY, boss, field.damage, null, 0, 0.0D, 0.0D)) {
            field.nextBiteAt.put(victim.getUUID(), gameTime + BITE_INTERVAL_TICKS);
        }
    }

    /** The ring at the field's edge, and the motes streaming through it the way it pulls. */
    private static void paint(ServerLevel level, Field field, Vec3 centre, long gameTime) {
        if (level.getNearestPlayer(centre.x, centre.y, centre.z, AUDIENCE_RANGE, false) == null) {
            return;
        }
        if (gameTime % MARK_INTERVAL_TICKS == 0L) {
            BossTelegraphUtil.ring(level, centre, field.radius, BossTelegraphUtil.dust(BossAbilityKind.GRAVITY));
        }
        RandomSource random = level.getRandom();
        boolean pull = field.mode == BossPhaseData.GRAVITY_MODE_PULL;
        for (int i = 0; i < STREAM_PARTICLES_PER_TICK; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = field.radius * (STREAM_INNER_SHARE + random.nextDouble() * (1.0D - STREAM_INNER_SHARE));
            double height = random.nextDouble() * STREAM_HEIGHT;
            double dx = Math.cos(angle) * distance;
            double dz = Math.sin(angle) * distance;
            // With a count of zero the offsets are a velocity, and for these two particles the
            // velocity is the whole trip: a portal mote appears at the far end of it and drifts
            // home to where it was spawned, its reverse leaves home and drifts out to the end.
            // So both are spawned on the boss, and the pull's motes come in while the push's go out.
            level.sendParticles(pull ? ParticleTypes.PORTAL : ParticleTypes.REVERSE_PORTAL,
                    centre.x, centre.y + height, centre.z, 0, dx, 0.0D, dz, 1.0D);
        }
    }

    /**
     * Lands the throw's own hit, from the fall event, before vanilla works out the fall's.
     *
     * @param fallDistance     what the fall event says they fell
     * @param damageMultiplier the fall event's multiplier, for the same sum vanilla is about to do
     */
    public static void onFall(LivingEntity victim, float fallDistance, float damageMultiplier) {
        // The map is server state and the event fires on the client too; on an integrated
        // server that is the same static map, from another thread.
        if (victim.level().isClientSide || LANDINGS.isEmpty()) {
            return;
        }
        Landing landing = LANDINGS.remove(victim.getUUID());
        if (landing == null) {
            return;
        }
        land(victim, landing, fallHurts(victim, fallDistance, damageMultiplier));
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
     * The landing itself.
     *
     * <p>Two hits on one tick are not two hits to vanilla: the second only lands what it has
     * over the first, for as long as the invulnerability frames the first opened last. So
     * when the fall is about to hurt as well, the frames this hit opened are closed again
     * behind it, and the fall lands whole on top - which is what "on top of the fall damage"
     * has to mean. A fall that would not have hurt leaves them alone, so a short throw is one
     * hit with the usual grace after it.</p>
     */
    private static void land(LivingEntity victim, Landing landing, boolean fallHurts) {
        EntityNPCInterface boss = landing.boss;
        if (!boss.isAlive() || boss.isRemoved() || !(victim.level() instanceof ServerLevel level)) {
            return;
        }
        // No knockback and no effects: the effects went on with the throw, and a landing is
        // a thud, not a shove.
        boolean landed = BossAbilityDamageUtil.hit(victim, BossAbilityKind.GRAVITY, boss, landing.damage,
                null, 0, 0.0D, 0.0D);
        if (landed && fallHurts) {
            victim.invulnerableTime = 0;
        }
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.GENERIC_BIG_FALL,
                SoundSource.HOSTILE, 1.0F, 0.8F);
        level.sendParticles(BossTelegraphUtil.dust(BossAbilityKind.GRAVITY), victim.getX(),
                victim.getY() + 0.2D, victim.getZ(), 10, 0.5D, 0.1D, 0.5D, 0.0D);
    }

    /**
     * Landings the fall event never reports: a splash, or an npc set to take no fall damage,
     * which never reaches the event at all. Watched for from the tick instead - down again
     * after having been seen off the floor - and forgiven after long enough.
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
                    || landing.boss.isRemoved() || gameTime - landing.thrownAt > LANDING_TIMEOUT_TICKS) {
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
     * Everyone this field may catch around {@code centre}.
     *
     * <p>Asked of the boss that opened it rather than worked out here, so a gravity field and
     * an area slam can never end up with different ideas of who counts as an enemy.</p>
     */
    private static List<LivingEntity> victims(ServerLevel level, EntityNPCInterface boss, Vec3 centre,
                                              double radius) {
        TeleportPathController controller = boss instanceof IBossController holder
                ? holder.cnpcgeckoaddon$getTeleportPathController() : null;
        return controller == null ? List.of() : controller.gravityVictims(level, centre, radius);
    }
}

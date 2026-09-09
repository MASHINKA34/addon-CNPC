package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BeamLooks;
import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossEffectSet;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.mixin.IBossController;
import com.goodbird.cnpcgeckoaddon.utils.TickQueue;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Holds a boss' sweeping beam: for a while after the cast one or more lines turn round it,
 * and whoever they catch up with is burned.
 *
 * <p>The whole mechanic is the turn. A beam comes round at a walking pace or slower, so a
 * player who keeps stepping the way it is going is never in it and one who stops is; and
 * because a beam ends at the first solid block on its line, a pillar is somewhere to stand.
 * Both are why the beams are drawn on every tick and whatever the warning settings say: a
 * beam nobody can see is not a mechanic, it is a trap.</p>
 *
 * <p>Everything the sweep needs is snapshotted on the cast - the enrage bonus included - and
 * driven from the level tick, the way a gravity field is: the boss is back on its rotation
 * the moment the cast lands. Only the centre is read fresh, and only when the beams are set
 * to follow the boss.</p>
 *
 * <p>Nothing here is persisted. A sweep lives for seconds, and a server that shuts down
 * inside that window should not burn whoever logs in first.</p>
 */
public final class BossBeamScheduler {

    /** Sweeps one level tick works on. One per boss is all a fight has, so this is a runaway stop. */
    private static final int MAX_PER_TICK = 16;
    /** Beyond this nobody can see a beam, so it turns without costing anything. */
    /**
     * How high above the boss' feet the beams leave. Its waist, for a boss of about a
     * player's size; a giant's waist would pass clean over everyone's head, so the beams
     * are never raised above a block.
     */
    private static final double MAX_HEIGHT = 1.0D;
    private static final double TICKS_PER_SECOND = 20.0D;
    /**
     * How far past a beam's length the sweep looks for victims. The reach is measured from
     * the centre to their feet, and a beam still covers somebody standing on its tip whose
     * feet are a little below it.
     */
    private static final double VICTIM_SEARCH_SLACK = 2.0D;
    /** Sparks where a beam meets a wall, so the cut reads as the wall's doing. */
    private static final int WALL_SPARKS = 2;
    /**
     * One point in this many carries a look's second particle instead of its first - the
     * smoke in a fire beam, the wisp in a soul one. Enough to be noticed, too few to thin
     * the line they sit in.
     */
    private static final int ACCENT_ONE_IN = 6;
    /** Rarer still for the void: its swirl is the odd fleck in a line of rods, not a second colour. */
    private static final int RARE_ACCENT_ONE_IN = 12;
    /** The dust under the lightning's sparks, the frost's snow and the toxic beam's slime. */
    private static final DustParticleOptions LIGHTNING_DUST = BossTelegraphUtil.dustOf(0xFFFFFF);
    private static final DustParticleOptions FROST_DUST = BossTelegraphUtil.dustOf(0xBFE8FF);
    private static final DustParticleOptions TOXIC_DUST = BossTelegraphUtil.dustOf(0xAAFF33);

    /** One sweep, mid turn. */
    private static final class Sweep {
        private final ResourceKey<Level> dimension;
        private final EntityNPCInterface boss;
        private final int count;
        private final double length;
        /** Minecraft yaw of the first beam on the tick the sweep began, in degrees. */
        private final float startYaw;
        /** Degrees turned per tick, sign included. */
        private final double degreesPerTick;
        private final boolean followsBoss;
        private final boolean stopsAtWalls;
        /** Half the beam's width: how far off its line it still catches somebody. */
        private final double halfWidth;
        /** What one hit does, enrage already counted in. */
        private final int damage;
        private final int knockback;
        private final int hitIntervalTicks;
        private final BossEffectSet effects;
        /** What the beams are drawn out of, by {@link BeamLooks} id. */
        private final String look;
        private final long startedAt;
        private final long endsAt;
        /** Where the beams turn round: the boss, tick by tick, or the spot it cast them from. */
        private Vec3 centre;
        /** Victim id -> earliest game time a beam may hit them again. */
        private final Map<UUID, Long> nextHitAt = new HashMap<>();

        private Sweep(ResourceKey<Level> dimension, EntityNPCInterface boss, BossPhaseData phase,
                      float startYaw, int damage, int knockback, long gameTime) {
            this.dimension = dimension;
            this.boss = boss;
            this.count = phase.beam().getCount();
            this.length = phase.beam().getLength();
            this.startYaw = startYaw;
            this.degreesPerTick = phase.beam().getDegreesPerSecond() / TICKS_PER_SECOND;
            this.followsBoss = phase.beam().isFollowsBoss();
            this.stopsAtWalls = phase.beam().isStopsAtWalls();
            this.halfWidth = phase.beam().getWidth() * 0.5D;
            this.damage = damage;
            this.knockback = knockback;
            this.hitIntervalTicks = phase.beam().getHitIntervalTicks();
            this.effects = phase.beam().getEffects();
            this.look = phase.beam().getLook();
            this.startedAt = gameTime;
            this.endsAt = gameTime + phase.beam().getDurationTicks();
            this.centre = centreOf(boss);
        }
    }

    private static final TickQueue<Sweep> SWEEPS = new TickQueue<>("boss beam sweeps", MAX_PER_TICK);

    private BossBeamScheduler() {
    }

    /**
     * Switches the beams on round the boss.
     *
     * @param startYaw  the Minecraft yaw the first beam leaves at; the rest are spaced evenly after it
     * @param damage    what one hit does, with the enrage bonus already in it
     * @param knockback how hard a hit shoves, in the same already-scaled terms
     */
    public static void start(ServerLevel level, EntityNPCInterface boss, BossPhaseData phase, float startYaw,
                             int damage, int knockback, long gameTime) {
        SWEEPS.add(new Sweep(level.dimension(), boss, phase, startYaw, damage, knockback, gameTime));
        Vec3 centre = centreOf(boss);
        // The guardian's own zap, deeper: the one sound in the game that already means a beam.
        level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.GUARDIAN_ATTACK,
                SoundSource.HOSTILE, 2.0F, 0.6F);
    }

    public static boolean hasPending() {
        return !SWEEPS.isEmpty();
    }

    /** Whether one boss has beams turning right now. */
    public static boolean isSweeping(EntityNPCInterface boss) {
        return SWEEPS.find(sweep -> sweep.boss == boss) != null;
    }

    /** Ticks the sweep one boss has running has left, or 0 when it has none. */
    public static long remainingTicks(EntityNPCInterface boss, long gameTime) {
        Sweep sweep = SWEEPS.find(candidate -> candidate.boss == boss);
        return sweep == null ? 0L : Math.max(0L, sweep.endsAt - gameTime);
    }

    public static void tick(ServerLevel level) {
        long gameTime = level.getGameTime();
        SWEEPS.sweep(sweep -> sweep.dimension.equals(level.dimension()),
                sweep -> tickSweep(level, sweep, gameTime));
    }

    /** Drops anything still turning in a level that is going away. */
    public static void clear(ServerLevel level) {
        SWEEPS.removeIf(sweep -> sweep.dimension.equals(level.dimension()));
    }

    /**
     * Switches the beams one boss has running off, for its death, the end of its fight and
     * a change of phase.
     *
     * <p>A beam is the boss doing something, not a fixture of the arena: killing it mid
     * sweep is a win, and the arena owes the party nothing more.</p>
     */
    public static void clearBoss(EntityNPCInterface boss) {
        if (SWEEPS.isEmpty()) {
            return;
        }
        SWEEPS.removeIf(sweep -> sweep.boss == boss);
    }

    /** @return whether this sweep is still turning and belongs back in the queue */
    private static boolean tickSweep(ServerLevel level, Sweep sweep, long gameTime) {
        EntityNPCInterface boss = sweep.boss;
        if (!boss.isAlive() || boss.isRemoved() || boss.level() != level || gameTime >= sweep.endsAt) {
            return false;
        }
        if (sweep.followsBoss) {
            // Read fresh every tick: the beams turn round wherever the boss is now.
            sweep.centre = centreOf(boss);
        }
        Vec3[] ends = new Vec3[sweep.count];
        for (int i = 0; i < sweep.count; i++) {
            ends[i] = reach(level, boss, sweep.centre, direction(beamYaw(sweep, i, gameTime)),
                    sweep.length, sweep.stopsAtWalls);
        }
        burn(level, sweep, ends, gameTime);
        paint(level, sweep.centre, ends, sweep.length, sweep.look);
        return true;
    }

    /**
     * Lands the beams on whoever is standing in one, once per victim per tick and no
     * oftener than the interval.
     *
     * <p>The interval only starts once a hit really lands, the way the gravity field's bite
     * counts: a hit swallowed by somebody's invulnerability frames is retried next tick
     * rather than forgiven for the whole interval. A beam that does no damage lands its
     * effects on the interval instead, since there is nothing else to wait for.</p>
     */
    private static void burn(ServerLevel level, Sweep sweep, Vec3[] ends, long gameTime) {
        EntityNPCInterface boss = sweep.boss;
        for (LivingEntity victim : victims(level, boss, sweep.centre, sweep.length + VICTIM_SEARCH_SLACK)) {
            // The beam is what hits, so it asks for itself: somebody whose totem list or
            // immunity changed under it must not keep being burned.
            if (BossAbilityDamageUtil.passesBy(victim, BossAbilityKind.BEAM)) {
                continue;
            }
            Long due = sweep.nextHitAt.get(victim.getUUID());
            if (due != null && gameTime < due) {
                continue;
            }
            for (int i = 0; i < ends.length; i++) {
                Vec3 off = offLine(sweep.centre, ends[i], sweep.halfWidth, victim);
                if (off == null) {
                    continue;
                }
                // Somebody standing dead on the line is shoved ahead of the beam, which is
                // the one way off it that is also the way it is going.
                if (off.lengthSqr() < 1.0E-6D) {
                    Vec3 along = ends[i].subtract(sweep.centre);
                    double turn = sweep.degreesPerTick < 0.0D ? -1.0D : 1.0D;
                    off = new Vec3(along.z * turn, 0.0D, -along.x * turn);
                }
                // Vanilla shoves against the vector it is handed, which is why the offset
                // goes in negated: the victim is pushed off the beam's line, not onto it.
                boolean landed = BossAbilityDamageUtil.hit(victim, BossAbilityKind.BEAM, boss, sweep.damage,
                        sweep.effects, sweep.knockback, -off.x, -off.z);
                if (landed || sweep.damage <= 0) {
                    sweep.nextHitAt.put(victim.getUUID(), gameTime + sweep.hitIntervalTicks);
                }
                // One hit a tick however many beams cross them: the second beam of a cross is
                // the same sweep, not a second one.
                break;
            }
        }
    }

    /**
     * Whether a beam from {@code from} to {@code to} catches this victim, and if so where
     * they stand off its line, flat: null for a miss.
     *
     * <p>The victim is the line from their feet to their eyes, and the beam catches them
     * when the shortest distance between that line and the beam is inside the half width.
     * The beam is level and the victim upright, so that distance is the flat distance to
     * the beam's segment and the height gap between the beam and the victim's body, squared
     * and summed. Which means a ledge more than about a block up or down is out of a narrow
     * beam, and a wider one reaches a little further up and down as well as sideways.</p>
     */
    private static Vec3 offLine(Vec3 from, Vec3 to, double halfWidth, LivingEntity victim) {
        double axisX = to.x - from.x;
        double axisZ = to.z - from.z;
        double lengthSquared = axisX * axisX + axisZ * axisZ;
        double dx = victim.getX() - from.x;
        double dz = victim.getZ() - from.z;
        // How far along the beam they stand, held inside it: a beam is a segment, not a line,
        // and a beam a wall cut down to nothing is a point.
        double along = lengthSquared < 1.0E-8D ? 0.0D
                : Mth.clamp((dx * axisX + dz * axisZ) / lengthSquared, 0.0D, 1.0D);
        double offX = dx - axisX * along;
        double offZ = dz - axisZ * along;
        double gap = Math.max(0.0D, Math.max(victim.getY() - from.y, from.y - victim.getEyeY()));
        if (offX * offX + offZ * offZ + gap * gap > halfWidth * halfWidth) {
            return null;
        }
        return new Vec3(offX, 0.0D, offZ);
    }

    /**
     * Everyone the beams may catch round {@code centre}.
     *
     * <p>Asked of the boss that switched them on rather than worked out here, so a beam and
     * an area slam can never end up with different ideas of who counts as an enemy.</p>
     */
    private static List<LivingEntity> victims(ServerLevel level, EntityNPCInterface boss, Vec3 centre,
                                              double reach) {
        TeleportPathController controller = boss instanceof IBossController holder
                ? holder.cnpcgeckoaddon$getTeleportPathController() : null;
        return controller == null ? List.of() : controller.beamVictims(level, centre, reach);
    }

    /**
     * Where one beam points on this tick, as a Minecraft yaw.
     *
     * <p>The turn is taken off the yaw rather than added to it. Minecraft's yaw grows
     * clockwise seen from above, and the setting reads the other way round - positive is
     * anticlockwise, the way a compass rose and every maths lesson turn.</p>
     */
    private static float beamYaw(Sweep sweep, int index, long gameTime) {
        double turned = sweep.degreesPerTick * (gameTime - sweep.startedAt);
        return (float) (sweep.startYaw - turned + index * 360.0D / sweep.count);
    }

    /** The flat unit direction a Minecraft yaw points along, the way vanilla reads it. */
    private static Vec3 direction(float yaw) {
        double radians = yaw * Mth.DEG_TO_RAD;
        return new Vec3(-Math.sin(radians), 0.0D, Math.cos(radians));
    }

    /**
     * Where the beams leave the boss: on its centre, at its waist or a block up, whichever
     * is lower.
     */
    public static Vec3 centreOf(EntityNPCInterface boss) {
        return boss.position().add(0.0D, Math.min(boss.getBbHeight() * 0.5D, MAX_HEIGHT), 0.0D);
    }

    /**
     * Where a beam leaving {@code centre} this way ends: at its full length, or at the first
     * block with a collision box on its line when it is set to stop at walls.
     *
     * <p>Clipped down the beam's own line, the way a thrown boulder is: a pillar narrower
     * than a wide beam still cuts it, and standing behind one is the whole point. Anything
     * with a collision box counts as a wall, leaves and glass included; grass, water and
     * carpets stop nothing.</p>
     */
    private static Vec3 reach(ServerLevel level, EntityNPCInterface boss, Vec3 centre, Vec3 direction,
                              double length, boolean stopsAtWalls) {
        Vec3 end = centre.add(direction.scale(length));
        if (!stopsAtWalls) {
            return end;
        }
        BlockHitResult hit = level.clip(new ClipContext(centre, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, boss));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    }

    /**
     * The lines the beams would leave along right now, for the wind-up's mark.
     *
     * <p>Painted from the boss' centre at the yaw the cast committed to, and cut short by
     * the same walls the sweep will be: a warning that ran through a pillar would promise a
     * beam the pillar is going to stop. Always in the ability's colour, whatever look the
     * beams themselves have: it is a warning, and has to read the same under every boss.</p>
     */
    public static void paintStart(ServerLevel level, EntityNPCInterface boss, float startYaw, int count,
                                  double length, boolean stopsAtWalls) {
        Vec3 centre = centreOf(boss);
        Vec3[] ends = new Vec3[count];
        for (int i = 0; i < count; i++) {
            float yaw = (float) (startYaw + i * 360.0D / count);
            ends[i] = reach(level, boss, centre, direction(yaw), length, stopsAtWalls);
        }
        paint(level, centre, ends, length, BeamLooks.KIND);
    }

    /**
     * The beams themselves, one run of the look's particles each from the centre to wherever
     * they end.
     *
     * <p>At the wind-up mark's own spacing and ceiling, so four beams a tick cost what a
     * corridor's outline does every other one; and only with somebody near enough to see
     * them, since a beam is drawn on every tick it turns. A look only says which particle a
     * point or a wall gets, never how many, so a fire beam costs exactly what a plain one does.</p>
     */
    private static void paint(ServerLevel level, Vec3 centre, Vec3[] ends, double length, String look) {
        if (level.getNearestPlayer(centre.x, centre.y, centre.z, BossTelegraphUtil.AUDIENCE_RANGE, false) == null) {
            return;
        }
        RandomSource random = level.getRandom();
        double fullSquared = length * length - 1.0E-3D;
        for (Vec3 end : ends) {
            BossTelegraphUtil.line(level, centre, end, () -> pointParticle(look, random));
            // A beam cut short is burning into whatever cut it.
            if (end.distanceToSqr(centre) < fullSquared) {
                level.sendParticles(wallParticle(look), end.x, end.y, end.z, WALL_SPARKS,
                        0.1D, 0.1D, 0.1D, 0.05D);
            }
        }
    }

    /**
     * What one point of a beam of this look is drawn with.
     *
     * <p>A look is a main particle with a second one scattered through it, or a short-lived
     * one and a dust of its colour half and half: a spark or a snowflake is gone in a few
     * ticks, and it is the dust between them that keeps the line readable from twenty blocks.</p>
     */
    private static ParticleOptions pointParticle(String look, RandomSource random) {
        return switch (look) {
            case BeamLooks.FIRE -> accent(random) ? ParticleTypes.SMOKE : ParticleTypes.FLAME;
            case BeamLooks.SOUL -> accent(random) ? ParticleTypes.SOUL : ParticleTypes.SOUL_FIRE_FLAME;
            case BeamLooks.VOID -> random.nextInt(RARE_ACCENT_ONE_IN) == 0 ? ParticleTypes.PORTAL : ParticleTypes.END_ROD;
            case BeamLooks.LIGHTNING -> random.nextBoolean() ? ParticleTypes.ELECTRIC_SPARK : LIGHTNING_DUST;
            case BeamLooks.FROST -> random.nextBoolean() ? ParticleTypes.SNOWFLAKE : FROST_DUST;
            case BeamLooks.SCULK -> accent(random) ? ParticleTypes.SCULK_CHARGE_POP : ParticleTypes.SCULK_SOUL;
            case BeamLooks.TOXIC -> accent(random) ? ParticleTypes.ITEM_SLIME : TOXIC_DUST;
            // BeamLooks.KIND: the ability's own dust, the way the beam has always been drawn.
            default -> BossTelegraphUtil.dust(BossAbilityKind.BEAM);
        };
    }

    private static boolean accent(RandomSource random) {
        return random.nextInt(ACCENT_ONE_IN) == 0;
    }

    /** What a beam of this look throws where a wall cuts it. */
    private static SimpleParticleType wallParticle(String look) {
        return switch (look) {
            case BeamLooks.FIRE -> ParticleTypes.LAVA;
            case BeamLooks.SOUL -> ParticleTypes.SOUL;
            case BeamLooks.VOID -> ParticleTypes.REVERSE_PORTAL;
            case BeamLooks.FROST -> ParticleTypes.ITEM_SNOWBALL;
            case BeamLooks.SCULK -> ParticleTypes.SCULK_CHARGE_POP;
            case BeamLooks.TOXIC -> ParticleTypes.ITEM_SLIME;
            // The ability colour and the lightning both strike plain sparks off the wall.
            default -> ParticleTypes.CRIT;
        };
    }
}

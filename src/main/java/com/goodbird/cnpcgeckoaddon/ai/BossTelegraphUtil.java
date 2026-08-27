package com.goodbird.cnpcgeckoaddon.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The shapes a boss paints on the arena while it winds an ability up.
 *
 * <p>Everything here is cosmetic and entirely server-side: the marks go out as ordinary dust
 * particles, so a player sees what is about to happen to them without the addon installed
 * and without a packet of its own.</p>
 */
public final class BossTelegraphUtil {

    /**
     * One colour per ability, in the order {@code TeleportPathData} lists them in its warning
     * mask. Two abilities winding up in the same fight have to be told apart at a glance, so
     * the colour is what says which one is coming and not merely that one is.
     */
    private static final int[] ABILITY_COLORS = {
            0xFF5926, // area attack - red-orange
            0x4DCCFF, // ranged attack - cyan
            0xF2EACC, // melee hit - bone white
            0x59E673, // fluid spit - green
            0xCC8C40, // hook - bronze, the colour of the chain it throws
            0xFF59BF, // capture - pink
            0x8C59FF, // minion summon - violet
            0xFFCC33, // leap slam - amber
            0xFF3355  // line strike - crimson
    };

    /** How much of an ability's colour its faded half keeps. */
    private static final float FADED_BRIGHTNESS = 0.55F;
    /** Smaller as well as darker: the two together are what read as "this part hurts less". */
    private static final float FADED_SCALE = 0.7F;

    /** Built once: the options are immutable and one is handed out per ability per emit. */
    private static final DustParticleOptions[] ABILITY_DUST = buildDust(1.0F, 1.0F);
    private static final DustParticleOptions[] FADED_DUST = buildDust(FADED_BRIGHTNESS, FADED_SCALE);

    /** Roughly one emit per this many blocks along a ring, an arc or a line. */
    private static final double EMIT_SPACING = 0.6D;
    private static final int MIN_SHAPE_POINTS = 8;
    /** Ceiling on the emits per shape, so a wide ring costs no more than a narrow one. */
    private static final int MAX_SHAPE_POINTS = 48;
    private static final int MAX_LINE_POINTS = 32;
    /** The flanks of a corridor are walked at this, which is half of what its own edges get. */
    private static final double SIDE_EMIT_SPACING = EMIT_SPACING * 2.0D;
    /**
     * Ceiling on the emits one edge of a corridor costs. A rectangle is four edges and its
     * flanks are eight more, so each of them has to stay well under a whole ring's budget.
     */
    private static final int MAX_CORRIDOR_EDGE_POINTS = 24;
    /** Rare on purpose: the aura only has to catch the eye, not hide the boss behind dust. */
    private static final int AURA_PARTICLES = 6;

    private BossTelegraphUtil() {
    }

    /** The dust one ability marks the ground with. */
    public static DustParticleOptions dust(int ability) {
        return ABILITY_DUST[Mth.clamp(ability, 0, ABILITY_DUST.length - 1)];
    }

    /**
     * The same colour, dimmer, for the softer part of a mark an ability draws in two halves.
     *
     * <p>Still its own colour rather than a neutral grey: a player has to be able to see at
     * a glance that the faint band belongs to the bright one beside it.</p>
     */
    public static DustParticleOptions fadedDust(int ability) {
        return FADED_DUST[Mth.clamp(ability, 0, FADED_DUST.length - 1)];
    }

    /** The same colour as packed RGB, for the name that goes into the action bar. */
    public static int textColor(int ability) {
        return ABILITY_COLORS[Mth.clamp(ability, 0, ABILITY_COLORS.length - 1)];
    }

    /** A full circle lying on the floor, walked the way the area attack's wave is. */
    public static void ring(ServerLevel level, Vec3 centre, double radius, DustParticleOptions dust) {
        int points = shapePoints(Mth.TWO_PI * radius);
        for (int i = 0; i < points; i++) {
            double angle = i * Mth.TWO_PI / points;
            emitOnFloor(level, centre, Math.cos(angle) * radius, Math.sin(angle) * radius, dust);
        }
    }

    /**
     * The slice of a circle a boss can reach in front of itself.
     *
     * @param yaw       where the boss is looking, in Minecraft's own degrees
     * @param halfAngle how far the arc opens to either side of that
     */
    public static void arc(ServerLevel level, Vec3 centre, double radius, float yaw,
                           double halfAngle, DustParticleOptions dust) {
        double half = halfAngle * Mth.DEG_TO_RAD;
        // Minecraft measures yaw from south and turns it clockwise, which is a quarter turn
        // away from the angles the ring above walks through.
        double facing = (yaw + 90.0F) * Mth.DEG_TO_RAD;
        int points = shapePoints(2.0D * half * radius);
        for (int i = 0; i <= points; i++) {
            double angle = facing - half + i * 2.0D * half / points;
            emitOnFloor(level, centre, Math.cos(angle) * radius, Math.sin(angle) * radius, dust);
        }
    }

    /**
     * The rectangle a line strike is about to come down, and the softer bands beside it.
     *
     * <p>A ring is no use here. What a player needs from this mark is which way to step, and
     * only an outline with sides can say that. The flanks are drawn in the faded colour for
     * the same reason they hit softer: standing in one is a mistake, not a death.</p>
     *
     * @param axis      the flat unit direction the strike was committed to
     * @param width     the full width of the corridor, half of it to either side of the axis
     * @param sideWidth how far past that the softer band reaches, or zero for none
     */
    public static void corridor(ServerLevel level, Vec3 origin, Vec3 axis, double length,
                                double width, double sideWidth, DustParticleOptions dust,
                                DustParticleOptions sideDust) {
        double half = width * 0.5D;
        edge(level, origin, axis, dust, EMIT_SPACING, 0.0D, -half, length, -half);
        edge(level, origin, axis, dust, EMIT_SPACING, 0.0D, half, length, half);
        edge(level, origin, axis, dust, EMIT_SPACING, 0.0D, -half, 0.0D, half);
        edge(level, origin, axis, dust, EMIT_SPACING, length, -half, length, half);
        if (sideWidth <= 0.0D) {
            return;
        }
        // Only the outer edge and the two caps: the inner edge of each band is the corridor's
        // own side, already drawn above in the colour that matters more.
        double outer = half + sideWidth;
        for (double sign : new double[]{-1.0D, 1.0D}) {
            edge(level, origin, axis, sideDust, SIDE_EMIT_SPACING,
                    0.0D, sign * outer, length, sign * outer);
            edge(level, origin, axis, sideDust, SIDE_EMIT_SPACING,
                    0.0D, sign * half, 0.0D, sign * outer);
            edge(level, origin, axis, sideDust, SIDE_EMIT_SPACING,
                    length, sign * half, length, sign * outer);
        }
    }

    /**
     * One straight run of dust, laid out in a corridor's own terms: how far down the line a
     * point sits, and how far off it.
     */
    private static void edge(ServerLevel level, Vec3 origin, Vec3 axis, DustParticleOptions dust,
                             double spacing, double fromAlong, double fromAcross,
                             double toAlong, double toAcross) {
        double alongStep = toAlong - fromAlong;
        double acrossStep = toAcross - fromAcross;
        double distance = Math.sqrt(alongStep * alongStep + acrossStep * acrossStep);
        int points = Mth.clamp((int) Math.round(distance / spacing), 1, MAX_CORRIDOR_EDGE_POINTS);
        // A quarter turn of the axis, which is what the corridor is measured across.
        double acrossX = axis.z;
        double acrossZ = -axis.x;
        for (int i = 0; i <= points; i++) {
            double along = fromAlong + alongStep * i / points;
            double across = fromAcross + acrossStep * i / points;
            emitOnFloor(level, origin, axis.x * along + acrossX * across,
                    axis.z * along + acrossZ * across, dust);
        }
    }

    /** A straight run of dust from the boss to whatever it has picked out. */
    public static void line(ServerLevel level, Vec3 from, Vec3 to, DustParticleOptions dust) {
        Vec3 step = to.subtract(from);
        int points = Mth.clamp((int) Math.round(step.length() / EMIT_SPACING), 1, MAX_LINE_POINTS);
        for (int i = 0; i <= points; i++) {
            Vec3 point = from.add(step.scale((double) i / points));
            level.sendParticles(dust, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    /**
     * The boss itself lit up in the ability's colour.
     *
     * <p>What the zone on the floor cannot do: tell someone who is watching the boss rather
     * than their own feet that it is charging something.</p>
     */
    public static void aura(ServerLevel level, LivingEntity boss, DustParticleOptions dust) {
        double spread = boss.getBbWidth() * 0.6D;
        level.sendParticles(dust, boss.getX(), boss.getY() + boss.getBbHeight() * 0.5D, boss.getZ(),
                AURA_PARTICLES, spread, boss.getBbHeight() * 0.4D, spread, 0.0D);
    }

    private static int shapePoints(double length) {
        return Mth.clamp((int) Math.round(length / EMIT_SPACING), MIN_SHAPE_POINTS, MAX_SHAPE_POINTS);
    }

    /**
     * Drops one point of a shape onto the floor under it.
     *
     * <p>Shares the wave's floor search, so a mark lies on the arena the same way and skips
     * the same holes: a ring hanging in mid air over a balcony edge reads as a bug, and one
     * buried under the floor warns nobody.</p>
     */
    private static void emitOnFloor(ServerLevel level, Vec3 centre, double offsetX, double offsetZ,
                                    DustParticleOptions dust) {
        double x = centre.x + offsetX;
        double z = centre.z + offsetZ;
        BlockPos floor = BossAreaVfxScheduler.findFloor(level, x, centre.y, z);
        if (floor == null) {
            return;
        }
        level.sendParticles(dust, x, floor.getY() + 1.1D, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    private static DustParticleOptions[] buildDust(float brightness, float scale) {
        DustParticleOptions[] dust = new DustParticleOptions[ABILITY_COLORS.length];
        for (int i = 0; i < ABILITY_COLORS.length; i++) {
            int rgb = ABILITY_COLORS[i];
            dust[i] = new DustParticleOptions(new Vector3f(
                    (rgb >> 16 & 0xFF) / 255.0F * brightness,
                    (rgb >> 8 & 0xFF) / 255.0F * brightness,
                    (rgb & 0xFF) / 255.0F * brightness), scale);
        }
        return dust;
    }
}

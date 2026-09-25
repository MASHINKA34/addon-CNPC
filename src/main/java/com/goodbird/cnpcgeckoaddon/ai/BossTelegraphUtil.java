package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.utils.BossFloorUtil;
import com.goodbird.cnpcgeckoaddon.utils.TelegraphShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The shapes a boss paints on the arena while it winds an ability up.
 *
 * <p>Everything here is cosmetic and entirely server-side: the marks go out as ordinary dust
 * particles, so a player sees what is about to happen to them without the addon installed
 * and without a packet of its own.</p>
 */
public final class BossTelegraphUtil {

    /**
     * With no player this close the mark cannot be seen, so it is not worth the particles.
     *
     * <p>What every boss used before the range was a setting, and still what anything with
     * no boss behind it uses. Ask {@link #audienceRange} wherever the boss is known.</p>
     */
    public static final double AUDIENCE_RANGE = 64.0D;

    /**
     * One colour per ability, numbered by {@link BossAbilityKind}. Two abilities winding up
     * in the same fight have to be told apart at a glance, so the colour is what says which
     * one is coming and not merely that one is.
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
            0xFF3355, // line strike - crimson
            0xFF8C1A, // death blast - ember; never painted, the boss is already dead
            0x3355FF, // geyser - deep blue, the column coming up out of the floor
            0x9999A6, // boulder - stone grey, the colour of the thing about to arrive
            0xC2703D, // boulder rain - dust brown, the same stone seen from underneath
            0x2EE6B8, // tether - teal, a leash of light that has to be run through
            0xE040FB, // gravity - magenta, the one hue between the summon and the capture
            0xFFE14D, // mark - pale yellow, a lit fuse read against the geyser's blue
            0xB4FF3C, // take cover - lime, the one green left between the spit and the mark
            0xFF1A1A, // arena hazard - plain red, between the slam's orange and the strike's crimson
            0x2E8B57, // hunt - hunter green, well darker than the spit's mint and the lime of take cover
            0xE6F2FF, // sweeping beam - cold white, a searing line read against the melee's warm cream
            0xD9A6E0, // cocoon - cobweb mauve, the capture's pink with the life bled out of it
            0x1E90FF, // dash - azure streak, a deeper blue than the shot's cyan and brighter than the geyser's
            0xFF9A3C, // cone strike - tangerine, between the slam's red-orange and the leap's amber, where only the unpainted blast sat
            0x00FF00, // platforms - signal green, pure where take cover's lime leans yellow and the spit's mint pales, and never the hazard box's red
            0x7FD4FF, // hurricane - storm sky, a paler blue than the dash's azure and the shot's cyan, well clear of the geyser's deep blue
            0x4B3D8F, // shadow copies - deep indigo, the summon's violet with the light taken out of it, darker than the cocoon's mauve
            0xA0522D, // seismic waves - sienna, the cracked floor's own brown, redder than the rain's dust and darker than the hook's bronze
            0x6A00B4, // reality rift - the rift's own deep purple, the tint its victims see, darker than the summon's violet and richer than the copies' indigo
            0xB5A642  // vents - brass, the grate in the wall, duller than the mark's yellow and greener than the hook's bronze, off the crowded fire hues
    };

    /** How much of an ability's colour its faded half keeps, before the boss says otherwise. */
    private static final float FADED_BRIGHTNESS = 0.55F;
    /** Smaller as well as darker: the two together are what read as "this part hurts less". */
    private static final float FADED_SCALE = 0.7F;

    /** Built once: the options are immutable and one is handed out per ability per emit. */
    private static final DustParticleOptions[] ABILITY_DUST = buildDust(1.0F, 1.0F);
    private static final DustParticleOptions[] FADED_DUST = buildDust(FADED_BRIGHTNESS, FADED_SCALE);

    /**
     * One set of faded dust per brightness a boss asked for, so a mark drawn every other tick
     * is not a new options object every other tick. A handful of bosses is a handful of
     * entries, and the whole range is ninety of them at worst.
     */
    private static final Map<Integer, DustParticleOptions[]> TUNED_FADED_DUST = new ConcurrentHashMap<>();

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
    /** The brightness the prebuilt faded set was made at, in per cent. */
    private static final int DEFAULT_FADED_PERCENT = Math.round(FADED_BRIGHTNESS * 100.0F);
    /**
     * Spacing and ceiling for an edge that stays up for a whole phase rather than a wind-up.
     *
     * <p>Wider apart than a wind-up mark's points, and far more of them. The shapes above
     * cap their points so a wide ring costs no more than a narrow one, and an arena hazard's
     * ring is wide by nature: forty-eight points round a circle sixty blocks across are dots
     * four blocks apart, which nobody can stand just inside of. The count is affordable
     * because vanilla only hands a particle to players within thirty-two blocks of it, so
     * each player pays for the arc near them and never for the whole circle.</p>
     */
    private static final double EDGE_EMIT_SPACING = 1.0D;
    private static final int MAX_EDGE_POINTS = 256;

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

    /** The same, as dim as this boss asked its faded half to be. */
    public static DustParticleOptions fadedDust(int ability, int fadedPercent) {
        if (fadedPercent == DEFAULT_FADED_PERCENT) {
            return fadedDust(ability);
        }
        DustParticleOptions[] set = TUNED_FADED_DUST.computeIfAbsent(fadedPercent,
                percent -> buildDust(percent / 100.0F, FADED_SCALE));
        return set[Mth.clamp(ability, 0, set.length - 1)];
    }

    /**
     * How far from this boss a mark of its is worth drawing at all.
     *
     * @param boss the boss whose mark it is; anything that is not a configured boss gets
     *             {@link #AUDIENCE_RANGE}, which is what every one of them used to get
     */
    public static double audienceRange(Entity boss) {
        return BossTuningUtil.of(boss).telegraphAudienceRange();
    }

    /** The same colour as packed RGB, for the name that goes into the action bar. */
    public static int textColor(int ability) {
        return ABILITY_COLORS[Mth.clamp(ability, 0, ABILITY_COLORS.length - 1)];
    }

    /**
     * Dust of one packed colour that belongs to no ability: the barrier borrows its boss'
     * bar colour rather than having a hue of its own in the list above.
     */
    public static DustParticleOptions dustOf(int rgb) {
        return new DustParticleOptions(new Vector3f(
                (rgb >> 16 & 0xFF) / 255.0F, (rgb >> 8 & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F), 1.0F);
    }

    /**
     * The same shapes, drawn the way the boss was told to draw them.
     *
     * <p>Each one is the old call when the warning is dust, and the figure itself - its
     * middle and its radius, its corners, its axis - handed to {@link BossTelegraphFrames}
     * when it is a band. Nothing in between: the points of a band are cut on the client,
     * where the floor under each of them is known.</p>
     */
    public static void ring(ServerLevel level, Vec3 centre, double radius, BossTelegraphPaint paint) {
        if (!paint.lines()) {
            ring(level, centre, radius, paint.dust());
            return;
        }
        BossTelegraphFrames.add(level, paint, TelegraphShape.ring(centre, radius, paint.rgb(), false));
    }

    /** An edge read exactly rather than merely noticed; one figure either way. */
    public static void edgeRing(ServerLevel level, Vec3 centre, double radius, BossTelegraphPaint paint) {
        if (!paint.lines()) {
            edgeRing(level, centre, radius, paint.dust());
            return;
        }
        BossTelegraphFrames.add(level, paint, TelegraphShape.ring(centre, radius, paint.rgb(), false));
    }

    public static void rectangle(ServerLevel level, double minX, double minZ, double maxX,
                                 double maxZ, double y, BossTelegraphPaint paint) {
        rectangle(level, minX, minZ, maxX, maxZ, y, paint, EDGE_EMIT_SPACING);
    }

    /**
     * The same box dotted as closely as the caller asks, for a platform whose outline has to
     * read from across the arena. A band ignores the spacing: the client draws it whole.
     */
    public static void rectangle(ServerLevel level, double minX, double minZ, double maxX,
                                 double maxZ, double y, BossTelegraphPaint paint, double spacing) {
        if (!paint.lines()) {
            rectangle(level, minX, minZ, maxX, maxZ, y, paint.dust(), spacing);
            return;
        }
        BossTelegraphFrames.add(level, paint,
                TelegraphShape.rectangle(minX, minZ, maxX, maxZ, y, paint.rgb(), false));
    }

    public static void arc(ServerLevel level, Vec3 centre, double radius, float yaw,
                           double halfAngle, BossTelegraphPaint paint) {
        if (!paint.lines()) {
            arc(level, centre, radius, yaw, halfAngle, paint.dust());
            return;
        }
        BossTelegraphFrames.add(level, paint,
                TelegraphShape.arc(centre, radius, yaw, halfAngle, paint.rgb(), false));
    }

    public static void sector(ServerLevel level, Vec3 centre, double radius, float yaw,
                              double halfAngle, BossTelegraphPaint paint) {
        if (!paint.lines()) {
            sector(level, centre, radius, yaw, halfAngle, paint.dust());
            return;
        }
        BossTelegraphFrames.add(level, paint,
                TelegraphShape.sector(centre, radius, yaw, halfAngle, paint.rgb(), false));
    }

    /** The fan of a cone that lands after the one being swung: the same figure, told to fade. */
    public static void fadedSector(ServerLevel level, Vec3 centre, double radius, float yaw,
                                   double halfAngle, BossTelegraphPaint paint) {
        if (!paint.lines()) {
            fadedSector(level, centre, radius, yaw, halfAngle, paint.fadedDust());
            return;
        }
        BossTelegraphFrames.add(level, paint,
                TelegraphShape.sector(centre, radius, yaw, halfAngle, paint.rgb(), true));
    }

    /** The lane and its softer bands, which the figure carries rather than a second colour. */
    public static void corridor(ServerLevel level, Vec3 origin, Vec3 axis, double length,
                                double width, double sideWidth, BossTelegraphPaint paint) {
        if (!paint.lines()) {
            corridor(level, origin, axis, length, width, sideWidth, paint.dust(), paint.fadedDust());
            return;
        }
        BossTelegraphFrames.add(level, paint,
                TelegraphShape.corridor(origin, axis, length, width, sideWidth, paint.rgb(), false));
    }

    /** The run from the boss to whatever it picked out; the one figure that is not on the floor. */
    public static void line(ServerLevel level, Vec3 from, Vec3 to, BossTelegraphPaint paint) {
        if (!paint.lines()) {
            line(level, from, to, paint.dust());
            return;
        }
        BossTelegraphFrames.add(level, paint, TelegraphShape.link(from, to, paint.rgb(), false));
    }

    /** A full circle lying on the floor, walked the way the area attack's wave is. */
    public static void ring(ServerLevel level, Vec3 centre, double radius, DustParticleOptions dust) {
        int points = shapePoints(Mth.TWO_PI * radius);
        for (int i = 0; i < points; i++) {
            double angle = i * Mth.TWO_PI / points;
            emitOnFloor(level, centre, Math.cos(angle) * radius, Math.sin(angle) * radius, dust);
        }
    }

    /** A ring that has to be read exactly, not merely noticed: the edge of an arena hazard. */
    public static void edgeRing(ServerLevel level, Vec3 centre, double radius, DustParticleOptions dust) {
        int points = Mth.clamp((int) Math.round(Mth.TWO_PI * radius / EDGE_EMIT_SPACING),
                MIN_SHAPE_POINTS, MAX_EDGE_POINTS);
        for (int i = 0; i < points; i++) {
            double angle = i * Mth.TWO_PI / points;
            emitOnFloor(level, centre, Math.cos(angle) * radius, Math.sin(angle) * radius, dust);
        }
    }

    /**
     * The flat outline of a box, on the floor found under {@code y}, for an arena hazard
     * that burns inside it.
     *
     * <p>Walked edge by edge at the hazard ring's spacing. Each edge keeps its first corner
     * and leaves its last to the edge after it, so no corner is painted twice.</p>
     */
    public static void rectangle(ServerLevel level, double minX, double minZ, double maxX, double maxZ,
                                 double y, DustParticleOptions dust) {
        rectangle(level, minX, minZ, maxX, maxZ, y, dust, EDGE_EMIT_SPACING);
    }

    /** The same box, its edges walked at the caller's own spacing rather than the hazard's. */
    public static void rectangle(ServerLevel level, double minX, double minZ, double maxX, double maxZ,
                                 double y, DustParticleOptions dust, double spacing) {
        Vec3 origin = new Vec3(minX, y, minZ);
        double width = maxX - minX;
        double depth = maxZ - minZ;
        edgeRun(level, origin, dust, spacing, 0.0D, 0.0D, width, 0.0D);
        edgeRun(level, origin, dust, spacing, width, 0.0D, width, depth);
        edgeRun(level, origin, dust, spacing, width, depth, 0.0D, depth);
        edgeRun(level, origin, dust, spacing, 0.0D, depth, 0.0D, 0.0D);
    }

    /** One side of a rectangle, from one corner up to but not including the next. */
    private static void edgeRun(ServerLevel level, Vec3 origin, DustParticleOptions dust, double spacing,
                                double fromX, double fromZ, double toX, double toZ) {
        double stepX = toX - fromX;
        double stepZ = toZ - fromZ;
        int points = edgePoints(Math.sqrt(stepX * stepX + stepZ * stepZ), spacing);
        for (int i = 0; i < points; i++) {
            emitOnFloor(level, origin, fromX + stepX * i / points, fromZ + stepZ * i / points, dust);
        }
    }

    /**
     * How many points one edge of {@code length} gets at {@code spacing}: at least its first
     * corner, and never more than the edge ceiling however long or closely dotted it is.
     */
    static int edgePoints(double length, double spacing) {
        return Mth.clamp((int) Math.round(length / spacing), 1, MAX_EDGE_POINTS);
    }

    /**
     * The slice of a circle a boss can reach in front of itself.
     *
     * @param yaw       where the boss is looking, in Minecraft's own degrees
     * @param halfAngle how far the arc opens to either side of that
     */
    public static void arc(ServerLevel level, Vec3 centre, double radius, float yaw,
                           double halfAngle, DustParticleOptions dust) {
        arc(level, centre, radius, yaw, halfAngle, dust, EMIT_SPACING);
    }

    private static void arc(ServerLevel level, Vec3 centre, double radius, float yaw,
                            double halfAngle, DustParticleOptions dust, double spacing) {
        double half = halfAngle * Mth.DEG_TO_RAD;
        // Minecraft measures yaw from south and turns it clockwise, which is a quarter turn
        // away from the angles the ring above walks through.
        double facing = (yaw + 90.0F) * Mth.DEG_TO_RAD;
        int points = shapePoints(2.0D * half * radius, spacing);
        for (int i = 0; i <= points; i++) {
            double angle = facing - half + i * 2.0D * half / points;
            emitOnFloor(level, centre, Math.cos(angle) * radius, Math.sin(angle) * radius, dust);
        }
    }

    /**
     * The fan a cone strike is about to land: the arc at its full length, and a straight edge
     * along the floor from the boss out to each end of it.
     *
     * <p>An arc on its own is the melee swing's mark, and reads as "somewhere in front". The two
     * edges are what a player needs from a cone: where its sides run, so which way is out.</p>
     *
     * @param yaw       the middle of the fan, in Minecraft's own degrees
     * @param halfAngle how far the fan opens to either side of that
     */
    public static void sector(ServerLevel level, Vec3 centre, double radius, float yaw,
                              double halfAngle, DustParticleOptions dust) {
        sector(level, centre, radius, yaw, halfAngle, dust, EMIT_SPACING);
    }

    /**
     * The same fan walked at half the density, for a cone that lands after the one marked
     * brightly: drawn in the faded colour, the way a corridor's flanks are, and for the same
     * reason spaced out the way they are.
     */
    public static void fadedSector(ServerLevel level, Vec3 centre, double radius, float yaw,
                                   double halfAngle, DustParticleOptions dust) {
        sector(level, centre, radius, yaw, halfAngle, dust, SIDE_EMIT_SPACING);
    }

    private static void sector(ServerLevel level, Vec3 centre, double radius, float yaw,
                               double halfAngle, DustParticleOptions dust, double spacing) {
        arc(level, centre, radius, yaw, halfAngle, dust, spacing);
        double facing = (yaw + 90.0F) * Mth.DEG_TO_RAD;
        double half = halfAngle * Mth.DEG_TO_RAD;
        for (double side : new double[]{-half, half}) {
            double angle = facing + side;
            edge(level, centre, new Vec3(Math.cos(angle), 0.0D, Math.sin(angle)), dust, spacing,
                    0.0D, 0.0D, radius, 0.0D);
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
        line(level, from, to, () -> dust);
    }

    /**
     * The same run in whatever each point asks for: a sweeping beam's look picks a particle
     * point by point, and the run keeps its spacing and its ceiling whichever it picks.
     */
    public static void line(ServerLevel level, Vec3 from, Vec3 to, Supplier<ParticleOptions> particle) {
        Vec3 step = to.subtract(from);
        int points = Mth.clamp((int) Math.round(step.length() / EMIT_SPACING), 1, MAX_LINE_POINTS);
        for (int i = 0; i <= points; i++) {
            Vec3 point = from.add(step.scale((double) i / points));
            level.sendParticles(particle.get(), point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    /**
     * The boss itself lit up in the ability's colour.
     *
     * <p>What the zone on the floor cannot do: tell someone who is watching the boss rather
     * than their own feet that it is charging something.</p>
     */
    public static void aura(ServerLevel level, LivingEntity boss, DustParticleOptions dust) {
        int particles = BossTuningUtil.of(boss).telegraphAuraParticles();
        if (particles <= 0) {
            return;
        }
        double spread = boss.getBbWidth() * 0.6D;
        level.sendParticles(dust, boss.getX(), boss.getY() + boss.getBbHeight() * 0.5D, boss.getZ(),
                particles, spread, boss.getBbHeight() * 0.4D, spread, 0.0D);
    }

    private static int shapePoints(double length) {
        return shapePoints(length, EMIT_SPACING);
    }

    private static int shapePoints(double length, double spacing) {
        return Mth.clamp((int) Math.round(length / spacing), MIN_SHAPE_POINTS, MAX_SHAPE_POINTS);
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
        BlockPos floor = BossFloorUtil.findFloor(level, x, centre.y, z);
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

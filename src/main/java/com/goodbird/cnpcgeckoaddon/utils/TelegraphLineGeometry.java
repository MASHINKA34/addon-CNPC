package com.goodbird.cnpcgeckoaddon.utils;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The flat geometry and the timing curves a drawn boss warning is made of.
 *
 * <p>Kept away from the renderer on purpose. Everything here is a function of numbers the
 * server sent and the moment it is being looked at, which is what lets a test walk a ring
 * without a world, a window or a graphics card - and the shapes are exactly the ones a
 * player is about to be killed by, so they are worth checking.</p>
 *
 * <p>Nothing here touches a block. A contour comes out as points at the height the shape was
 * drawn at; laying each of them on its own floor is the renderer's job, because only the
 * client knows what it has loaded.</p>
 */
public final class TelegraphLineGeometry {

    /** No piece of a contour is longer than this, so a band follows a staircase step by step. */
    public static final double SEGMENT = 0.5D;
    /** Enough for a circle sixty blocks across at that step; past it the shape is thinned. */
    public static final int MAX_RUN_POINTS = 512;
    /** A tiny ring is still a circle rather than a triangle. */
    private static final int MIN_RUN_POINTS = 8;

    /** Dashed: a block of band, half a block of nothing. */
    public static final double DASH_LENGTH = 1.0D;
    public static final double DASH_GAP = 0.5D;
    /** Dotted: short marks with more space than mark between them. */
    public static final double DOT_LENGTH = 0.3D;
    public static final double DOT_GAP = 0.4D;

    /** What a fading band keeps by the time the ability lands. */
    public static final float FADE_END_ALPHA = 0.25F;
    /** Ticks per blink at the start of a wind-up and at the end of it. */
    public static final float PULSE_PERIOD_START_TICKS = 20.0F;
    public static final float PULSE_PERIOD_END_TICKS = 5.0F;
    /** A pulse dims rather than disappears: a warning that is not there warns nobody. */
    public static final float PULSE_FLOOR_ALPHA = 0.35F;

    /** How finely a filled fan is cut round and outwards; coarser than the outline on purpose. */
    private static final int MAX_FILL_ARC_STEPS = 64;
    private static final int FILL_BANDS = 8;
    /** And how finely a filled box or lane is cut, per side. */
    private static final int MAX_FILL_GRID = 24;

    private TelegraphLineGeometry() {
    }

    /**
     * One unbroken line of a shape's outline.
     *
     * <p>A closed run repeats its first point as its last, so whoever walks it never has to
     * remember to shut it. {@code faded} is the softer half of a shape drawn in two - the
     * bands beside a lane, the cones a series strikes after the one it is swinging.</p>
     */
    public record Run(List<Vec3> points, boolean closed, boolean faded) {
        public Run {
            points = List.copyOf(points);
        }

        /** How far it is from one end of the run to the other, along the run. */
        public double length() {
            double total = 0.0D;
            for (int i = 1; i < points.size(); i++) {
                total += points.get(i).distanceTo(points.get(i - 1));
            }
            return total;
        }
    }

    /** One quad of a shape's inside, with how far out of the shape it sits, from 0 to 1. */
    public record Cell(Vec3 a, Vec3 b, Vec3 c, Vec3 d, double reach) {
    }

    /** A stretch of a run, measured along it from its start. */
    public record Span(double from, double to) {
        public double length() {
            return Math.max(0.0D, to - from);
        }
    }

    /** Every line a shape's outline is drawn as. */
    public static List<Run> contours(TelegraphShape shape) {
        return switch (shape.kind()) {
            case TelegraphShape.KIND_RING -> List.of(ringRun(shape));
            case TelegraphShape.KIND_RECTANGLE -> List.of(rectangleRun(shape));
            case TelegraphShape.KIND_ARC -> List.of(arcRun(shape));
            case TelegraphShape.KIND_SECTOR -> sectorRuns(shape);
            case TelegraphShape.KIND_CORRIDOR -> corridorRuns(shape);
            case TelegraphShape.KIND_LINK -> List.of(linkRun(shape));
            default -> List.of();
        };
    }

    private static Run ringRun(TelegraphShape shape) {
        Vec3 centre = shape.anchor();
        double radius = Math.abs(shape.radius());
        int steps = steps(Mth.TWO_PI * radius);
        List<Vec3> points = new ArrayList<>(steps + 1);
        for (int i = 0; i < steps; i++) {
            double angle = i * Mth.TWO_PI / steps;
            points.add(centre.add(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius));
        }
        points.add(points.getFirst());
        return new Run(points, true, shape.faded());
    }

    private static Run rectangleRun(TelegraphShape shape) {
        double minX = Math.min(shape.x(), shape.maxX());
        double maxX = Math.max(shape.x(), shape.maxX());
        double minZ = Math.min(shape.z(), shape.maxZ());
        double maxZ = Math.max(shape.z(), shape.maxZ());
        double y = shape.y();
        List<Vec3> corners = List.of(
                new Vec3(minX, y, minZ), new Vec3(maxX, y, minZ),
                new Vec3(maxX, y, maxZ), new Vec3(minX, y, maxZ), new Vec3(minX, y, minZ));
        List<Vec3> points = new ArrayList<>();
        points.add(corners.getFirst());
        for (int i = 1; i < corners.size(); i++) {
            walk(points, corners.get(i - 1), corners.get(i));
        }
        close(points);
        return new Run(points, true, shape.faded());
    }

    private static Run arcRun(TelegraphShape shape) {
        return new Run(arcPoints(shape), false, shape.faded());
    }

    private static List<Vec3> arcPoints(TelegraphShape shape) {
        Vec3 centre = shape.anchor();
        double radius = Math.abs(shape.radius());
        double half = shape.halfAngle() * Mth.DEG_TO_RAD;
        double facing = facing(shape);
        int steps = steps(2.0D * half * radius);
        List<Vec3> points = new ArrayList<>(steps + 1);
        for (int i = 0; i <= steps; i++) {
            double angle = facing - half + i * 2.0D * half / steps;
            points.add(centre.add(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius));
        }
        return points;
    }

    /**
     * Minecraft measures yaw from south and turns it clockwise, which is a quarter turn away
     * from the angles a ring is walked through.
     */
    private static double facing(TelegraphShape shape) {
        return (shape.yaw() + 90.0F) * Mth.DEG_TO_RAD;
    }

    /**
     * The fan: its arc, and a straight side from the middle out to each end of it.
     *
     * <p>An arc on its own reads as "somewhere in front". The two sides are what a player
     * needs from a cone - where it stops, so which way is out - so they are their own runs
     * and not a decoration on the arc.</p>
     */
    private static List<Run> sectorRuns(TelegraphShape shape) {
        List<Vec3> arc = arcPoints(shape);
        List<Run> runs = new ArrayList<>(3);
        runs.add(new Run(arc, false, shape.faded()));
        Vec3 centre = shape.anchor();
        for (Vec3 end : List.of(arc.getFirst(), arc.getLast())) {
            List<Vec3> side = new ArrayList<>();
            side.add(centre);
            walk(side, centre, end);
            runs.add(new Run(side, false, shape.faded()));
        }
        return runs;
    }

    /**
     * The lane, and the softer band beside it.
     *
     * <p>The band is drawn as its outer edge and the two caps that join it to the lane: its
     * inner edge is the lane's own side, already drawn in the colour that matters more.</p>
     */
    private static List<Run> corridorRuns(TelegraphShape shape) {
        Vec3 origin = shape.anchor();
        Vec3 axis = shape.axis();
        double length = shape.length();
        double half = shape.width() * 0.5D;
        double sideWidth = shape.sideWidth();
        List<Run> runs = new ArrayList<>(3);
        List<Vec3> lane = lane(origin, axis, List.of(
                new double[]{0.0D, -half}, new double[]{length, -half},
                new double[]{length, half}, new double[]{0.0D, half},
                new double[]{0.0D, -half}));
        close(lane);
        runs.add(new Run(lane, true, shape.faded()));
        if (sideWidth <= 0.0D) {
            return runs;
        }
        double outer = half + sideWidth;
        for (double sign : new double[]{-1.0D, 1.0D}) {
            runs.add(new Run(lane(origin, axis, List.of(
                    new double[]{0.0D, sign * half}, new double[]{0.0D, sign * outer},
                    new double[]{length, sign * outer}, new double[]{length, sign * half})),
                    false, true));
        }
        return runs;
    }

    /** Walks a lane-frame outline - how far along, how far across - out into world points. */
    private static List<Vec3> lane(Vec3 origin, Vec3 axis, List<double[]> corners) {
        // A quarter turn of the axis, which is what a lane is measured across.
        double acrossX = axis.z;
        double acrossZ = -axis.x;
        List<Vec3> world = new ArrayList<>(corners.size());
        for (double[] corner : corners) {
            world.add(origin.add(axis.x * corner[0] + acrossX * corner[1], 0.0D,
                    axis.z * corner[0] + acrossZ * corner[1]));
        }
        List<Vec3> points = new ArrayList<>();
        points.add(world.getFirst());
        for (int i = 1; i < world.size(); i++) {
            walk(points, world.get(i - 1), world.get(i));
        }
        return points;
    }

    private static Run linkRun(TelegraphShape shape) {
        Vec3 from = shape.anchor();
        Vec3 to = shape.to();
        List<Vec3> points = new ArrayList<>();
        points.add(from);
        walk(points, from, to);
        return new Run(points, false, shape.faded());
    }

    /**
     * Shuts a run on the very point it started from.
     *
     * <p>Walking all the way round and stopping where the arithmetic lands is not the same
     * thing: a circle's last point comes out a hair off its first, and a hair of a gap in a
     * band on the floor reads as a hole in the arena.</p>
     */
    private static void close(List<Vec3> points) {
        points.set(points.size() - 1, points.getFirst());
    }

    /** Adds every point after {@code from} up to and including {@code to}. */
    private static void walk(List<Vec3> points, Vec3 from, Vec3 to) {
        int steps = Mth.clamp((int) Math.ceil(from.distanceTo(to) / SEGMENT), 1, MAX_RUN_POINTS);
        for (int i = 1; i <= steps; i++) {
            points.add(from.add(to.subtract(from).scale((double) i / steps)));
        }
    }

    /** The steps a curve is cut into: never so few that a small circle reads as a polygon. */
    private static int steps(double length) {
        return Mth.clamp((int) Math.ceil(length / SEGMENT), MIN_RUN_POINTS, MAX_RUN_POINTS);
    }

    /**
     * The inside of a shape, cut into quads, each tagged with how far out of the shape it is.
     *
     * <p>The tag is what lets a flood be drawn without cutting the shape again every frame:
     * the quads are laid out once, and the ones past the moment's reach are simply not drawn.
     * Outwards means away from the middle for a ring, a fan or a box, and away from the boss
     * for a lane - which is the direction each of those really arrives from.</p>
     */
    public static List<Cell> fill(TelegraphShape shape) {
        return switch (shape.kind()) {
            case TelegraphShape.KIND_RING -> fan(shape, 0.0D, Mth.TWO_PI);
            case TelegraphShape.KIND_ARC, TelegraphShape.KIND_SECTOR -> {
                double half = shape.halfAngle() * Mth.DEG_TO_RAD;
                yield fan(shape, facing(shape) - half, 2.0D * half);
            }
            case TelegraphShape.KIND_RECTANGLE -> rectangleCells(shape);
            case TelegraphShape.KIND_CORRIDOR -> corridorCells(shape);
            default -> List.of();
        };
    }

    private static List<Cell> fan(TelegraphShape shape, double from, double sweep) {
        Vec3 centre = shape.anchor();
        double radius = Math.abs(shape.radius());
        if (radius <= 0.0D || sweep <= 0.0D) {
            return List.of();
        }
        int around = Mth.clamp((int) Math.ceil(sweep * radius / SEGMENT), 3, MAX_FILL_ARC_STEPS);
        List<Cell> cells = new ArrayList<>(around * FILL_BANDS);
        for (int band = 0; band < FILL_BANDS; band++) {
            double inner = radius * band / FILL_BANDS;
            double outer = radius * (band + 1.0D) / FILL_BANDS;
            for (int step = 0; step < around; step++) {
                double first = from + sweep * step / around;
                double second = from + sweep * (step + 1.0D) / around;
                cells.add(new Cell(
                        at(centre, first, inner), at(centre, second, inner),
                        at(centre, second, outer), at(centre, first, outer),
                        (band + 1.0D) / FILL_BANDS));
            }
        }
        return cells;
    }

    private static Vec3 at(Vec3 centre, double angle, double distance) {
        return centre.add(Math.cos(angle) * distance, 0.0D, Math.sin(angle) * distance);
    }

    private static List<Cell> rectangleCells(TelegraphShape shape) {
        double minX = Math.min(shape.x(), shape.maxX());
        double maxX = Math.max(shape.x(), shape.maxX());
        double minZ = Math.min(shape.z(), shape.maxZ());
        double maxZ = Math.max(shape.z(), shape.maxZ());
        double y = shape.y();
        int alongX = gridSteps(maxX - minX);
        int alongZ = gridSteps(maxZ - minZ);
        List<Cell> cells = new ArrayList<>(alongX * alongZ);
        for (int ix = 0; ix < alongX; ix++) {
            for (int iz = 0; iz < alongZ; iz++) {
                double x0 = Mth.lerp((double) ix / alongX, minX, maxX);
                double x1 = Mth.lerp((ix + 1.0D) / alongX, minX, maxX);
                double z0 = Mth.lerp((double) iz / alongZ, minZ, maxZ);
                double z1 = Mth.lerp((iz + 1.0D) / alongZ, minZ, maxZ);
                // Out of the middle in both directions at once, so the flood stays a box.
                double reach = Math.max(
                        outwards(x0, x1, minX, maxX), outwards(z0, z1, minZ, maxZ));
                cells.add(new Cell(new Vec3(x0, y, z0), new Vec3(x1, y, z0),
                        new Vec3(x1, y, z1), new Vec3(x0, y, z1), reach));
            }
        }
        return cells;
    }

    /** How far out of the middle of {@code min..max} the further edge of a cell sits, 0 to 1. */
    private static double outwards(double from, double to, double min, double max) {
        double middle = (min + max) * 0.5D;
        double half = (max - min) * 0.5D;
        return half <= 0.0D ? 1.0D
                : Math.max(Math.abs(from - middle), Math.abs(to - middle)) / half;
    }

    private static List<Cell> corridorCells(TelegraphShape shape) {
        Vec3 origin = shape.anchor();
        Vec3 axis = shape.axis();
        double length = shape.length();
        double half = shape.width() * 0.5D;
        if (length <= 0.0D || half <= 0.0D) {
            return List.of();
        }
        double acrossX = axis.z;
        double acrossZ = -axis.x;
        int along = gridSteps(length);
        int across = gridSteps(half * 2.0D);
        List<Cell> cells = new ArrayList<>(along * across);
        for (int ia = 0; ia < along; ia++) {
            double a0 = length * ia / along;
            double a1 = length * (ia + 1.0D) / along;
            for (int ic = 0; ic < across; ic++) {
                double c0 = -half + 2.0D * half * ic / across;
                double c1 = -half + 2.0D * half * (ic + 1.0D) / across;
                cells.add(new Cell(
                        lanePoint(origin, axis, acrossX, acrossZ, a0, c0),
                        lanePoint(origin, axis, acrossX, acrossZ, a1, c0),
                        lanePoint(origin, axis, acrossX, acrossZ, a1, c1),
                        lanePoint(origin, axis, acrossX, acrossZ, a0, c1),
                        a1 / length));
            }
        }
        return cells;
    }

    private static Vec3 lanePoint(Vec3 origin, Vec3 axis, double acrossX, double acrossZ,
                                  double along, double across) {
        return origin.add(axis.x * along + acrossX * across, 0.0D, axis.z * along + acrossZ * across);
    }

    private static int gridSteps(double size) {
        return Mth.clamp((int) Math.ceil(Math.abs(size)), 1, MAX_FILL_GRID);
    }

    /**
     * The stretches of a run a dashed pattern leaves drawn, from its start to {@code total}.
     *
     * <p>The pattern starts on a mark, so a closed contour always has one at the point it was
     * started from rather than a gap nobody can tell from a hole in the floor.</p>
     */
    public static List<Span> dashes(double total, double dash, double gap) {
        if (total <= 0.0D) {
            return List.of();
        }
        double mark = Math.max(1.0E-4D, dash);
        double step = mark + Math.max(0.0D, gap);
        List<Span> spans = new ArrayList<>();
        for (double at = 0.0D; at < total; at += step) {
            spans.add(new Span(at, Math.min(total, at + mark)));
        }
        return spans;
    }

    /** The same stretches, cut off at {@code limit}: what a contour drawing itself shows. */
    public static List<Span> clip(List<Span> spans, double limit) {
        List<Span> clipped = new ArrayList<>(spans.size());
        for (Span span : spans) {
            if (span.from() >= limit) {
                break;
            }
            clipped.add(span.to() <= limit ? span : new Span(span.from(), limit));
        }
        return clipped;
    }

    /** How much of a run a set of stretches really covers. */
    public static double covered(List<Span> spans) {
        double total = 0.0D;
        for (Span span : spans) {
            total += span.length();
        }
        return total;
    }

    /**
     * What a fading band keeps as the hit comes nearer: everything at the start of the
     * wind-up, a quarter of it at the end. A frame with no end to count towards stays full.
     */
    public static float fadeAlpha(float progress) {
        return progress < 0.0F ? 1.0F
                : Mth.lerp(Mth.clamp(progress, 0.0F, 1.0F), 1.0F, FADE_END_ALPHA);
    }

    /** How long one blink of the pulse takes at this point of the wind-up, in ticks. */
    public static float pulsePeriodTicks(float progress) {
        return progress < 0.0F ? PULSE_PERIOD_START_TICKS
                : Mth.lerp(Mth.clamp(progress, 0.0F, 1.0F),
                        PULSE_PERIOD_START_TICKS, PULSE_PERIOD_END_TICKS);
    }

    /**
     * How far through its blinking the pulse is.
     *
     * <p>Counted as the blinks really gone by rather than as "now divided by the period of
     * the moment": the period is shrinking, and dividing by it would jump the pulse back and
     * forth every time it changed. Integrating a period that falls in a straight line gives
     * the logarithm below, which only ever goes forwards - so the blinks crowd together
     * towards the hit without the band ever stuttering.</p>
     *
     * @param windUpTicks how long the whole wind-up lasts, as the client judges it
     * @param ticks       the client's own clock, for a warning with no end to count towards
     */
    public static float pulsePhase(float progress, float windUpTicks, float ticks) {
        if (progress < 0.0F) {
            return (float) (Mth.TWO_PI * ticks / PULSE_PERIOD_START_TICKS);
        }
        float slope = PULSE_PERIOD_START_TICKS - PULSE_PERIOD_END_TICKS;
        float done = Mth.clamp(progress, 0.0F, 1.0F);
        double turns = Math.log(PULSE_PERIOD_START_TICKS
                / (PULSE_PERIOD_START_TICKS - slope * done)) / slope;
        return (float) (Mth.TWO_PI * Math.max(0.0F, windUpTicks) * turns);
    }

    /** What the pulse leaves of the band at that point of its blink. */
    public static float pulseAlpha(float phase) {
        return PULSE_FLOOR_ALPHA
                + (1.0F - PULSE_FLOOR_ALPHA) * (0.5F + 0.5F * (float) Math.cos(phase));
    }

    /**
     * How far out of a shape its flood has got, from nothing to the whole of it. A warning
     * with no end to count towards is shown whole rather than caught halfway.
     */
    public static float reach(float progress) {
        return progress < 0.0F ? 1.0F : Mth.clamp(progress, 0.0F, 1.0F);
    }
}

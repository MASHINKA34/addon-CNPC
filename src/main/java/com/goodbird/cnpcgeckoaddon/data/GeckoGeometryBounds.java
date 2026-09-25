package com.goodbird.cnpcgeckoaddon.data;

import java.util.List;
import java.util.Optional;

/**
 * Measures a GeckoLib model the way GeckoLib draws it, out of plain numbers.
 *
 * <p>The model picker frames its preview by these bounds, so they have to be the box the
 * renderer really fills: every bone moved by its animation offset, turned about its pivot Z
 * then Y then X and scaled there, every cube turned about its own pivot, each box grown by its
 * inflate - the order of GeckoLib 4's {@code RenderUtil.prepMatrixForBone} and
 * {@code GeoRenderer.renderCube}. Nothing here touches GeckoLib or Minecraft, so a test can
 * build a model by hand; {@code GeckoModelBounds} copies a baked model into these records.</p>
 *
 * <p>Units are the baked model's: pivots and offsets in pixels (sixteen to a block), boxes and
 * the result in blocks, angles in radians, x already mirrored the way the loader mirrors it.</p>
 */
public final class GeckoGeometryBounds {

    private static final double PIXEL = 1.0D / 16.0D;

    /** A flat model still has one pixel of thickness to fit, or its scale would run off to infinity. */
    private static final double MIN_EXTENT = PIXEL;

    private GeckoGeometryBounds() {
    }

    /**
     * One box of a bone: its corners in blocks before the inflate, and the turn about its own
     * pivot. The inflate grows the box on every side, the way Blockbench's does.
     */
    public record Cube(double minX, double minY, double minZ,
                       double maxX, double maxY, double maxZ,
                       double inflate,
                       double pivotX, double pivotY, double pivotZ,
                       double rotX, double rotY, double rotZ) {

        /** An unturned, uninflated box. */
        public static Cube box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            return new Cube(minX, minY, minZ, maxX, maxY, maxZ, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    /**
     * One bone. The offset is the translation the bone is drawn with (GeckoLib's position with
     * its x negated), the scale is applied at the pivot after the turn. A bone GeckoLib would
     * not draw is handed in without its cubes or its children rather than flagged.
     */
    public record Bone(double pivotX, double pivotY, double pivotZ,
                       double rotX, double rotY, double rotZ,
                       double scaleX, double scaleY, double scaleZ,
                       double offsetX, double offsetY, double offsetZ,
                       List<Cube> cubes, List<Bone> children) {

        public Bone {
            cubes = List.copyOf(cubes);
            children = List.copyOf(children);
        }

        /** A bone at rest: pivot only, no turn, no offset, scale one. */
        public static Bone at(double pivotX, double pivotY, double pivotZ, List<Cube> cubes, List<Bone> children) {
            return new Bone(pivotX, pivotY, pivotZ, 0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D,
                    0.0D, 0.0D, 0.0D, cubes, children);
        }
    }

    /** An axis-aligned box in blocks. */
    public record Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

        public double width() {
            return maxX - minX;
        }

        public double height() {
            return maxY - minY;
        }

        public double depth() {
            return maxZ - minZ;
        }

        public double centerX() {
            return (minX + maxX) * 0.5D;
        }

        public double centerY() {
            return (minY + maxY) * 0.5D;
        }

        public double centerZ() {
            return (minZ + maxZ) * 0.5D;
        }

        /**
         * The widest this box gets across a screen while it spins about its own vertical middle:
         * whatever the angle, no corner is further from that axis than half the diagonal.
         */
        public double horizontalDiagonal() {
            return Math.hypot(width(), depth());
        }

        /** The same box around the same origin, grown or shrunk by {@code factor}. */
        public Bounds scaled(double factor) {
            return new Bounds(minX * factor, minY * factor, minZ * factor,
                    maxX * factor, maxY * factor, maxZ * factor);
        }
    }

    /**
     * How big to draw a model in a box on screen, and where its middle goes.
     *
     * @param scale             screen pixels per block
     * @param centerAboveBottom how far above the box's bottom edge the model's vertical middle sits,
     *                          which puts its lowest point on the bottom margin
     */
    public record Fit(double scale, double centerAboveBottom) {
    }

    /**
     * The box every cube of the model fills once each bone and cube is posed, or empty when
     * the model has nothing to draw.
     */
    public static Optional<Bounds> compute(List<Bone> topLevelBones) {
        Accumulator accumulator = new Accumulator();
        if (topLevelBones != null) {
            for (Bone bone : topLevelBones) {
                include(bone, Affine.identity(), accumulator);
            }
        }
        return accumulator.toBounds();
    }

    /**
     * Fits {@code bounds} into a {@code width} by {@code height} box with {@code margin} of it
     * left free on every side. Across, the model has to fit at any turn, so the horizontal
     * diagonal is measured rather than the width; up, its height. Its feet rest on the bottom
     * margin, so a model that is wider than tall stands on the floor of the box instead of
     * floating in the middle of it.
     *
     * @param maxScale the most pixels per block a speck of a model is blown up to
     */
    public static Fit fit(Bounds bounds, double width, double height, double margin, double maxScale) {
        double free = Math.max(0.0D, 1.0D - 2.0D * margin);
        double across = Math.max(MIN_EXTENT, bounds.horizontalDiagonal());
        double up = Math.max(MIN_EXTENT, bounds.height());
        double scale = Math.min(maxScale, Math.min(width * free / across, height * free / up));
        return new Fit(scale, height * margin + bounds.height() * scale * 0.5D);
    }

    private static void include(Bone bone, Affine parent, Accumulator accumulator) {
        if (bone == null) {
            return;
        }
        Affine pose = parent.copy();
        pose.translate(bone.offsetX() * PIXEL, bone.offsetY() * PIXEL, bone.offsetZ() * PIXEL);
        pose.translate(bone.pivotX() * PIXEL, bone.pivotY() * PIXEL, bone.pivotZ() * PIXEL);
        pose.rotateZ(bone.rotZ());
        pose.rotateY(bone.rotY());
        pose.rotateX(bone.rotX());
        pose.scale(bone.scaleX(), bone.scaleY(), bone.scaleZ());
        pose.translate(-bone.pivotX() * PIXEL, -bone.pivotY() * PIXEL, -bone.pivotZ() * PIXEL);
        for (Cube cube : bone.cubes()) {
            include(cube, pose, accumulator);
        }
        for (Bone child : bone.children()) {
            include(child, pose, accumulator);
        }
    }

    private static void include(Cube cube, Affine bonePose, Accumulator accumulator) {
        if (cube == null) {
            return;
        }
        Affine pose = bonePose.copy();
        pose.translate(cube.pivotX() * PIXEL, cube.pivotY() * PIXEL, cube.pivotZ() * PIXEL);
        pose.rotateZ(cube.rotZ());
        pose.rotateY(cube.rotY());
        pose.rotateX(cube.rotX());
        pose.translate(-cube.pivotX() * PIXEL, -cube.pivotY() * PIXEL, -cube.pivotZ() * PIXEL);
        double inflate = cube.inflate();
        double[] xs = {cube.minX() - inflate, cube.maxX() + inflate};
        double[] ys = {cube.minY() - inflate, cube.maxY() + inflate};
        double[] zs = {cube.minZ() - inflate, cube.maxZ() + inflate};
        for (double x : xs) {
            for (double y : ys) {
                for (double z : zs) {
                    accumulator.include(pose.x(x, y, z), pose.y(x, y, z), pose.z(x, y, z));
                }
            }
        }
    }

    /**
     * A 3x4 affine matrix that multiplies on the right the way a PoseStack does, so the calls
     * above read in the order GeckoLib makes them.
     */
    private static final class Affine {
        private double m00;
        private double m01;
        private double m02;
        private double m03;
        private double m10;
        private double m11;
        private double m12;
        private double m13;
        private double m20;
        private double m21;
        private double m22;
        private double m23;

        static Affine identity() {
            Affine affine = new Affine();
            affine.m00 = 1.0D;
            affine.m11 = 1.0D;
            affine.m22 = 1.0D;
            return affine;
        }

        Affine copy() {
            Affine copy = new Affine();
            copy.m00 = m00;
            copy.m01 = m01;
            copy.m02 = m02;
            copy.m03 = m03;
            copy.m10 = m10;
            copy.m11 = m11;
            copy.m12 = m12;
            copy.m13 = m13;
            copy.m20 = m20;
            copy.m21 = m21;
            copy.m22 = m22;
            copy.m23 = m23;
            return copy;
        }

        void translate(double x, double y, double z) {
            m03 += m00 * x + m01 * y + m02 * z;
            m13 += m10 * x + m11 * y + m12 * z;
            m23 += m20 * x + m21 * y + m22 * z;
        }

        void scale(double x, double y, double z) {
            m00 *= x;
            m10 *= x;
            m20 *= x;
            m01 *= y;
            m11 *= y;
            m21 *= y;
            m02 *= z;
            m12 *= z;
            m22 *= z;
        }

        void rotateX(double angle) {
            if (angle == 0.0D) {
                return;
            }
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double a01 = m01 * cos + m02 * sin;
            double a11 = m11 * cos + m12 * sin;
            double a21 = m21 * cos + m22 * sin;
            m02 = m02 * cos - m01 * sin;
            m12 = m12 * cos - m11 * sin;
            m22 = m22 * cos - m21 * sin;
            m01 = a01;
            m11 = a11;
            m21 = a21;
        }

        void rotateY(double angle) {
            if (angle == 0.0D) {
                return;
            }
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double a00 = m00 * cos - m02 * sin;
            double a10 = m10 * cos - m12 * sin;
            double a20 = m20 * cos - m22 * sin;
            m02 = m00 * sin + m02 * cos;
            m12 = m10 * sin + m12 * cos;
            m22 = m20 * sin + m22 * cos;
            m00 = a00;
            m10 = a10;
            m20 = a20;
        }

        void rotateZ(double angle) {
            if (angle == 0.0D) {
                return;
            }
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double a00 = m00 * cos + m01 * sin;
            double a10 = m10 * cos + m11 * sin;
            double a20 = m20 * cos + m21 * sin;
            m01 = m01 * cos - m00 * sin;
            m11 = m11 * cos - m10 * sin;
            m21 = m21 * cos - m20 * sin;
            m00 = a00;
            m10 = a10;
            m20 = a20;
        }

        double x(double x, double y, double z) {
            return m00 * x + m01 * y + m02 * z + m03;
        }

        double y(double x, double y, double z) {
            return m10 * x + m11 * y + m12 * z + m13;
        }

        double z(double x, double y, double z) {
            return m20 * x + m21 * y + m22 * z + m23;
        }
    }

    private static final class Accumulator {
        private double minX = Double.POSITIVE_INFINITY;
        private double minY = Double.POSITIVE_INFINITY;
        private double minZ = Double.POSITIVE_INFINITY;
        private double maxX = Double.NEGATIVE_INFINITY;
        private double maxY = Double.NEGATIVE_INFINITY;
        private double maxZ = Double.NEGATIVE_INFINITY;

        void include(double x, double y, double z) {
            // One broken number in a third-party file must not stretch the frame to infinity.
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                return;
            }
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }

        Optional<Bounds> toBounds() {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                return Optional.empty();
            }
            return Optional.of(new Bounds(minX, minY, minZ, maxX, maxY, maxZ));
        }
    }
}

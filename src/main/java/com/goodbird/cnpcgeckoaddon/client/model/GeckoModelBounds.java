package com.goodbird.cnpcgeckoaddon.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.util.RenderUtil;

import java.util.Optional;

/** Calculates model-space bounds from the geometry GeckoLib actually renders. */
public final class GeckoModelBounds {
    private GeckoModelBounds() {
    }

    public static Optional<Bounds> calculateModelBounds(BakedGeoModel model) {
        if (model == null) {
            return Optional.empty();
        }

        BoundsAccumulator accumulator = new BoundsAccumulator();
        PoseStack poseStack = new PoseStack();
        try {
            for (GeoBone bone : model.topLevelBones()) {
                includeBone(bone, poseStack, accumulator);
            }
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
        return accumulator.toBounds();
    }

    private static void includeBone(GeoBone bone, PoseStack poseStack, BoundsAccumulator accumulator) {
        if (bone == null || bone.isHidden() || Boolean.TRUE.equals(bone.shouldNeverRender())) {
            return;
        }

        poseStack.pushPose();
        RenderUtil.prepMatrixForBone(poseStack, bone);
        for (GeoCube cube : bone.getCubes()) {
            includeCube(cube, poseStack, accumulator);
        }
        if (!bone.isHidingChildren()) {
            for (GeoBone child : bone.getChildBones()) {
                includeBone(child, poseStack, accumulator);
            }
        }
        poseStack.popPose();
    }

    private static void includeCube(GeoCube cube, PoseStack poseStack, BoundsAccumulator accumulator) {
        if (cube == null) {
            return;
        }

        poseStack.pushPose();
        RenderUtil.translateToPivotPoint(poseStack, cube);
        RenderUtil.rotateMatrixAroundCube(poseStack, cube);
        RenderUtil.translateAwayFromPivotPoint(poseStack, cube);
        for (GeoQuad quad : cube.quads()) {
            if (quad == null) {
                continue;
            }
            for (GeoVertex vertex : quad.vertices()) {
                if (vertex == null) {
                    continue;
                }
                Vector3f transformed = poseStack.last().pose()
                        .transformPosition(new Vector3f(vertex.position()));
                accumulator.include(transformed.x, transformed.y, transformed.z);
            }
        }
        poseStack.popPose();
    }

    public record Bounds(double minX, double minY, double minZ,
                         double maxX, double maxY, double maxZ) {
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
    }

    private static final class BoundsAccumulator {
        private double minX = Double.POSITIVE_INFINITY;
        private double minY = Double.POSITIVE_INFINITY;
        private double minZ = Double.POSITIVE_INFINITY;
        private double maxX = Double.NEGATIVE_INFINITY;
        private double maxY = Double.NEGATIVE_INFINITY;
        private double maxZ = Double.NEGATIVE_INFINITY;

        private void include(double x, double y, double z) {
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

        private Optional<Bounds> toBounds() {
            if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)
                    || !Double.isFinite(maxX) || !Double.isFinite(maxY) || !Double.isFinite(maxZ)) {
                return Optional.empty();
            }
            return Optional.of(new Bounds(minX, minY, minZ, maxX, maxY, maxZ));
        }
    }
}

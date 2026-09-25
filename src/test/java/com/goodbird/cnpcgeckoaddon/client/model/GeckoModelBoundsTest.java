package com.goodbird.cnpcgeckoaddon.client.model;

import com.goodbird.cnpcgeckoaddon.data.GeckoGeometryBounds;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.loading.json.raw.ModelProperties;
import software.bernie.geckolib.util.RenderUtil;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Holds the copy of a baked model to GeckoLib's own way of posing it.
 *
 * <p>{@link GeckoGeometryBounds} redoes GeckoLib's bone and cube transforms in plain numbers, and
 * the adapter feeds it GeckoLib's baked fields with their signs and units. Both can be wrong in a
 * way that only shows as a preview cut off for the models that turn a bone: so the same bones
 * are posed here through GeckoLib's RenderUtil and a PoseStack, the way the renderer does, and
 * the two boxes have to agree.</p>
 */
class GeckoModelBoundsTest {

    private static final double EPSILON = 1.0E-4D;

    @Test
    @DisplayName("the copied bones measure exactly what GeckoLib's own pose of them draws")
    void copiedBonesMatchGeckoLibsPose() {
        GeoBone root = bone(null, "root", 3.0F, 5.0F, -2.0F);
        root.updateRotation(0.3F, -0.7F, 0.2F);
        root.updatePosition(4.0F, -1.0F, 2.0F);
        root.updateScale(1.5F, 0.75F, 1.25F);
        root.getCubes().add(cube(-0.5, 0.0, -0.25, 0.5, 1.0, 0.25, new Vec3(-4.0, 8.0, 1.0), new Vec3(0.1, 0.4, -0.6)));

        GeoBone limb = bone(root, "limb", -6.0F, 12.0F, 0.0F);
        limb.updateRotation(1.1F, 0.0F, -0.9F);
        limb.getCubes().add(cube(0.2, 0.5, -0.1, 1.4, 0.9, 0.3, new Vec3(0.0, 10.0, 0.0), Vec3.ZERO));

        GeoBone tip = bone(limb, "tip", 16.0F, 4.0F, 3.0F);
        tip.updateRotation(0.0F, 2.0F, 0.5F);
        tip.getCubes().add(cube(1.0, 0.0, 0.0, 2.5, 0.25, 0.5, new Vec3(12.0, 2.0, 2.0), new Vec3(-0.8, 0.0, 0.3)));

        GeckoGeometryBounds.Bounds expected = posedByGeckoLib(root);
        GeckoGeometryBounds.Bounds actual = GeckoModelBounds.calculateModelBounds(model(root)).orElseThrow();
        assertSame(expected, actual);
    }

    @Test
    @DisplayName("an animated frame left in the shared bones does not move the frame")
    void theRestPoseIsMeasured() {
        GeoBone root = bone(null, "root", 0.0F, 0.0F, 0.0F);
        root.getCubes().add(cube(0.0, 0.0, 0.0, 1.0, 2.0, 1.0, Vec3.ZERO, Vec3.ZERO));
        GeckoGeometryBounds.Bounds atRest = GeckoModelBounds.calculateModelBounds(model(root)).orElseThrow();

        // What GeckoLib's animation processor does on the first frame, then a frame of an idle.
        root.saveInitialSnapshot();
        root.updateRotation(1.3F, 0.4F, -0.2F);
        root.updatePosition(20.0F, 30.0F, -5.0F);
        root.updateScale(3.0F, 3.0F, 3.0F);

        assertSame(atRest, GeckoModelBounds.calculateModelBounds(model(root)).orElseThrow());
    }

    @Test
    @DisplayName("a hidden bone's cubes and the children of a bone hiding them are left out, as the renderer leaves them")
    void hiddenBonesAreLeftOut() {
        GeoBone root = bone(null, "root", 0.0F, 0.0F, 0.0F);
        root.getCubes().add(cube(0.0, 0.0, 0.0, 1.0, 1.0, 1.0, Vec3.ZERO, Vec3.ZERO));
        GeoBone item = bone(root, "held_item", 0.0F, 0.0F, 0.0F);
        item.getCubes().add(cube(5.0, 5.0, 5.0, 6.0, 6.0, 6.0, Vec3.ZERO, Vec3.ZERO));
        item.setHidden(true);

        GeckoGeometryBounds.Bounds bounds = GeckoModelBounds.calculateModelBounds(model(root)).orElseThrow();
        assertEquals(1.0D, bounds.maxX(), EPSILON);
        assertEquals(1.0D, bounds.maxY(), EPSILON);
    }

    private static void assertSame(GeckoGeometryBounds.Bounds expected, GeckoGeometryBounds.Bounds actual) {
        assertEquals(expected.minX(), actual.minX(), EPSILON, "min x: " + expected + " / " + actual);
        assertEquals(expected.minY(), actual.minY(), EPSILON, "min y: " + expected + " / " + actual);
        assertEquals(expected.minZ(), actual.minZ(), EPSILON, "min z: " + expected + " / " + actual);
        assertEquals(expected.maxX(), actual.maxX(), EPSILON, "max x: " + expected + " / " + actual);
        assertEquals(expected.maxY(), actual.maxY(), EPSILON, "max y: " + expected + " / " + actual);
        assertEquals(expected.maxZ(), actual.maxZ(), EPSILON, "max z: " + expected + " / " + actual);
    }

    private static BakedGeoModel model(GeoBone root) {
        ModelProperties properties = new ModelProperties(null, null, null, null, null, null, null, null, null,
                null, "test", null, 64.0D, 64.0D, null, null, null);
        return new BakedGeoModel(List.of(root), properties);
    }

    private static GeoBone bone(GeoBone parent, String name, float pivotX, float pivotY, float pivotZ) {
        GeoBone bone = new GeoBone(parent, name, false, 0.0D, false, false);
        bone.updatePivot(pivotX, pivotY, pivotZ);
        if (parent != null) {
            parent.getChildBones().add(bone);
        }
        return bone;
    }

    /** A cube from its two corners in blocks, with the front and back faces holding all eight. */
    private static GeoCube cube(double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                                Vec3 pivot, Vec3 rotation) {
        GeoVertex[] back = {
                new GeoVertex(minX, minY, minZ), new GeoVertex(maxX, minY, minZ),
                new GeoVertex(maxX, maxY, minZ), new GeoVertex(minX, maxY, minZ)};
        GeoVertex[] front = {
                new GeoVertex(minX, minY, maxZ), new GeoVertex(maxX, minY, maxZ),
                new GeoVertex(maxX, maxY, maxZ), new GeoVertex(minX, maxY, maxZ)};
        GeoQuad[] quads = {
                new GeoQuad(back, new Vector3f(0.0F, 0.0F, -1.0F), Direction.NORTH),
                new GeoQuad(front, new Vector3f(0.0F, 0.0F, 1.0F), Direction.SOUTH)};
        return new GeoCube(quads, pivot, rotation,
                new Vec3((maxX - minX) * 16.0D, (maxY - minY) * 16.0D, (maxZ - minZ) * 16.0D), 0.0D, false);
    }

    /** The box GeckoLib's renderer fills with these bones: RenderUtil's own pose, vertex by vertex. */
    private static GeckoGeometryBounds.Bounds posedByGeckoLib(GeoBone root) {
        double[] box = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        pose(root, new PoseStack(), box);
        return new GeckoGeometryBounds.Bounds(box[0], box[1], box[2], box[3], box[4], box[5]);
    }

    private static void pose(GeoBone bone, PoseStack poseStack, double[] box) {
        poseStack.pushPose();
        RenderUtil.prepMatrixForBone(poseStack, bone);
        for (GeoCube cube : bone.getCubes()) {
            poseStack.pushPose();
            RenderUtil.translateToPivotPoint(poseStack, cube);
            RenderUtil.rotateMatrixAroundCube(poseStack, cube);
            RenderUtil.translateAwayFromPivotPoint(poseStack, cube);
            for (GeoQuad quad : cube.quads()) {
                for (GeoVertex vertex : quad.vertices()) {
                    Vector3f point = poseStack.last().pose().transformPosition(new Vector3f(vertex.position()));
                    box[0] = Math.min(box[0], point.x());
                    box[1] = Math.min(box[1], point.y());
                    box[2] = Math.min(box[2], point.z());
                    box[3] = Math.max(box[3], point.x());
                    box[4] = Math.max(box[4], point.y());
                    box[5] = Math.max(box[5], point.z());
                }
            }
            poseStack.popPose();
        }
        for (GeoBone child : bone.getChildBones()) {
            pose(child, poseStack, box);
        }
        poseStack.popPose();
    }
}

package com.goodbird.cnpcgeckoaddon.client.model;

import com.goodbird.cnpcgeckoaddon.data.GeckoGeometryBounds;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads a baked GeckoLib model into {@link GeckoGeometryBounds} and measures it there.
 *
 * <p>The baked bones are shared by every npc wearing the model, and the last frame that drew
 * one leaves its animated pose in them. The bounds are taken in the pose the model was baked
 * in - the bone's initial snapshot once an animation has touched it - so the frame does not
 * depend on which idle frame happened to be drawn last.</p>
 */
public final class GeckoModelBounds {
    private GeckoModelBounds() {
    }

    public static Optional<GeckoGeometryBounds.Bounds> calculateModelBounds(BakedGeoModel model) {
        if (model == null) {
            return Optional.empty();
        }
        try {
            List<GeckoGeometryBounds.Bone> bones = new ArrayList<>();
            for (GeoBone bone : model.topLevelBones()) {
                if (bone != null) {
                    bones.add(copy(bone));
                }
            }
            return GeckoGeometryBounds.compute(bones);
        } catch (RuntimeException broken) {
            // Broken third-party geometry costs its preview the fit, not the editor.
            return Optional.empty();
        }
    }

    static GeckoGeometryBounds.Bone copy(GeoBone bone) {
        BoneSnapshot rest = bone.getInitialSnapshot();
        List<GeckoGeometryBounds.Cube> cubes = new ArrayList<>();
        // The renderer skips a hidden bone's own cubes and, separately, the children of a bone
        // that hides them; neverRender bones are baked hidden.
        if (!bone.isHidden()) {
            for (GeoCube cube : bone.getCubes()) {
                GeckoGeometryBounds.Cube copied = copy(cube);
                if (copied != null) {
                    cubes.add(copied);
                }
            }
        }
        List<GeckoGeometryBounds.Bone> children = new ArrayList<>();
        if (!bone.isHidingChildren()) {
            for (GeoBone child : bone.getChildBones()) {
                if (child != null) {
                    children.add(copy(child));
                }
            }
        }
        return new GeckoGeometryBounds.Bone(
                bone.getPivotX(), bone.getPivotY(), bone.getPivotZ(),
                rest == null ? bone.getRotX() : rest.getRotX(),
                rest == null ? bone.getRotY() : rest.getRotY(),
                rest == null ? bone.getRotZ() : rest.getRotZ(),
                rest == null ? bone.getScaleX() : rest.getScaleX(),
                rest == null ? bone.getScaleY() : rest.getScaleY(),
                rest == null ? bone.getScaleZ() : rest.getScaleZ(),
                // RenderUtil.translateMatrixToBone moves by -x, y, z.
                -(rest == null ? bone.getPosX() : rest.getOffsetX()),
                rest == null ? bone.getPosY() : rest.getOffsetY(),
                rest == null ? bone.getPosZ() : rest.getOffsetZ(),
                cubes, children);
    }

    /** @return the cube's box, or null for a cube with no vertex to draw */
    static GeckoGeometryBounds.Cube copy(GeoCube cube) {
        if (cube == null || cube.quads() == null) {
            return null;
        }
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (GeoQuad quad : cube.quads()) {
            if (quad == null || quad.vertices() == null) {
                continue;
            }
            for (GeoVertex vertex : quad.vertices()) {
                if (vertex == null) {
                    continue;
                }
                minX = Math.min(minX, vertex.position().x());
                minY = Math.min(minY, vertex.position().y());
                minZ = Math.min(minZ, vertex.position().z());
                maxX = Math.max(maxX, vertex.position().x());
                maxY = Math.max(maxY, vertex.position().y());
                maxZ = Math.max(maxZ, vertex.position().z());
            }
        }
        if (minX > maxX) {
            return null;
        }
        // The baked vertices already stand where the inflate put them, so none is added twice.
        return new GeckoGeometryBounds.Cube(minX, minY, minZ, maxX, maxY, maxZ, 0.0D,
                cube.pivot().x(), cube.pivot().y(), cube.pivot().z(),
                cube.rotation().x(), cube.rotation().y(), cube.rotation().z());
    }
}

package com.goodbird.cnpcgeckoaddon.client.renderer;

import com.goodbird.cnpcgeckoaddon.entity.EntityCustomModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomModelHeldItemLayerTest {
    @Test
    void bothHandsInheritCurrentFramePoseAndEntityScale() {
        RecordingRenderer renderer = new RecordingRenderer();
        GeoBone root = bone(null, "root");
        bone(root, "held_item").setHidden(true);
        bone(root, "left_held_item").setHidden(true);
        List<Matrix4f> first = renderer.draw(new PoseStack(), root);
        assertEquals(2, first.size(), "hidden locator geometry must still render both held items");
        root.setRotY((float) Math.PI / 2);
        PoseStack scaled = new PoseStack();
        scaled.scale(2, 2, 2);
        List<Matrix4f> next = renderer.draw(scaled, root);
        assertEquals(2, next.size());
        for (int i = 0; i < first.size(); i++) {
            Matrix4f expected = new Matrix4f().scaling(2).rotateY((float) Math.PI / 2).mul(first.get(i));
            assertTrue(expected.equals(next.get(i), 0.00001F),
                    "items must inherit this frame's parent rotation and size exactly once");
        }
    }

    @Test
    void locatorRotationIsAppliedOnceAndDoesNotLeakToSibling() {
        RecordingRenderer renderer = new RecordingRenderer();
        GeoBone root = bone(null, "root");
        GeoBone main = bone(root, "held_item");
        bone(root, "left_held_item");
        List<Matrix4f> first = renderer.draw(new PoseStack(), root);
        main.setRotZ((float) Math.PI / 2);
        List<Matrix4f> next = renderer.draw(new PoseStack(), root);
        assertTrue(new Matrix4f().rotateZ((float) Math.PI / 2).mul(first.getFirst())
                .equals(next.getFirst(), 0.00001F));
        assertEquals(first.get(1), next.get(1), "one hand's transform must not move the other");
    }

    @Test
    void failedItemRenderingRestoresTheCallerPose() {
        RecordingRenderer renderer = new RecordingRenderer();
        renderer.layer.fail = true;
        PoseStack pose = new PoseStack();
        pose.translate(2, 3, 4);
        Matrix4f before = new Matrix4f(pose.last().pose());
        assertThrows(IllegalStateException.class, () -> renderer.layer.renderForBone(pose, null,
                bone(null, "held_item"), null, null, null, 0, 0, 0));
        assertEquals(before, pose.last().pose());
        assertTrue(pose.clear(), "the layer must pop its pose even when an item renderer throws");
    }

    private static GeoBone bone(GeoBone parent, String name) {
        GeoBone bone = new GeoBone(parent, name, false, 0.0, false, false);
        if (parent != null) {
            parent.getChildBones().add(bone);
        }
        return bone;
    }

    private static final class RecordingLayer extends CustomModelHeldItemLayer {
        private final List<Matrix4f> poses = new ArrayList<>();
        private boolean fail;

        private RecordingLayer(GeoRenderer<EntityCustomModel> renderer) {
            super(renderer);
        }

        @Override
        protected ItemStack stackForBone(GeoBone bone, EntityCustomModel animatable) {
            return bone.getName().endsWith("held_item") ? new ItemStack(Items.STICK) : ItemStack.EMPTY;
        }

        @Override
        protected void renderStack(PoseStack poseStack, EntityCustomModel animatable, ItemStack stack,
                                   MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
            if (fail) {
                throw new IllegalStateException("fixture render failure");
            }
            poses.add(new Matrix4f(poseStack.last().pose()));
        }
    }

    private static final class RecordingRenderer implements GeoRenderer<EntityCustomModel> {
        private final RecordingLayer layer = new RecordingLayer(this);

        private List<Matrix4f> draw(PoseStack pose, GeoBone root) {
            layer.poses.clear();
            renderRecursively(pose, null, root, null, null, null, false, 0, 0, 0, -1);
            assertTrue(pose.clear());
            return List.copyOf(layer.poses);
        }

        @Override
        public GeoModel<EntityCustomModel> getGeoModel() {
            return null;
        }

        @Override
        public EntityCustomModel getAnimatable() {
            return null;
        }

        @Override
        public List<GeoRenderLayer<EntityCustomModel>> getRenderLayers() {
            return List.of(layer);
        }

        @Override
        public VertexConsumer checkAndRefreshBuffer(boolean isReRender, VertexConsumer buffer,
                                                     MultiBufferSource source, RenderType type) {
            return buffer;
        }

        @Override
        public void fireCompileRenderLayersEvent() {
        }

        @Override
        public boolean firePreRenderEvent(PoseStack pose, BakedGeoModel model, MultiBufferSource source,
                                           float partialTick, int packedLight) {
            return true;
        }

        @Override
        public void firePostRenderEvent(PoseStack pose, BakedGeoModel model, MultiBufferSource source,
                                        float partialTick, int packedLight) {
        }

        @Override
        public void updateAnimatedTextureFrame(EntityCustomModel animatable) {
        }
    }
}

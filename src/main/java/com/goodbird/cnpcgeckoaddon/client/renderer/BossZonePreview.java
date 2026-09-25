package com.goodbird.cnpcgeckoaddon.client.renderer;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.ai.BossTelegraphUtil;
import com.goodbird.cnpcgeckoaddon.client.ZoneSelection;
import com.goodbird.cnpcgeckoaddon.client.ZoneSelectionClient;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.utils.EventGuard;
import com.goodbird.cnpcgeckoaddon.utils.ZoneCoordinates;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Draws a boss' zones and spots into the world for whoever is editing it, and nobody else.
 *
 * <p>Three sources, one look. While any screen of a boss' settings is open - and while its
 * editor has stepped aside for a pick - every zone and spot of that boss is drawn from the
 * settings the screens hold, so an unsaved change shows at once. A "show" button can put one
 * shape up for ten seconds that outlives the screen. And a pick under way draws its own outline
 * from the first corner to the block under the crosshair. Boxes are an outline over a faint
 * fill, spots a marker post with a name over it, circles a ring on the floor, each in the colour
 * of the ability it belongs to; whatever the open editor edits is drawn brighter and thicker.</p>
 *
 * <p>Nothing is sent or saved from here, and the switch for the whole of it is a field of this
 * class: it lasts as long as the game is running and belongs to this client alone.</p>
 */
@EventBusSubscriber(modid = CNPCGeckoAddon.MODID, value = Dist.CLIENT)
public final class BossZonePreview {

    /** Shapes that belong to no ability, next to the abilities' own kinds, which count from 0. */
    public static final int KIND_AGGRO = -1;
    public static final int KIND_TOTEM = -2;
    public static final int KIND_CHEST = -3;
    /** A box whose height lies outside the world, drawn anyway so the stale numbers can be found. */
    public static final int KIND_INVALID = -4;
    /** Anything picked for something that is not a boss. */
    public static final int KIND_PLAIN = -5;

    /** What an editor of a boss-wide shape kept in no settings object of its own names as its focus. */
    public enum Focus {
        AGGRO_ZONE,
        CHEST
    }

    /** How a shape is drawn. */
    public enum Type {
        BOX,
        POINT,
        RING
    }

    /**
     * One thing to draw.
     *
     * @param box    the volume of a box
     * @param point  where a spot stands, or the middle of a ring
     * @param radius how wide a ring is
     * @param focus  whether it is the one being edited, drawn brighter and thicker
     */
    public record Shape(Type type, int kind, String label, AABB box, Vec3 point, double radius, boolean focus) {

        public static Shape box(int kind, String label, AABB box, boolean focus) {
            return new Shape(Type.BOX, kind, label, box, box.getCenter(), 0.0D, focus);
        }

        public static Shape point(int kind, String label, Vec3 point, boolean focus) {
            return new Shape(Type.POINT, kind, label, null, point, 0.0D, focus);
        }

        public static Shape ring(int kind, String label, Vec3 centre, double radius, boolean focus) {
            return new Shape(Type.RING, kind, label, null, centre, radius, focus);
        }
    }

    private record Timed(Shape shape, ClientLevel level, long expiresAt) {
    }

    /** How long a "show" button keeps its shape up: ten seconds. */
    private static final int PREVIEW_TICKS = 200;
    private static final double MARKER_INSET = 0.3D;
    /** A spot's post: how tall, and how far out from its middle. */
    private static final double POST_HEIGHT = 2.0D;
    private static final double POST_HALF_WIDTH = 0.12D;
    /** How far over the floor a ring and a spot's footprint float, so they read as painted on. */
    private static final double FLOOR_LIFT = 0.03D;
    /** Past this no name is drawn: it would be a smudge, and a crowd of them hides the arena. */
    private static final double LABEL_RANGE = 64.0D;
    private static final float LABEL_SCALE = 0.025F;
    private static final float LINE_ALPHA = 0.85F;
    private static final float FILL_ALPHA = 0.10F;
    private static final float POST_FILL_ALPHA = 0.28F;
    private static final float FOCUS_FILL_ALPHA = 0.22F;
    private static final float FOCUS_POST_FILL_ALPHA = 0.45F;
    /** How much nearer white the focus is drawn, and how much wider its lines are. */
    private static final float FOCUS_WHITEN = 0.35F;
    private static final float FOCUS_LINE_SCALE = 2.4F;

    private static final int AGGRO_COLOR = 0x26FF26;
    private static final int TOTEM_COLOR = 0xC0C0FF;
    private static final int CHEST_COLOR = 0xFFD24D;
    private static final int INVALID_COLOR = 0xFF2626;
    private static final int PLAIN_COLOR = 0xFFFFFF;

    private static final List<Timed> TIMED = new ArrayList<>();

    private static boolean shown = true;
    private static List<Shape> live = List.of();
    private static ClientLevel liveLevel;

    /** Built on first use rather than when the class loads, which may happen where nothing draws. */
    private static MultiBufferSource.BufferSource buffers;
    private static RenderType focusLines;

    private BossZonePreview() {
    }

    /** Whether the zones of an open boss are drawn at all. */
    public static boolean isShown() {
        return shown;
    }

    public static void setShown(boolean value) {
        shown = value;
        if (!value) {
            live = List.of();
        }
    }

    /** Puts one box up for ten seconds, whatever is open or closed meanwhile. */
    public static void show(int kind, String label, AABB box) {
        showTimed(Shape.box(kind, label, box, false));
    }

    /** Puts one spot up for ten seconds, whatever is open or closed meanwhile. */
    public static void show(int kind, String label, Vec3 point) {
        showTimed(Shape.point(kind, label, point, false));
    }

    private static void showTimed(Shape shape) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            TIMED.add(new Timed(shape, level, level.getGameTime() + PREVIEW_TICKS));
        }
    }

    /**
     * The aggro zone for ten seconds, the way its screen's show button has always drawn it: the box
     * cut to the world's height, or in red uncut when nothing of it is left, so numbers copied from
     * another dimension can be found and replaced; and a marker in each of the two corners typed.
     */
    public static void showAggroZone(TeleportPathData data) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        BlockPos corner1 = new BlockPos(data.getAggroZoneX1(), data.getAggroZoneY1(), data.getAggroZoneZ1());
        BlockPos corner2 = new BlockPos(data.getAggroZoneX2(), data.getAggroZoneY2(), data.getAggroZoneZ2());
        AABB whole = ZoneCoordinates.blockBox(corner1, corner2);
        AABB cut = ZoneCoordinates.clampToBuildHeight(whole, level.getMinBuildHeight(), level.getMaxBuildHeight());
        String title = I18n.get("cnpcgeckoaddon.boss.aggro_zone_title");
        show(cut == null ? KIND_INVALID : KIND_AGGRO, title, cut == null ? whole : cut);
        show(KIND_AGGRO, "1", marker(corner1));
        show(KIND_AGGRO, "2", marker(corner2));
    }

    private static AABB marker(BlockPos pos) {
        return new AABB(pos.getX() + MARKER_INSET, pos.getY() + MARKER_INSET, pos.getZ() + MARKER_INSET,
                pos.getX() + 1.0D - MARKER_INSET, pos.getY() + 1.0D - MARKER_INSET, pos.getZ() + 1.0D - MARKER_INSET);
    }

    /** The colour a kind of shape is drawn in: an ability's own, or one of the few above. */
    public static int colorOf(int kind) {
        return switch (kind) {
            case KIND_AGGRO -> AGGRO_COLOR;
            case KIND_TOTEM -> TOTEM_COLOR;
            case KIND_CHEST -> CHEST_COLOR;
            case KIND_INVALID -> INVALID_COLOR;
            case KIND_PLAIN -> PLAIN_COLOR;
            default -> BossTelegraphUtil.textColor(kind);
        };
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        EventGuard.handle("client.zone_preview.tick", event, BossZonePreview::handleTick);
    }

    /**
     * Reads the shapes out of the screens once a tick rather than once a frame: they change when
     * somebody types, and a boss with every list full is a few hundred of them.
     */
    private static void handleTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || !shown) {
            live = List.of();
            liveLevel = null;
            return;
        }
        Screen hidden = ZoneSelectionClient.hiddenScreen();
        live = BossZoneShapes.collect(hidden != null ? hidden : minecraft.screen, level);
        liveLevel = level;
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        EventGuard.handle("client.zone_preview.logout", event, BossZonePreview::handleLogout);
    }

    private static void handleLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        TIMED.clear();
        live = List.of();
        liveLevel = null;
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        EventGuard.handle("client.zone_preview.render", event, BossZonePreview::handleRender);
    }

    private static void handleRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            TIMED.clear();
            return;
        }
        List<Shape> shapes = new ArrayList<>();
        long gameTime = level.getGameTime();
        Iterator<Timed> timed = TIMED.iterator();
        while (timed.hasNext()) {
            Timed next = timed.next();
            // The level object changes on a dimension transfer, which keeps one world's
            // coordinates from being drawn in the next.
            if (next.level() != level || gameTime >= next.expiresAt()) {
                timed.remove();
            } else {
                shapes.add(next.shape());
            }
        }
        if (liveLevel == level) {
            shapes.addAll(live);
        }
        addPickShapes(shapes, event.getPartialTick().getGameTimeDeltaPartialTick(false));
        if (shapes.isEmpty()) {
            return;
        }
        draw(event, shapes);
    }

    /**
     * The pick under way: the block the first click would make a corner, the box from the first
     * corner to the one the next click would make after it - by the rule the clicks go by, so
     * what is outlined is what will be written - and for a point the post on the block its click
     * would write. Measured as it goes - the box's size, the spot's block - so the builder can
     * count without the fields.
     */
    private static void addPickShapes(List<Shape> shapes, float partialTick) {
        ZoneSelection selection = ZoneSelectionClient.selection();
        if (selection == null) {
            return;
        }
        int kind = ZoneSelectionClient.kind();
        BlockHitResult aim = ZoneSelectionClient.aimedBlock(partialTick);
        BlockPos aimed = aim == null ? null : aim.getBlockPos();
        Direction face = aim == null ? null : aim.getDirection();
        if (selection.isPoint()) {
            if (aim != null) {
                // On the block the editor writes, which is not always the one in front of the face.
                BlockPos spot = selection.picked(aimed, face);
                shapes.add(Shape.box(kind, "", ZoneCoordinates.blockBox(aimed, aimed), true));
                shapes.add(Shape.point(kind, spot.getX() + " " + spot.getY() + " " + spot.getZ(),
                        Vec3.atBottomCenterOf(spot), true));
            }
            return;
        }
        ZoneSelection.Box box = selection.liveBox(aimed, face);
        if (box == null) {
            if (aimed != null) {
                BlockPos corner = selection.picked(aimed, face);
                shapes.add(Shape.box(kind, corner.getX() + " " + corner.getY() + " " + corner.getZ(),
                        ZoneCoordinates.blockBox(corner, corner), true));
            }
            return;
        }
        BlockPos size = box.max().subtract(box.min()).offset(1, 1, 1);
        shapes.add(Shape.box(kind, size.getX() + " × " + size.getY() + " × " + size.getZ(),
                ZoneCoordinates.blockBox(box.min(), box.max()), true));
        shapes.add(Shape.box(kind, "1", marker(selection.firstCorner()), true));
    }

    private static void draw(RenderLevelStageEvent event, List<Shape> shapes) {
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        MultiBufferSource.BufferSource source = buffers();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        // Popped in a finally: the level renderer throws on a pose stack left unbalanced, so a
        // draw that fails behind the guard must still hand the stack back the way it got it.
        try {
            poseStack.translate(-eye.x, -eye.y, -eye.z);

            // Grouped by how they are drawn: this buffer source draws a batch as soon as the next
            // one of another kind is asked for.
            RenderType thin = RenderType.lines();
            VertexConsumer lines = source.getBuffer(thin);
            for (Shape shape : shapes) {
                if (!shape.focus()) {
                    outline(poseStack, lines, shape, colorOf(shape.kind()), LINE_ALPHA);
                }
            }
            source.endBatch(thin);

            RenderType thick = focusLines();
            VertexConsumer focus = source.getBuffer(thick);
            for (Shape shape : shapes) {
                if (shape.focus()) {
                    outline(poseStack, focus, shape, whiten(colorOf(shape.kind())), 1.0F);
                }
            }
            source.endBatch(thick);

            RenderType quads = RenderType.debugQuads();
            VertexConsumer fill = source.getBuffer(quads);
            for (Shape shape : shapes) {
                fill(poseStack.last(), fill, shape);
            }
            source.endBatch(quads);

            Font font = minecraft.font;
            for (Shape shape : shapes) {
                Vec3 at = labelSpot(shape);
                if (shape.label() == null || shape.label().isEmpty() || at.distanceToSqr(eye) > LABEL_RANGE * LABEL_RANGE) {
                    continue;
                }
                int rgb = shape.focus() ? whiten(colorOf(shape.kind())) : colorOf(shape.kind());
                label(poseStack, source, font, camera, at, shape.label(), rgb, shape.focus());
            }
            source.endBatch();
        } finally {
            poseStack.popPose();
        }
    }

    private static void outline(PoseStack poseStack, VertexConsumer consumer, Shape shape, int rgb, float alpha) {
        float r = (rgb >> 16 & 0xFF) / 255.0F;
        float g = (rgb >> 8 & 0xFF) / 255.0F;
        float b = (rgb & 0xFF) / 255.0F;
        switch (shape.type()) {
            case BOX -> LevelRenderer.renderLineBox(poseStack, consumer, shape.box(), r, g, b, alpha);
            case POINT -> {
                LevelRenderer.renderLineBox(poseStack, consumer, post(shape.point()), r, g, b, alpha);
                LevelRenderer.renderLineBox(poseStack, consumer, footprint(shape.point()), r, g, b, alpha);
            }
            case RING -> ring(poseStack.last(), consumer, shape.point(), shape.radius(), r, g, b, alpha);
        }
    }

    /** A circle of short straight lines, as fine as its size needs and no finer. */
    private static void ring(PoseStack.Pose pose, VertexConsumer consumer, Vec3 centre, double radius,
                             float r, float g, float b, float alpha) {
        if (radius <= 0.0D) {
            return;
        }
        int segments = Mth.clamp((int) Math.ceil(radius * 4.0D), 24, 256);
        double y = centre.y + FLOOR_LIFT;
        for (int i = 0; i < segments; i++) {
            double a0 = Mth.TWO_PI * i / segments;
            double a1 = Mth.TWO_PI * (i + 1) / segments;
            float x0 = (float) (centre.x + Math.cos(a0) * radius);
            float z0 = (float) (centre.z + Math.sin(a0) * radius);
            float x1 = (float) (centre.x + Math.cos(a1) * radius);
            float z1 = (float) (centre.z + Math.sin(a1) * radius);
            float dx = x1 - x0;
            float dz = z1 - z0;
            float length = Mth.sqrt(dx * dx + dz * dz);
            if (length <= 0.0F) {
                continue;
            }
            // The line shader widens a line across its normal, which for a line is its own direction.
            consumer.addVertex(pose, x0, (float) y, z0).setColor(r, g, b, alpha)
                    .setNormal(pose, dx / length, 0.0F, dz / length);
            consumer.addVertex(pose, x1, (float) y, z1).setColor(r, g, b, alpha)
                    .setNormal(pose, dx / length, 0.0F, dz / length);
        }
    }

    private static void fill(PoseStack.Pose pose, VertexConsumer consumer, Shape shape) {
        int rgb = shape.focus() ? whiten(colorOf(shape.kind())) : colorOf(shape.kind());
        switch (shape.type()) {
            case BOX -> faces(pose, consumer, shape.box(), rgb, shape.focus() ? FOCUS_FILL_ALPHA : FILL_ALPHA);
            case POINT -> faces(pose, consumer, post(shape.point()), rgb,
                    shape.focus() ? FOCUS_POST_FILL_ALPHA : POST_FILL_ALPHA);
            case RING -> {
                // A ring is its line alone: a filled disc the size of an arena hides the arena.
            }
        }
    }

    /** The six faces of a box, faint enough to see the arena through. */
    private static void faces(PoseStack.Pose pose, VertexConsumer consumer, AABB box, int rgb, float alpha) {
        int color = (Mth.clamp((int) (alpha * 255.0F), 0, 255) << 24) | (rgb & 0xFFFFFF);
        float x0 = (float) box.minX;
        float y0 = (float) box.minY;
        float z0 = (float) box.minZ;
        float x1 = (float) box.maxX;
        float y1 = (float) box.maxY;
        float z1 = (float) box.maxZ;
        quad(pose, consumer, color, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        quad(pose, consumer, color, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        quad(pose, consumer, color, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
        quad(pose, consumer, color, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(pose, consumer, color, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        quad(pose, consumer, color, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer consumer, int color,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        consumer.addVertex(pose, ax, ay, az).setColor(color);
        consumer.addVertex(pose, bx, by, bz).setColor(color);
        consumer.addVertex(pose, cx, cy, cz).setColor(color);
        consumer.addVertex(pose, dx, dy, dz).setColor(color);
    }

    /** A spot's post: thin, standing on the spot, tall enough to be found across an arena. */
    private static AABB post(Vec3 at) {
        return new AABB(at.x - POST_HALF_WIDTH, at.y, at.z - POST_HALF_WIDTH,
                at.x + POST_HALF_WIDTH, at.y + POST_HEIGHT, at.z + POST_HALF_WIDTH);
    }

    /** The block a spot stands in, outlined on the floor so which block it is can be read. */
    private static AABB footprint(Vec3 at) {
        double x = Math.floor(at.x);
        double z = Math.floor(at.z);
        return new AABB(x, at.y + FLOOR_LIFT, z, x + 1.0D, at.y + FLOOR_LIFT, z + 1.0D);
    }

    private static Vec3 labelSpot(Shape shape) {
        return switch (shape.type()) {
            case BOX -> new Vec3((shape.box().minX + shape.box().maxX) / 2.0D, shape.box().maxY + 0.4D,
                    (shape.box().minZ + shape.box().maxZ) / 2.0D);
            case POINT -> shape.point().add(0.0D, POST_HEIGHT + 0.4D, 0.0D);
            // On the rim, not the middle: the middle of a ring is usually where the boss stands,
            // name and all.
            case RING -> shape.point().add(0.0D, 0.6D, -shape.radius());
        };
    }

    /**
     * A name over a shape, turned to face the camera and legible through the walls, like a name
     * tag. Shadowed rather than set on a plate: drawn through the walls nothing is depth-tested,
     * so the order decides what is on top, and a font draws its shadow before its letters but
     * its plate after them.
     */
    private static void label(PoseStack poseStack, MultiBufferSource source, Font font, Camera camera, Vec3 at,
                              String text, int rgb, boolean focus) {
        poseStack.pushPose();
        try {
            poseStack.translate(at.x, at.y, at.z);
            poseStack.mulPose(camera.rotation());
            float scale = focus ? LABEL_SCALE * 1.25F : LABEL_SCALE;
            poseStack.scale(scale, -scale, scale);
            Matrix4f matrix = poseStack.last().pose();
            font.drawInBatch(text, -font.width(text) / 2.0F, 0.0F, 0xFF000000 | rgb, true, matrix, source,
                    Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
        } finally {
            poseStack.popPose();
        }
    }

    /** A colour a share of the way to white: the focus, brighter than everything around it. */
    private static int whiten(int rgb) {
        int r = rgb >> 16 & 0xFF;
        int g = rgb >> 8 & 0xFF;
        int b = rgb & 0xFF;
        r += Math.round((255 - r) * FOCUS_WHITEN);
        g += Math.round((255 - g) * FOCUS_WHITEN);
        b += Math.round((255 - b) * FOCUS_WHITEN);
        return r << 16 | g << 8 | b;
    }

    private static MultiBufferSource.BufferSource buffers() {
        if (buffers == null) {
            // A buffer of its own, so flushing it can never draw a batch somebody else left open.
            buffers = MultiBufferSource.immediate(new ByteBufferBuilder(RenderType.TRANSIENT_BUFFER_SIZE));
        }
        return buffers;
    }

    /**
     * The game's line, drawn wider: the lines shader takes its width from the render system, and
     * the stock line type sets it from the window alone, so the focus sets it again after that.
     */
    private static RenderType focusLines() {
        if (focusLines == null) {
            RenderType base = RenderType.lines();
            focusLines = new RenderType(CNPCGeckoAddon.MODID + "_zone_focus_lines", DefaultVertexFormat.POSITION_COLOR_NORMAL,
                    VertexFormat.Mode.LINES, RenderType.TRANSIENT_BUFFER_SIZE, false, false,
                    () -> {
                        base.setupRenderState();
                        RenderSystem.lineWidth(Math.max(2.5F,
                                Minecraft.getInstance().getWindow().getWidth() / 1920.0F * 2.5F) * FOCUS_LINE_SCALE);
                    },
                    base::clearRenderState) {
            };
        }
        return focusLines;
    }
}

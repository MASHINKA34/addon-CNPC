package com.goodbird.cnpcgeckoaddon.client.renderer;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.data.TelegraphLineStyles;
import com.goodbird.cnpcgeckoaddon.network.BossTelegraphClientBridge;
import com.goodbird.cnpcgeckoaddon.utils.BossFloorUtil;
import com.goodbird.cnpcgeckoaddon.utils.TelegraphLineGeometry;
import com.goodbird.cnpcgeckoaddon.utils.TelegraphShape;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Draws the bands a boss warns with, out of the frames the server sends.
 *
 * <p>Decides nothing. It holds one frame per boss and channel, lays every point of it on the
 * floor the same search the dust uses finds, and draws what it was told to draw until the
 * frame runs out. No timers of the fight are kept here and nothing is guessed about what the
 * boss will do next: a frame that stops arriving simply goes out.</p>
 */
@EventBusSubscriber(modid = CNPCGeckoAddon.MODID, value = Dist.CLIENT)
public final class BossTelegraphRenderer {

    /** How far above the block a band floats, so it reads as painted on rather than buried. */
    private static final double FLOOR_LIFT = 0.03D;
    /** What a band drawn in full colour keeps, and what the softer half of a shape keeps. */
    private static final float BRIGHT_ALPHA = 0.90F;
    private static final float FADED_ALPHA = 0.45F;
    /** A flood asked for by the motion alone, with no depth of its own set on the boss. */
    private static final float DEFAULT_FILL_ALPHA = 0.40F;
    /** The glow's outer haze, as a share of what its core is drawn at. */
    private static final float GLOW_HAZE_SHARE = 1.0F / 3.0F;
    private static final double GLOW_HAZE_WIDTHS = 2.5D;
    private static final double GLOW_CORE_WIDTHS = 0.6D;
    /** The two bands of the double line: a third of the width each, half a width apart. */
    private static final double DOUBLE_WIDTHS = 1.0D / 3.0D;
    private static final double DOUBLE_OFFSET_WIDTHS = 0.5D;
    /**
     * How long a wind-up is taken to be until two frames of one have been seen. Only the
     * pulse asks, and only to space its blinks, so the default warning time is close enough
     * for the one frame it is used on.
     */
    private static final float ASSUMED_WIND_UP_TICKS = TeleportPathData.DEFAULT_TELEGRAPH_LEAD_TICKS;

    private static final Map<Long, Frame> FRAMES = new HashMap<>();

    static {
        BossTelegraphClientBridge.setHandler(BossTelegraphRenderer::accept);
    }

    private BossTelegraphRenderer() {
    }

    /** One run of a contour that really has floor under all of it, ready to be drawn. */
    private record Band(List<Vec3> points, double[] reached, double length, int rgb,
                        boolean faded, boolean flat) {
    }

    /** One quad of a shape's inside, on the floor, with how far out of the shape it sits. */
    private record FillQuad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, double reach, int rgb, boolean faded) {
    }

    private static final class Frame {
        private final String styleId;
        private final int widthTenths;
        private final int motion;
        private final int fillPercent;
        private final float progress;
        private final long receivedAt;
        private final long expiresAt;
        private final List<TelegraphShape> shapes;
        /** How fast the wind-up was running when this arrived, judged against the frame before. */
        private final float progressPerTick;

        private ClientLevel builtFor;
        private List<Band> bands = List.of();
        private List<FillQuad> fill = List.of();

        private Frame(String styleId, int widthTenths, int motion, int fillPercent, float progress,
                      long receivedAt, int ttlTicks, List<TelegraphShape> shapes, float progressPerTick) {
            this.styleId = styleId;
            this.widthTenths = widthTenths;
            this.motion = motion;
            this.fillPercent = fillPercent;
            this.progress = progress;
            this.receivedAt = receivedAt;
            this.expiresAt = receivedAt + ttlTicks;
            this.shapes = shapes;
            this.progressPerTick = progressPerTick;
        }
    }

    /** One key per boss and channel: a new frame replaces the whole of the last one. */
    private static long keyOf(int ownerId, byte channel) {
        return ((long) ownerId << 8) | (channel & 0xFFL);
    }

    public static void accept(int ownerId, byte channel, String styleId, int widthTenths,
                              int motion, int fillPercent, float progress, int ttlTicks,
                              List<TelegraphShape> shapes) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            FRAMES.clear();
            return;
        }
        long key = keyOf(ownerId, channel);
        long now = level.getGameTime();
        FRAMES.put(key, new Frame(styleId, widthTenths, motion, fillPercent, progress, now,
                ttlTicks, List.copyOf(shapes), rateOf(FRAMES.get(key), progress, now)));
    }

    /**
     * How much of the wind-up one tick covers, taken from the two frames of it seen so far.
     *
     * <p>Frames arrive every other tick and the screen is drawn far more often than that, so
     * a flood or a contour drawing itself would step rather than move if it only ever knew
     * the progress of the last frame. Two frames say how fast it is going, which is enough to
     * carry it smoothly to the next one.</p>
     */
    private static float rateOf(Frame previous, float progress, long now) {
        if (previous == null || previous.progress < 0.0F || progress < previous.progress
                || now <= previous.receivedAt) {
            return 0.0F;
        }
        return (progress - previous.progress) / (now - previous.receivedAt);
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        FRAMES.clear();
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                || FRAMES.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            FRAMES.clear();
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long gameTime = level.getGameTime();
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        RenderType quads = RenderType.debugQuads();
        VertexConsumer consumer = buffers.getBuffer(quads);
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        Iterator<Frame> frames = FRAMES.values().iterator();
        while (frames.hasNext()) {
            Frame frame = frames.next();
            if (gameTime >= frame.expiresAt) {
                frames.remove();
                continue;
            }
            // The level object changes on a dimension transfer, which is what stops the
            // coordinates of the arena left behind from being drawn in the next world.
            if (frame.builtFor != level) {
                build(frame, level);
            }
            draw(frame, poseStack, consumer, camera, gameTime, partialTick);
        }

        poseStack.popPose();
        // This batch belongs solely to the warnings; flushing it keeps their vertices out of
        // whatever the game draws next.
        buffers.endBatch(quads);
    }

    /**
     * Lays every point of a frame on the floor under it, once.
     *
     * <p>A frame lives two ticks and is drawn on every one of the screen's own frames in
     * between, and the floor search is a walk down through the blocks. Doing it per shape per
     * screen frame is what would turn a wide ring into a stutter, so it is done when the frame
     * arrives and only the drawing is repeated.</p>
     */
    private static void build(Frame frame, ClientLevel level) {
        List<Band> bands = new ArrayList<>();
        List<FillQuad> fill = new ArrayList<>();
        boolean floods = frame.fillPercent > 0
                || frame.motion == TeleportPathData.TELEGRAPH_MOTION_FILL;
        for (TelegraphShape shape : frame.shapes) {
            boolean flat = shape.kind() != TelegraphShape.KIND_LINK;
            for (TelegraphLineGeometry.Run run : TelegraphLineGeometry.contours(shape)) {
                if (flat) {
                    addFlatBands(bands, level, run, shape);
                } else {
                    addBand(bands, run.points(), shape.rgb(), run.faded(), false);
                }
            }
            if (!floods || !flat) {
                continue;
            }
            for (TelegraphLineGeometry.Cell cell : TelegraphLineGeometry.fill(shape)) {
                Vec3 a = onFloor(level, cell.a());
                Vec3 b = onFloor(level, cell.b());
                Vec3 c = onFloor(level, cell.c());
                Vec3 d = onFloor(level, cell.d());
                // A quad with a corner over a hole is dropped whole, the way a point of the
                // dust over one was simply never emitted.
                if (a != null && b != null && c != null && d != null) {
                    fill.add(new FillQuad(a, b, c, d, cell.reach(), shape.rgb(), shape.faded()));
                }
            }
        }
        frame.bands = bands;
        frame.fill = fill;
        frame.builtFor = level;
    }

    /** Splits one contour into the stretches of it that have floor, dropping the holes. */
    private static void addFlatBands(List<Band> bands, ClientLevel level,
                                     TelegraphLineGeometry.Run run, TelegraphShape shape) {
        List<Vec3> piece = new ArrayList<>();
        for (Vec3 point : run.points()) {
            Vec3 grounded = onFloor(level, point);
            if (grounded == null) {
                addBand(bands, piece, shape.rgb(), run.faded(), true);
                piece = new ArrayList<>();
                continue;
            }
            piece.add(grounded);
        }
        addBand(bands, piece, shape.rgb(), run.faded(), true);
    }

    private static void addBand(List<Band> bands, List<Vec3> points, int rgb, boolean faded,
                                boolean flat) {
        if (points.size() < 2) {
            return;
        }
        double[] reached = new double[points.size()];
        for (int i = 1; i < points.size(); i++) {
            reached[i] = reached[i - 1] + points.get(i).distanceTo(points.get(i - 1));
        }
        double length = reached[reached.length - 1];
        if (length <= 0.0D) {
            return;
        }
        bands.add(new Band(List.copyOf(points), reached, length, rgb, faded, flat));
    }

    private static Vec3 onFloor(ClientLevel level, Vec3 point) {
        BlockPos floor = BossFloorUtil.findFloor(level, point.x, point.y, point.z);
        return floor == null ? null : new Vec3(point.x, floor.getY() + 1.0D + FLOOR_LIFT, point.z);
    }

    private static void draw(Frame frame, PoseStack poseStack, VertexConsumer consumer,
                             Vec3 camera, long gameTime, float partialTick) {
        float progress = liveProgress(frame, gameTime, partialTick);
        float motionAlpha = motionAlpha(frame, progress, gameTime + partialTick);
        double width = frame.widthTenths / 10.0D;
        double traced = TelegraphLineGeometry.reach(
                frame.motion == TeleportPathData.TELEGRAPH_MOTION_TRACE ? progress : -1.0F);
        PoseStack.Pose pose = poseStack.last();

        if (!frame.fill.isEmpty()) {
            float reach = (float) TelegraphLineGeometry.reach(
                    frame.motion == TeleportPathData.TELEGRAPH_MOTION_FILL ? progress : -1.0F);
            float fillAlpha = frame.fillPercent > 0
                    ? frame.fillPercent / 100.0F : DEFAULT_FILL_ALPHA;
            for (FillQuad quad : frame.fill) {
                if (quad.reach() > reach) {
                    continue;
                }
                quad(pose, consumer, quad.a(), quad.b(), quad.c(), quad.d(),
                        quad.rgb(), fillAlpha * (quad.faded() ? FADED_ALPHA / BRIGHT_ALPHA : 1.0F));
            }
        }

        for (Band band : frame.bands) {
            float alpha = (band.faded() ? FADED_ALPHA : BRIGHT_ALPHA) * motionAlpha;
            List<TelegraphLineGeometry.Span> spans = spansOf(frame.styleId, band.length());
            if (traced < 1.0D) {
                spans = TelegraphLineGeometry.clip(spans, band.length() * traced);
            }
            if (spans.isEmpty()) {
                continue;
            }
            switch (frame.styleId) {
                case TelegraphLineStyles.DOUBLE -> {
                    double half = width * DOUBLE_WIDTHS * 0.5D;
                    double apart = width * DOUBLE_OFFSET_WIDTHS;
                    stripe(pose, consumer, camera, band, spans, half, -apart, alpha);
                    stripe(pose, consumer, camera, band, spans, half, apart, alpha);
                }
                case TelegraphLineStyles.GLOW -> {
                    stripe(pose, consumer, camera, band, spans, width * GLOW_HAZE_WIDTHS * 0.5D,
                            0.0D, alpha * GLOW_HAZE_SHARE);
                    stripe(pose, consumer, camera, band, spans, width * GLOW_CORE_WIDTHS * 0.5D,
                            0.0D, alpha);
                }
                default -> stripe(pose, consumer, camera, band, spans, width * 0.5D, 0.0D, alpha);
            }
        }
    }

    /** Where the wind-up has got by now, carried on from the last frame at the rate it was running. */
    private static float liveProgress(Frame frame, long gameTime, float partialTick) {
        if (frame.progress < 0.0F) {
            return -1.0F;
        }
        float elapsed = gameTime + partialTick - frame.receivedAt;
        return Mth.clamp(frame.progress + frame.progressPerTick * elapsed, 0.0F, 1.0F);
    }

    private static float motionAlpha(Frame frame, float progress, float ticks) {
        return switch (frame.motion) {
            case TeleportPathData.TELEGRAPH_MOTION_FADE -> TelegraphLineGeometry.fadeAlpha(progress);
            case TeleportPathData.TELEGRAPH_MOTION_PULSE -> TelegraphLineGeometry.pulseAlpha(
                    TelegraphLineGeometry.pulsePhase(progress, windUpTicks(frame), ticks));
            default -> 1.0F;
        };
    }

    private static float windUpTicks(Frame frame) {
        return frame.progressPerTick > 0.0F ? 1.0F / frame.progressPerTick : ASSUMED_WIND_UP_TICKS;
    }

    private static List<TelegraphLineGeometry.Span> spansOf(String styleId, double length) {
        return switch (styleId) {
            case TelegraphLineStyles.DASHED -> TelegraphLineGeometry.dashes(length,
                    TelegraphLineGeometry.DASH_LENGTH, TelegraphLineGeometry.DASH_GAP);
            case TelegraphLineStyles.DOTTED -> TelegraphLineGeometry.dashes(length,
                    TelegraphLineGeometry.DOT_LENGTH, TelegraphLineGeometry.DOT_GAP);
            default -> List.of(new TelegraphLineGeometry.Span(0.0D, length));
        };
    }

    /**
     * One stripe of a band: the stretches of it a style leaves drawn, laid out across the run
     * at {@code halfWidth} and shifted {@code offset} to one side of it.
     *
     * <p>Walked once with the stretches and the segments in step rather than segment by
     * stretch: a dotted ring sixty blocks across is three hundred of each, and the two lists
     * are both in order, so there is no reason to pay for their product.</p>
     */
    private static void stripe(PoseStack.Pose pose, VertexConsumer consumer, Vec3 camera,
                               Band band, List<TelegraphLineGeometry.Span> spans,
                               double halfWidth, double offset, float alpha) {
        if (alpha <= 0.0F || halfWidth <= 0.0D) {
            return;
        }
        List<Vec3> points = band.points();
        int segment = 0;
        for (TelegraphLineGeometry.Span span : spans) {
            while (segment < points.size() - 1 && band.reached()[segment + 1] <= span.from()) {
                segment++;
            }
            for (int i = segment; i < points.size() - 1; i++) {
                double from = band.reached()[i];
                double to = band.reached()[i + 1];
                if (from >= span.to()) {
                    break;
                }
                double start = Math.max(from, span.from());
                double end = Math.min(to, span.to());
                if (end <= start || to <= from) {
                    continue;
                }
                Vec3 head = points.get(i);
                Vec3 tail = points.get(i + 1);
                Vec3 step = tail.subtract(head);
                Vec3 first = head.add(step.scale((start - from) / (to - from)));
                Vec3 second = head.add(step.scale((end - from) / (to - from)));
                Vec3 across = band.flat()
                        ? flatAcross(step) : billboardAcross(step, camera, first);
                if (across == null) {
                    continue;
                }
                Vec3 shift = across.scale(offset);
                Vec3 edge = across.scale(halfWidth);
                quad(pose, consumer,
                        first.add(shift).subtract(edge), second.add(shift).subtract(edge),
                        second.add(shift).add(edge), first.add(shift).add(edge),
                        band.rgb(), alpha);
            }
        }
    }

    /** A quarter turn of the step, which is what a band lying on the floor is measured across. */
    private static Vec3 flatAcross(Vec3 step) {
        double length = Math.sqrt(step.x * step.x + step.z * step.z);
        return length < 1.0E-6D ? null : new Vec3(step.z / length, 0.0D, -step.x / length);
    }

    /** And for the run from the boss to its target, which hangs in the air: turned to the eye. */
    private static Vec3 billboardAcross(Vec3 step, Vec3 camera, Vec3 at) {
        Vec3 toCamera = camera.subtract(at);
        Vec3 across = step.cross(toCamera);
        return across.lengthSqr() < 1.0E-9D ? null : across.normalize();
    }

    /**
     * One flat quad in the ability's colour. Wound once and never twice: the render type it
     * goes into does not cull, so a band on the floor is there from underneath as well.
     */
    private static void quad(PoseStack.Pose pose, VertexConsumer consumer, Vec3 a, Vec3 b,
                             Vec3 c, Vec3 d, int rgb, float alpha) {
        int color = (Mth.clamp((int) (alpha * 255.0F), 0, 255) << 24) | (rgb & 0xFFFFFF);
        vertex(pose, consumer, a, color);
        vertex(pose, consumer, b, color);
        vertex(pose, consumer, c, color);
        vertex(pose, consumer, d, color);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer consumer, Vec3 at, int color) {
        consumer.addVertex(pose, (float) at.x, (float) at.y, (float) at.z).setColor(color);
    }
}

package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.network.NetworkWrapper;
import com.goodbird.cnpcgeckoaddon.network.PacketSyncBossTelegraph;
import com.goodbird.cnpcgeckoaddon.utils.TelegraphShape;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gathers the shapes a tick drew into one frame per boss and channel, and sends them.
 *
 * <p>A shape drawn as a band is not a particle and cannot go out on its own: a ring and the
 * lane beside it arriving as two packets would be drawn as two warnings, each replacing the
 * other, and the whole tick would flicker. So a tick's worth of one boss' shapes is collected
 * here and goes out together at the end of it, as one frame that replaces whatever the client
 * held for that boss and that channel.</p>
 *
 * <p>A frame with nothing in it is how a warning is put out at once - the flashing edge of a
 * hazard, a platform's outline between blinks - rather than left up until it runs out.</p>
 *
 * <p>Nothing here is saved, and nothing is cleaned up when a boss dies: a frame lives three
 * ticks, so a fight that ends takes its bands with it.</p>
 */
public final class BossTelegraphFrames {

    private record Key(ResourceKey<Level> dimension, int ownerId, byte channel) {
    }

    private static final class Frame {
        private final BossTelegraphPaint paint;
        private final List<TelegraphShape> shapes = new ArrayList<>();

        private Frame(BossTelegraphPaint paint) {
            this.paint = paint;
        }
    }

    /** In the order they were first drawn, so one boss' channels go out the same way each tick. */
    private static final Map<Key, Frame> FRAMES = new LinkedHashMap<>();

    private BossTelegraphFrames() {
    }

    /**
     * Adds one figure to the frame this paint belongs to.
     *
     * <p>Past the frame's ceiling the rest of the tick's shapes are dropped rather than
     * queued: a warning nobody can read through is worth no more than the packet it costs,
     * and the ceiling is well above anything an ability really draws.</p>
     */
    public static void add(ServerLevel level, BossTelegraphPaint paint, TelegraphShape shape) {
        Frame frame = frameFor(level, paint);
        if (frame.shapes.size() < PacketSyncBossTelegraph.MAX_SHAPES) {
            frame.shapes.add(shape);
        }
    }

    /** Opens an empty frame for a channel: what puts its warning out on this very tick. */
    public static void blank(ServerLevel level, BossTelegraphPaint paint) {
        frameFor(level, paint);
    }

    /**
     * The frame of this tick for that boss and channel.
     *
     * <p>The paint of whoever opened it is the one the frame keeps. Two things drawing on one
     * channel in one tick - a wind-up starting while a series of cones is still swinging - are
     * one warning as far as the client is concerned, and the first of them is as good a
     * description of it as the second.</p>
     */
    private static Frame frameFor(ServerLevel level, BossTelegraphPaint paint) {
        return FRAMES.computeIfAbsent(
                new Key(level.dimension(), paint.ownerId(), paint.channel()),
                key -> new Frame(paint));
    }

    public static boolean hasPending() {
        return !FRAMES.isEmpty();
    }

    /** Sends this level's frames and empties them; whatever is not redrawn next tick is over. */
    public static void tick(ServerLevel level) {
        Iterator<Map.Entry<Key, Frame>> frames = FRAMES.entrySet().iterator();
        while (frames.hasNext()) {
            Map.Entry<Key, Frame> entry = frames.next();
            if (!entry.getKey().dimension().equals(level.dimension())) {
                continue;
            }
            frames.remove();
            send(level, entry.getKey(), entry.getValue());
        }
    }

    /** Drops anything a level that is going away had drawn but not yet sent. */
    public static void clear(ServerLevel level) {
        FRAMES.keySet().removeIf(key -> key.dimension().equals(level.dimension()));
    }

    private static void send(ServerLevel level, Key key, Frame frame) {
        Entity owner = level.getEntity(key.ownerId());
        if (owner == null) {
            return;
        }
        Vec3 from = owner.position();
        // The shapes' own spread is added to the audience range: a mark rides its victim
        // across the arena and a hazard's edge is by nature nowhere near its middle, so
        // measuring only to the boss would leave the people standing on it in the dark.
        double audience = BossTelegraphUtil.audienceRange(owner);
        double range = audience;
        for (TelegraphShape shape : frame.shapes) {
            range = Math.max(range, audience + shape.reachFrom(from));
        }
        double rangeSquared = range * range;
        BossTelegraphPaint paint = frame.paint;
        BossTelegraphPaint.Settings settings = paint.settings();
        PacketSyncBossTelegraph packet = new PacketSyncBossTelegraph(key.ownerId(), key.channel(),
                settings.style(), settings.widthTenths(), settings.motion(),
                settings.fillPercent(), paint.progress(), paint.ttlTicks(), frame.shapes);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(from) <= rangeSquared) {
                NetworkWrapper.send(player, packet);
            }
        }
    }
}

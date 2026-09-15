package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.TelegraphLineStyles;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.mixin.IBossController;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.Entity;
import noppes.npcs.entity.EntityNPCInterface;

/**
 * What a boss is warning with, handed to the shapes instead of a particle.
 *
 * <p>Every mark on the arena floor used to be told which dust to use, which is all a shape
 * needs while the server is the one drawing it. A drawn band is not the server's to draw:
 * the client has to be told whose warning it is, which of the boss' clocks it belongs to,
 * what colour it is and how far the wind-up behind it has got. That is this - one object in
 * the place the dust used to sit, so the shape code reads the same either way.</p>
 */
public final class BossTelegraphPaint {

    /** The wind-up, and the cone a series is about to swing next. */
    public static final byte CHANNEL_CAST = 0;
    public static final byte CHANNEL_HAZARD = 1;
    public static final byte CHANNEL_PLATFORM = 2;
    public static final byte CHANNEL_GEYSER = 3;
    public static final byte CHANNEL_MARK = 4;
    public static final byte CHANNEL_BOULDER_RAIN = 5;
    public static final byte CHANNEL_GRAVITY = 6;
    /** The rings of a seismic series, outlined before each one hits. */
    public static final byte CHANNEL_SEISMIC = 7;

    /** A warning with nothing to count towards: an open hazard, a platform already alight. */
    public static final float NO_END = -1.0F;


    /**
     * The boss' own settings for drawn warnings, taken once.
     *
     * <p>Held apart from the paint because the schedulers take theirs on the cast and keep
     * it for the whole fuse, the way they already keep the artwork and the damage: a builder
     * changing the style mid-fight must not change the mark already burning on the floor.</p>
     */
    public record Settings(String style, int widthTenths, int motion, int fillPercent,
                           boolean lasting, int intervalTicks, int fadedPercent) {

        /** What a boss with no settings of its own warns like: exactly as it always did. */
        public static final Settings PARTICLES = new Settings(TelegraphLineStyles.PARTICLES,
                TeleportPathData.DEFAULT_TELEGRAPH_LINE_WIDTH,
                TeleportPathData.TELEGRAPH_MOTION_STATIC, 0, true,
                BossTuningUtil.defaults().telegraphIntervalTicks(),
                BossTuningUtil.defaults().telegraphFadedPercent());

        public static Settings of(TeleportPathData data) {
            return new Settings(data.getTelegraphLineStyle(), data.getTelegraphLineWidth(),
                    data.getTelegraphLineMotion(), data.getTelegraphLineFill(),
                    data.isTelegraphLineLasting(), data.tuning().telegraphIntervalTicks(),
                    data.tuning().telegraphFadedPercent());
        }

        /**
         * How long a frame drawn with these outlives the tick it was drawn on. Every clock
         * that paints one runs on the repaint interval, so one tick more than that is what
         * carries a warning from one frame to the next without leaving it up after the last.
         */
        public int ttlTicks() {
            return intervalTicks + 1;
        }

        /** The same, for a scheduler that holds the boss rather than its settings. */
        public static Settings of(EntityNPCInterface boss) {
            TeleportPathController controller = boss instanceof IBossController holder
                    ? holder.cnpcgeckoaddon$getTeleportPathController() : null;
            return controller == null ? PARTICLES : of(controller.settings());
        }

        /** Whether the style is a band the client draws rather than the server's own dust. */
        public boolean lines() {
            return TelegraphLineStyles.isLines(style);
        }
    }

    private final Settings settings;
    private final int ownerId;
    private final byte channel;
    private final int ability;
    private final float progress;

    private BossTelegraphPaint(Settings settings, int ownerId, byte channel, int ability,
                               float progress) {
        this.settings = settings;
        this.ownerId = ownerId;
        this.channel = channel;
        this.ability = ability;
        this.progress = progress;
    }

    public static BossTelegraphPaint of(Settings settings, Entity owner, byte channel,
                                        int ability, float progress) {
        return new BossTelegraphPaint(settings, owner.getId(), channel, ability, progress);
    }

    public static BossTelegraphPaint of(TeleportPathData data, Entity owner, byte channel,
                                        int ability, float progress) {
        return of(Settings.of(data), owner, channel, ability, progress);
    }

    /**
     * Whether this mark is drawn as a band rather than spat out as dust.
     *
     * <p>The wind-up always is when the style says so. Everything that stays up for a whole
     * phase - a fuse, the edge of a field, the outline of a burning platform - only is when
     * the boss was told to draw those too, because a band is a heavier thing to leave lying
     * on the arena for a minute than a scattering of dust is.</p>
     */
    public boolean lines() {
        return settings.lines() && (channel == CHANNEL_CAST || settings.lasting());
    }

    public Settings settings() {
        return settings;
    }

    public int ownerId() {
        return ownerId;
    }

    public byte channel() {
        return channel;
    }

    /** How far the wind-up behind the mark has got, from 0 to 1, or {@link #NO_END}. */
    public float progress() {
        return progress;
    }

    public int ttlTicks() {
        return settings.ttlTicks();
    }

    /** The dust the mark would be spat out as, for the half of the code that still does. */
    public DustParticleOptions dust() {
        return BossTelegraphUtil.dust(ability);
    }

    public DustParticleOptions fadedDust() {
        return BossTelegraphUtil.fadedDust(ability, settings.fadedPercent());
    }

    /** The ability's colour, which is what a drawn band is drawn in. */
    public int rgb() {
        return BossTelegraphUtil.textColor(ability);
    }
}

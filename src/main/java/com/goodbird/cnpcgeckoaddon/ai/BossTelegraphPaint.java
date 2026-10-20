package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityProfile;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
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
    /**
     * The zone under a reality rift's crystal, outlined for as long as the crystal hangs there.
     * Its owner is the crystal rather than the boss: the boss is a dimension away, and a drawn
     * band is sent to whoever is near the thing it is drawn on.
     */
    public static final byte CHANNEL_RIFT_CRYSTAL = 8;
    /** The vents a timer is about to fire, each outlined for its warning. */
    public static final byte CHANNEL_VENT = 9;

    /** A warning with nothing to count towards: an open hazard, a platform already alight. */
    public static final float NO_END = -1.0F;


    /**
     * The boss' own settings for drawn warnings, taken once, with one ability's profile laid over
     * them where it has a look of its own.
     *
     * <p>Held apart from the paint because the schedulers take theirs on the cast and keep
     * it for the whole fuse, the way they already keep the artwork and the damage: a builder
     * changing the style mid-fight must not change the mark already burning on the floor.</p>
     *
     * @param colour the colour the ability's profile draws it in, or
     *               {@link BossAbilityProfile#COLOR_OF_KIND} for the kind's own
     */
    public record Settings(String style, int widthTenths, int motion, int fillPercent,
                           boolean lasting, int intervalTicks, int fadedPercent, int colour) {

        /** What a boss with no settings of its own warns like: exactly as it always did. */
        public static final Settings PARTICLES = new Settings(TelegraphLineStyles.PARTICLES,
                TeleportPathData.DEFAULT_TELEGRAPH_LINE_WIDTH,
                TeleportPathData.TELEGRAPH_MOTION_STATIC, 0, true,
                BossTuningUtil.defaults().telegraphIntervalTicks(),
                BossTuningUtil.defaults().telegraphFadedPercent(), BossAbilityProfile.COLOR_OF_KIND);

        /** The boss-wide settings alone, in every kind's own colour. */
        public static Settings of(TeleportPathData data) {
            return new Settings(data.getTelegraphLineStyle(), data.getTelegraphLineWidth(),
                    data.getTelegraphLineMotion(), data.getTelegraphLineFill(),
                    data.isTelegraphLineLasting(), data.tuning().telegraphIntervalTicks(),
                    data.tuning().telegraphFadedPercent(), BossAbilityProfile.COLOR_OF_KIND);
        }

        /**
         * One ability's warning in one phase: the style, the width and the colour of its profile
         * over the boss' own, and the rest - the motion, the fill, whether the long marks are bands
         * too - the boss'. An untouched profile, or no phase, is the boss-wide settings exactly.
         */
        public static Settings of(TeleportPathData data, BossPhaseData phase, int kind) {
            Settings boss = of(data);
            BossAbilityProfile profile = phase == null ? BossAbilityProfile.defaults() : phase.profile(kind);
            if (profile.getTelegraphColor() == BossAbilityProfile.COLOR_OF_KIND
                    && profile.getTelegraphStyle().isEmpty()
                    && profile.getTelegraphLineWidth() == BossAbilityProfile.AS_BOSS) {
                return boss;
            }
            return new Settings(profile.styleOr(boss.style()), profile.lineWidthOr(boss.widthTenths()),
                    boss.motion(), boss.fillPercent(), boss.lasting(), boss.intervalTicks(),
                    boss.fadedPercent(), profile.getTelegraphColor());
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

        /**
         * The same for one ability, in the phase the boss is fighting: what a scheduler takes on
         * the cast and keeps for the whole fuse, look and all.
         */
        public static Settings of(EntityNPCInterface boss, int kind) {
            TeleportPathController controller = boss instanceof IBossController holder
                    ? holder.cnpcgeckoaddon$getTeleportPathController() : null;
            return controller == null ? PARTICLES : of(controller.settings(), controller.activePhase(), kind);
        }

        /** Whether the style is a band the client draws rather than the server's own dust. */
        public boolean lines() {
            return TelegraphLineStyles.isLines(style);
        }

        /** The colour {@code ability}'s marks are drawn in under these settings: the profile's, or the kind's. */
        public int rgb(int ability) {
            return colour >= 0 ? colour : BossTelegraphUtil.textColor(ability);
        }

        /** The dust of that colour, for a mark the server still spits out itself. */
        public DustParticleOptions dust(int ability) {
            return colour >= 0 ? BossTelegraphUtil.colourDust(colour) : BossTelegraphUtil.dust(ability);
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
        return of(settings, owner.getId(), channel, ability, progress);
    }

    /** The same by the owner's id, which is all the paint keeps of it. */
    static BossTelegraphPaint of(Settings settings, int ownerId, byte channel, int ability, float progress) {
        return new BossTelegraphPaint(settings, ownerId, channel, ability, progress);
    }

    /** One ability's paint in one phase: the boss' settings with that ability's profile over them. */
    public static BossTelegraphPaint of(TeleportPathData data, BossPhaseData phase, Entity owner, byte channel,
                                        int ability, float progress) {
        return of(Settings.of(data, phase, ability), owner, channel, ability, progress);
    }

    /**
     * The same in the phase the owner's controller is fighting, for a caller with no phase in hand;
     * an owner that is no boss in a fight gets the boss-wide settings, as every paint did before
     * profiles.
     */
    public static BossTelegraphPaint of(TeleportPathData data, Entity owner, byte channel,
                                        int ability, float progress) {
        return of(data, TeleportPathController.activePhaseOf(owner), owner, channel, ability, progress);
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
        return settings.dust(ability);
    }

    public DustParticleOptions fadedDust() {
        return settings.colour() >= 0
                ? BossTelegraphUtil.fadedColourDust(settings.colour(), settings.fadedPercent())
                : BossTelegraphUtil.fadedDust(ability, settings.fadedPercent());
    }

    /** The ability's colour - its profile's, or the kind's - which is what a drawn band is drawn in. */
    public int rgb() {
        return settings.rgb(ability);
    }
}

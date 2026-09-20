package com.goodbird.cnpcgeckoaddon.data;

import java.util.List;
import java.util.Locale;

/**
 * Where the rift crystal's drawn model lives and what is in it.
 *
 * <p>The geometry, the animation file, the four skins and the two clip names are an agreement
 * between the artwork and the code, and the artwork is made separately: the files may land
 * after the code that reads them, or never. So the agreement is written down once, here, and
 * the model, the renderer and the tests all read it from the same place rather than each
 * spelling out its own copy of a path - which is how one of them ends up looking for a file
 * nobody is drawing.</p>
 *
 * <p>Nothing here touches the game, so a test can check the whole contract without one.</p>
 */
public final class RiftCrystalContract {

    /** The namespace all three kinds of file live under: the addon's own. */
    public static final String NAMESPACE = "cnpcgeckoaddon";

    public static final String GEO_PATH = "geo/rift_crystal.geo.json";
    public static final String ANIMATION_PATH = "animations/rift_crystal.animation.json";
    /** The skin's own drawing; {@code %s} is the skin id, already cleaned. */
    public static final String TEXTURE_PATH_FORMAT = "textures/entity/rift_crystal/%s.png";

    /** The one controller the crystal animates through, and the two clips it knows. */
    public static final String CONTROLLER = "main";
    public static final String IDLE_ANIMATION = "idle";
    public static final String COLLECT_ANIMATION = "collect";

    /** What a crystal is drawn in when nothing else was asked for, or when the ask was not a skin. */
    public static final String DEFAULT_SKIN = "amethyst";

    /** The skins the artwork ships. A builder may name another; it falls back to the default. */
    public static final List<String> SKINS = List.of("amethyst", "ember", "void", "ice");

    /**
     * How long a collected crystal stays in the world for its collect clip to play out. Half a
     * second, the length the clip is drawn to.
     */
    public static final int COLLECT_LINGER_TICKS = 10;

    /** A skin id is a path segment, so it is kept to what one may hold, and to a sane length. */
    public static final int MAX_SKIN_LENGTH = 32;

    private RiftCrystalContract() {
    }

    /**
     * A skin id as a resource path may hold it: lower case, {@code [a-z0-9_]} only, no longer
     * than {@link #MAX_SKIN_LENGTH}, and {@link #DEFAULT_SKIN} for text that leaves nothing.
     *
     * <p>Cleaned rather than rejected because the field it comes from is typed into by hand and
     * read on every change of focus: "Ember" is plainly meant to be the ember skin, and an id
     * that still holds a colon or a space would be a texture lookup that throws.</p>
     */
    public static String cleanSkin(String value) {
        if (value == null) {
            return DEFAULT_SKIN;
        }
        String lower = value.trim().toLowerCase(Locale.ROOT);
        StringBuilder cleaned = new StringBuilder(Math.min(lower.length(), MAX_SKIN_LENGTH));
        for (int i = 0; i < lower.length() && cleaned.length() < MAX_SKIN_LENGTH; i++) {
            char c = lower.charAt(i);
            if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_') {
                cleaned.append(c);
            }
        }
        return cleaned.isEmpty() ? DEFAULT_SKIN : cleaned.toString();
    }

    /** The path of one skin's drawing, from an id that has already been through {@link #cleanSkin}. */
    public static String texturePath(String skin) {
        return String.format(Locale.ROOT, TEXTURE_PATH_FORMAT, cleanSkin(skin));
    }

    /** Whether this id is one of the skins the artwork ships, after cleaning. */
    public static boolean isKnownSkin(String skin) {
        return SKINS.contains(cleanSkin(skin));
    }

    /**
     * Whether a crystal is drawn as the model right now.
     *
     * <p>Both halves of the artwork have to be loaded, not just the geometry: a model with no
     * animation file draws as a still lump, which reads as broken rather than as artwork that
     * has not arrived. Either one missing falls back to the block, which is what a builder who
     * asked for the model before it shipped sees.</p>
     */
    public static boolean drawsModel(boolean wantsModel, boolean geoReady, boolean animationReady) {
        return wantsModel && geoReady && animationReady;
    }

    /** Whether a crystal collected on {@code collectedAtTick} has played its clip out by now. */
    public static boolean lingerOver(long collectedAtTick, long gameTime) {
        return gameTime - collectedAtTick >= COLLECT_LINGER_TICKS;
    }
}

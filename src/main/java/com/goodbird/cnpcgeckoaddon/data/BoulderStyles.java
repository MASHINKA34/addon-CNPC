package com.goodbird.cnpcgeckoaddon.data;

import java.util.List;

/**
 * What the rolling stone is drawn as.
 *
 * <p>{@link #BLOCK} is the styleless default and carries no artwork at all: it keeps the
 * scaled-up block the boulder has always been, so bosses saved before the skins existed
 * look exactly as they did. Every other style is one drawing wrapped around a lumpy shape.</p>
 *
 * <p>Only the id, the label and the two lighting flags live here: this class is read by the
 * GUI as well, and the debris a broken boulder throws still comes from the phase's block.</p>
 */
public final class BoulderStyles {
    public static final String BLOCK = "block";
    public static final String STONE = "stone";
    public static final String MAGMA = "magma";
    public static final String SCULK = "sculk";
    public static final String MOSSY = "mossy";
    public static final String BONE = "bone";
    public static final String GHOST = "ghost";

    private static final List<Style> STYLES = List.of(
            new Style(BLOCK, "cnpcgeckoaddon.boss.boulder_style.block", false, false),
            new Style(STONE, "cnpcgeckoaddon.boss.boulder_style.stone", false, false),
            new Style(MAGMA, "cnpcgeckoaddon.boss.boulder_style.magma", true, false),
            new Style(SCULK, "cnpcgeckoaddon.boss.boulder_style.sculk", false, false),
            new Style(MOSSY, "cnpcgeckoaddon.boss.boulder_style.mossy", false, false),
            new Style(BONE, "cnpcgeckoaddon.boss.boulder_style.bone", false, false),
            new Style(GHOST, "cnpcgeckoaddon.boss.boulder_style.ghost", true, true)
    );

    private BoulderStyles() {
    }

    public static List<Style> values() {
        return STYLES;
    }

    public static Style get(String id) {
        if (id != null) {
            for (Style style : STYLES) {
                if (style.id().equals(id)) {
                    return style;
                }
            }
        }
        return STYLES.getFirst();
    }

    public static String normalize(String id) {
        return get(id).id();
    }

    /** Whether the style is drawn from our own texture rather than from the phase's block. */
    public static boolean isTextured(String id) {
        return !BLOCK.equals(normalize(id));
    }

    /**
     * One boulder's artwork.
     *
     * <p>{@code glowing} draws the stone at full brightness whatever the room is lit like,
     * which is the whole point of a magma boulder in a dark dungeon. {@code translucent}
     * picks the blending render type instead of the cutout one.</p>
     */
    public record Style(String id, String translationKey, boolean glowing, boolean translucent) {
    }
}

package com.goodbird.cnpcgeckoaddon.data;

import java.util.List;

/**
 * The look of the wave an area attack throws out around the boss.
 *
 * <p>{@link #NONE} is the default and draws nothing at all, so bosses saved before the wave
 * existed keep hitting in complete silence exactly as they did.</p>
 *
 * <p>Only the id and the label live here: this class is read by the GUI as well, and the
 * particle and sound choices belong to the server-side scheduler that emits them.</p>
 */
public final class AreaVfxStyles {
    public static final String NONE = "none";
    public static final String VINES = "vines";
    public static final String STONE = "stone";
    public static final String HURRICANE = "hurricane";
    public static final String FIRE = "fire";
    public static final String GHOST = "ghost";
    public static final String SCULK_WAVE = "sculk_wave";

    private static final List<Style> STYLES = List.of(
            new Style(NONE, "cnpcgeckoaddon.boss.area_vfx.none"),
            new Style(VINES, "cnpcgeckoaddon.boss.area_vfx.vines"),
            new Style(STONE, "cnpcgeckoaddon.boss.area_vfx.stone"),
            new Style(HURRICANE, "cnpcgeckoaddon.boss.area_vfx.hurricane"),
            new Style(FIRE, "cnpcgeckoaddon.boss.area_vfx.fire"),
            new Style(GHOST, "cnpcgeckoaddon.boss.area_vfx.ghost"),
            new Style(SCULK_WAVE, "cnpcgeckoaddon.boss.area_vfx.sculk_wave")
    );

    private AreaVfxStyles() {
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

    /** Whether the style draws anything, i.e. whether it is worth scheduling a wave for it. */
    public static boolean isVisible(String id) {
        return !NONE.equals(normalize(id));
    }

    /** One wave's artwork. */
    public record Style(String id, String translationKey) {
    }
}

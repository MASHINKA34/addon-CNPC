package com.goodbird.cnpcgeckoaddon.data;

import java.util.List;

public final class BossBarStyles {
    public static final String NONE = "none";

    private static final List<Style> STYLES = List.of(
            new Style(NONE, "cnpcgeckoaddon.boss.bar_style.none", 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0),
            new Style("moss_cave", "cnpcgeckoaddon.boss.bar_style.moss_cave", 260, 37, 31, 11, 200, 14, 260,
                    260, 10, 31, 2, 200, 6),
            new Style("ghost_dungeon", "cnpcgeckoaddon.boss.bar_style.ghost_dungeon", 1329, 261, 104, 108, 1121, 105, 300,
                    1329, 54, 104, 12, 1121, 30),
            new Style("infernal", "cnpcgeckoaddon.boss.bar_style.infernal", 182, 16, 14, 4, 154, 9, 182,
                    182, 7, 14, 2, 154, 3),
            new Style("sculk", "cnpcgeckoaddon.boss.bar_style.sculk", 256, 44, 29, 14, 198, 18, 256,
                    256, 12, 29, 3, 198, 6)
    );

    private BossBarStyles() {
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

    public static boolean isEnabled(String id) {
        return !NONE.equals(normalize(id));
    }

    /**
     * One bar's artwork, in the texture's own pixels.
     *
     * <p>The {@code timer*} half describes the countdown strip drawn under the health bar.
     * Its texture is always as wide as the health bar's, so both are drawn at the same
     * scale and share an X - only the height and the inner track differ.</p>
     */
    public record Style(String id, String translationKey, int textureWidth, int textureHeight,
                        int trackX, int trackY, int trackWidth, int trackHeight, int preferredWidth,
                        int timerWidth, int timerHeight, int timerTrackX, int timerTrackY,
                        int timerTrackWidth, int timerTrackHeight) {
    }
}

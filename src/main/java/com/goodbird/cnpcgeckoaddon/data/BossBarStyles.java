package com.goodbird.cnpcgeckoaddon.data;

import java.util.List;

public final class BossBarStyles {
    public static final String NONE = "none";

    private static final List<Style> STYLES = List.of(
            new Style(NONE, "cnpcgeckoaddon.boss.bar_style.none", 0, 0, 0, 0, 0, 0, 0),
            new Style("moss_cave", "cnpcgeckoaddon.boss.bar_style.moss_cave", 260, 37, 31, 11, 200, 14, 260),
            new Style("ghost_dungeon", "cnpcgeckoaddon.boss.bar_style.ghost_dungeon", 1329, 261, 104, 108, 1121, 105, 443),
            new Style("infernal", "cnpcgeckoaddon.boss.bar_style.infernal", 182, 16, 14, 4, 154, 9, 182),
            new Style("sculk", "cnpcgeckoaddon.boss.bar_style.sculk", 256, 44, 29, 14, 198, 18, 256)
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

    public record Style(String id, String translationKey, int textureWidth, int textureHeight,
                        int trackX, int trackY, int trackWidth, int trackHeight, int preferredWidth) {
    }
}

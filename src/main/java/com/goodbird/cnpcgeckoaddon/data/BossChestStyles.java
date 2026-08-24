package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.util.StringRepresentable;

import java.util.ArrayList;
import java.util.List;

/** The skins a boss loot chest can wear, and the plain block that means "no skin at all". */
public final class BossChestStyles {
    public static final String VANILLA = "vanilla";

    /**
     * The skins the boss chest block can be built in.
     *
     * <p>An enum rather than plain ids because this doubles as the block state property: a
     * chest then remembers its own look and syncs it to the client for free, and every skin
     * can point at its own particle texture from the blockstate file.</p>
     */
    public enum Skin implements StringRepresentable {
        MOSS_CAVE("moss_cave"),
        INFERNAL("infernal"),
        GHOST("ghost"),
        SCULK("sculk"),
        GILDED("gilded"),
        BONE("bone");

        private final String id;

        Skin(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return id;
        }
    }

    private static final List<Style> STYLES = build();

    private BossChestStyles() {
    }

    private static List<Style> build() {
        List<Style> styles = new ArrayList<>();
        // Vanilla comes first so it is what an unreadable id falls back to.
        styles.add(new Style(VANILLA, "cnpcgeckoaddon.boss.chest_style." + VANILLA, null));
        for (Skin skin : Skin.values()) {
            styles.add(new Style(skin.getSerializedName(),
                    "cnpcgeckoaddon.boss.chest_style." + skin.getSerializedName(), skin));
        }
        return List.copyOf(styles);
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

    /** @return the skin behind this id, or null when it means the plain configured block */
    public static Skin skinOf(String id) {
        return get(id).skin();
    }

    /** One entry of the skin picker. A null {@code skin} is the plain block. */
    public record Style(String id, String translationKey, Skin skin) {
    }
}

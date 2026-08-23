package com.goodbird.cnpcgeckoaddon.data;

import java.util.List;

/**
 * Artwork for the cord the hook drags its victims by.
 *
 * <p>{@link #PARTICLES} is the styleless default and carries no artwork at all: the server
 * keeps spitting its own spark trail, so bosses saved before cords existed look exactly as
 * they did.</p>
 */
public final class HookCordStyles {
    public static final String PARTICLES = "particles";
    public static final String VINE = "vine";
    public static final String CHAIN_INFERNAL = "chain_infernal";
    public static final String TENTACLE = "tentacle";
    public static final String GHOST = "ghost";

    private static final List<Style> STYLES = List.of(
            new Style(PARTICLES, "cnpcgeckoaddon.boss.hook_cord.particles",
                    0.0F, 0, 0, false, false, false, 0.0F),
            new Style(VINE, "cnpcgeckoaddon.boss.hook_cord.vine",
                    0.25F, 4, 4, false, false, false, 0.05F),
            new Style(CHAIN_INFERNAL, "cnpcgeckoaddon.boss.hook_cord.chain_infernal",
                    0.22F, 4, 3, true, true, false, 0.02F),
            new Style(TENTACLE, "cnpcgeckoaddon.boss.hook_cord.tentacle",
                    0.35F, 4, 5, false, false, false, 0.05F),
            new Style(GHOST, "cnpcgeckoaddon.boss.hook_cord.ghost",
                    0.28F, 4, 4, true, true, true, 0.02F)
    );

    private HookCordStyles() {
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

    /** Whether the style is drawn from a texture rather than left to the server's particles. */
    public static boolean isTextured(String id) {
        return !PARTICLES.equals(normalize(id));
    }

    /**
     * One cord's artwork.
     *
     * <p>{@code width} is in blocks. The texture is a vertical filmstrip of {@code frames}
     * square frames that swaps every {@code frameTicks}; {@code flow} offsets the frame by
     * the segment number as well, so the pattern crawls toward the victim instead of just
     * blinking in place. {@code sag} is how far the middle of the cord dips, as a fraction
     * of its own length - a chain barely gives, a vine hangs.</p>
     */
    public record Style(String id, String translationKey, float width, int frames,
                        int frameTicks, boolean flow, boolean glowing, boolean translucent,
                        float sag) {
    }
}

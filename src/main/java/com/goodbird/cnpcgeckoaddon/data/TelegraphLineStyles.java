package com.goodbird.cnpcgeckoaddon.data;

import java.util.List;

/**
 * How a boss draws the zone of the ability it is winding up.
 *
 * <p>{@link #PARTICLES} is the styleless default and is drawn by nobody: the server keeps
 * spitting the dust it always did, so a boss saved before this existed warns exactly as it
 * used to. Every other id is a continuous band the client draws along the same shape, which
 * is the whole point of the table - the same trick the hook's cord plays with its own
 * {@code particles} entry.</p>
 *
 * <p>Ids and labels only. This is read by the screen and carried in a save; what a band looks
 * like belongs to the renderer.</p>
 */
public final class TelegraphLineStyles {
    public static final String PARTICLES = "particles";
    public static final String SOLID = "solid";
    public static final String DASHED = "dashed";
    public static final String DOTTED = "dotted";
    public static final String DOUBLE = "double";
    public static final String GLOW = "glow";

    private static final List<Style> STYLES = List.of(
            new Style(PARTICLES, "cnpcgeckoaddon.boss.telegraph_line.particles"),
            new Style(SOLID, "cnpcgeckoaddon.boss.telegraph_line.solid"),
            new Style(DASHED, "cnpcgeckoaddon.boss.telegraph_line.dashed"),
            new Style(DOTTED, "cnpcgeckoaddon.boss.telegraph_line.dotted"),
            new Style(DOUBLE, "cnpcgeckoaddon.boss.telegraph_line.double"),
            new Style(GLOW, "cnpcgeckoaddon.boss.telegraph_line.glow")
    );

    private TelegraphLineStyles() {
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

    /** Whether the zone is drawn as a band by the client rather than left to the dust. */
    public static boolean isLines(String id) {
        return !PARTICLES.equals(normalize(id));
    }

    /**
     * Where this style sits in the table, which is what goes over the wire and onto the
     * cycling button: one byte rather than the id spelled out per packet.
     */
    public static int indexOf(String id) {
        String normalized = normalize(id);
        for (int i = 0; i < STYLES.size(); i++) {
            if (STYLES.get(i).id().equals(normalized)) {
                return i;
            }
        }
        return 0;
    }

    /** The id at that place in the table, or the default for anything off the end of it. */
    public static String byIndex(int index) {
        return index < 0 || index >= STYLES.size() ? PARTICLES : STYLES.get(index).id();
    }

    /** One way of drawing the zone: what it is called in a save, and what a builder reads. */
    public record Style(String id, String translationKey) {
    }
}

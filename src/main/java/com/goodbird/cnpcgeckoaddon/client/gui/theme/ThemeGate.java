package com.goodbird.cnpcgeckoaddon.client.gui.theme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Whether the addon's own theme can be drawn, decided from what the resource packs hold, and the
 * one colour rule the theme's dark panel forces on the screens.
 *
 * <p>The theme's art ships apart from the code that draws it, and a pack may drop or replace any
 * of its files. All seven have to be there at the size they are cut to, or none of the theme is
 * used and the screens keep the look CustomNPCs gives them: half a theme - a themed panel under
 * stock buttons, or an icon sheet read at the wrong cell size - reads as broken rather than as
 * a choice.</p>
 *
 * <p>Kept free of the game's classes, so the rule is covered by a plain test.</p>
 */
public final class ThemeGate {

    /** A texture's size in pixels. */
    public record Size(int width, int height) {
        @Override
        public String toString() {
            return width + "x" + height;
        }
    }

    /** What a reload found: whether the theme can be drawn, and if not, what is missing or off. */
    public record Verdict(boolean available, List<String> problems) {
    }

    public static final Size PANEL = new Size(64, 64);
    public static final Size BUTTON = new Size(200, 60);
    public static final Size TOGGLE = new Size(48, 20);
    public static final Size FIELD = new Size(200, 20);
    public static final Size SCROLL = new Size(16, 48);
    public static final Size HEADER = new Size(256, 16);
    public static final Size ICONS = new Size(ThemeIcons.SHEET, ThemeIcons.SHEET);

    /** Every file of the theme, by its name under textures/gui/theme/, with the size it is cut to. */
    public static final Map<String, Size> FILES = files();

    /**
     * What the grey CustomNPCs gives its labels becomes on the theme's panel. That grey was chosen
     * for a light stone panel and all but disappears on a dark one.
     */
    public static final int PANEL_TEXT = 0xD0D0D0;

    /** A colour whose brightest channel is under this is too dark to read on the theme's panel. */
    private static final int DARK_CHANNEL = 0x80;

    private ThemeGate() {
    }

    private static Map<String, Size> files() {
        Map<String, Size> files = new LinkedHashMap<>();
        files.put("panel.png", PANEL);
        files.put("button.png", BUTTON);
        files.put("toggle.png", TOGGLE);
        files.put("field.png", FIELD);
        files.put("scroll.png", SCROLL);
        files.put("header.png", HEADER);
        files.put("icons.png", ICONS);
        return Collections.unmodifiableMap(files);
    }

    /**
     * Checks every file of the theme.
     *
     * @param found the size a file was read at, or null where the packs hold no readable file
     */
    public static Verdict check(Function<String, Size> found) {
        List<String> problems = new ArrayList<>();
        FILES.forEach((name, wanted) -> {
            Size size = found.apply(name);
            if (size == null) {
                problems.add(name + " missing");
            } else if (!size.equals(wanted)) {
                problems.add(name + " is " + size + ", not " + wanted);
            }
        });
        return new Verdict(problems.isEmpty(), List.copyOf(problems));
    }

    /** Whether the screens draw the theme: its files are all there, and the player has not turned it off. */
    public static boolean enabled(Verdict verdict, boolean wanted) {
        return verdict.available() && wanted;
    }

    /** The single line the log gets on a reload that leaves the theme unavailable. */
    public static String logLine(Verdict verdict) {
        return "gui theme off, addon screens keep the CustomNPCs look: "
                + String.join("; ", verdict.problems());
    }

    /**
     * The colour a label is drawn in on the theme's panel: its own, unless that is too dark to
     * read there. Bright colours - the white of a title, a warning's red - are left alone.
     */
    public static int panelTextColor(int color) {
        int red = color >> 16 & 0xFF;
        int green = color >> 8 & 0xFF;
        int blue = color & 0xFF;
        return Math.max(red, Math.max(green, blue)) < DARK_CHANNEL
                ? color & 0xFF000000 | PANEL_TEXT : color;
    }
}

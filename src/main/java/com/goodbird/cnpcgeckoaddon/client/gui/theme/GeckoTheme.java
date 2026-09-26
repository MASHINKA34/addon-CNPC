package com.goodbird.cnpcgeckoaddon.client.gui.theme;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.config.AddonClientConfig;
import com.goodbird.cnpcgeckoaddon.utils.CrashGuard;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The addon's own look for its screens - a dark panel, buttons, fields, a scrollbar, a title
 * strip, and an icon for every ability and every section of the boss menus - drawn from the
 * textures under {@code textures/gui/theme/}.
 *
 * <p>The art is made apart from this code, and without it nothing changes: every file is looked
 * for on each resource reload, and unless all of them are there at their sizes the screens are
 * drawn exactly as CustomNPCs draws them, with one line in the log saying why. The player can
 * turn the theme off as well ({@link AddonClientConfig}); {@link #enabled()} is both at once and
 * is what every themed widget asks each frame.</p>
 */
public final class GeckoTheme {

    private static final Logger LOGGER = LoggerFactory.getLogger(CNPCGeckoAddon.MODID);

    private static final String ICONS_FILE = "icons.png";

    public static final ResourceLocation PANEL = ResourceLocation.fromNamespaceAndPath(CNPCGeckoAddon.MODID,
            "textures/gui/theme/panel.png");
    public static final ResourceLocation BUTTON = ResourceLocation.fromNamespaceAndPath(CNPCGeckoAddon.MODID,
            "textures/gui/theme/button.png");
    public static final ResourceLocation TOGGLE = ResourceLocation.fromNamespaceAndPath(CNPCGeckoAddon.MODID,
            "textures/gui/theme/toggle.png");
    public static final ResourceLocation FIELD = ResourceLocation.fromNamespaceAndPath(CNPCGeckoAddon.MODID,
            "textures/gui/theme/field.png");
    public static final ResourceLocation SCROLL = ResourceLocation.fromNamespaceAndPath(CNPCGeckoAddon.MODID,
            "textures/gui/theme/scroll.png");
    public static final ResourceLocation HEADER = ResourceLocation.fromNamespaceAndPath(CNPCGeckoAddon.MODID,
            "textures/gui/theme/header.png");
    public static final ResourceLocation ICONS = ResourceLocation.fromNamespaceAndPath(CNPCGeckoAddon.MODID,
            "textures/gui/theme/icons.png");

    /**
     * Each texture by the name {@link ThemeGate#FILES} knows it by. A name the gate lists and this
     * table lacks is read as missing, which keeps the theme off rather than half drawn.
     */
    private static final Map<String, ResourceLocation> TEXTURES = Map.of(
            "panel.png", PANEL,
            "button.png", BUTTON,
            "toggle.png", TOGGLE,
            "field.png", FIELD,
            "scroll.png", SCROLL,
            "header.png", HEADER,
            ICONS_FILE, ICONS);

    /** Button states: the rows of button.png, top to bottom. */
    public static final int NORMAL = 0;
    public static final int HOVERED = 1;
    public static final int DISABLED = 2;

    /** The width of the switch drawn on a yes/no button; either half of toggle.png. */
    public static final int TOGGLE_WIDTH = 24;
    public static final int TOGGLE_HEIGHT = 20;

    /** How wide the scrollbar's pieces are drawn: scroll.png's own width. */
    public static final int SCROLL_WIDTH = 16;

    /** The side of each of scroll.png's three square cells - track, thumb, arrow. */
    public static final int SCROLL_CELL = 16;

    /** How tall the title strip is: header.png's own height. */
    public static final int HEADER_HEIGHT = 16;

    // Where each texture is cut, as the art is drawn to it.
    private static final int PANEL_BORDER = 8;
    private static final int BUTTON_ROW = 20;
    private static final int BUTTON_BORDER = 4;
    private static final int FIELD_BORDER = 3;
    private static final int SCROLL_END = 4;
    private static final int HEADER_END = 8;

    /** How far past its text a title strip reaches on either side, where it is drawn behind one line. */
    private static final int HEADER_PAD = 12;

    private static boolean available;
    private static long[] drawnIcons = new long[ThemeIcons.CELLS / Long.SIZE];

    /** Looks for the theme's files again whenever the resource packs change. */
    public static final ResourceManagerReloadListener RELOAD_LISTENER =
            manager -> CrashGuard.run("client.gui.theme.reload", () -> reload(manager));

    private GeckoTheme() {
    }

    /** Whether every file of the theme is there, whatever the player chose. */
    public static boolean available() {
        return available;
    }

    /** Whether the addon's screens are drawn in the theme right now. */
    public static boolean enabled() {
        return available && AddonClientConfig.guiTheme();
    }

    /**
     * Reads every file of the theme and decides whether it can be drawn.
     *
     * <p>Each file is decoded rather than merely looked up: a file of the wrong size would be cut
     * in the wrong places, and the icon sheet has to be read anyway to know which cells are
     * empty. They are small, and this runs once per reload.</p>
     */
    static void reload(ResourceManager manager) {
        // A reload that fails half way leaves the theme off rather than half-read.
        available = false;
        Map<String, ThemeGate.Size> sizes = new HashMap<>();
        long[] icons = null;
        for (Map.Entry<String, ResourceLocation> texture : TEXTURES.entrySet()) {
            String name = texture.getKey();
            Optional<Resource> resource = manager.getResource(texture.getValue());
            if (resource.isEmpty()) {
                continue;
            }
            try (InputStream input = resource.get().open();
                 NativeImage image = NativeImage.read(NativeImage.Format.RGBA, input)) {
                ThemeGate.Size size = new ThemeGate.Size(image.getWidth(), image.getHeight());
                sizes.put(name, size);
                if (name.equals(ICONS_FILE) && size.equals(ThemeGate.ICONS)) {
                    icons = ThemeIcons.drawnCells((x, y) -> image.getPixelRGBA(x, y) >>> 24);
                }
            } catch (IOException | RuntimeException error) {
                // Unreadable counts as missing; the stack goes to the log.
                CrashGuard.caught("client.gui.theme.read", name, error);
            }
        }
        ThemeGate.Verdict verdict = ThemeGate.check(sizes::get);
        drawnIcons = icons != null ? icons : new long[ThemeIcons.CELLS / Long.SIZE];
        available = verdict.available();
        if (!available) {
            LOGGER.info(ThemeGate.logLine(verdict));
        }
    }

    /** The row of button.png a button in this state is drawn from. */
    public static int buttonState(boolean active, boolean hovered) {
        return !active ? DISABLED : hovered ? HOVERED : NORMAL;
    }

    /** The panel, nine-sliced to the rectangle. */
    public static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        draw(graphics, PANEL, ThemeGate.PANEL, ThemeSlice.nine(x, y, width, height,
                0, 0, ThemeGate.PANEL.width(), ThemeGate.PANEL.height(), PANEL_BORDER));
    }

    /** A button's face in one of {@link #NORMAL}, {@link #HOVERED} or {@link #DISABLED}. */
    public static void button(GuiGraphics graphics, int x, int y, int width, int height, int state) {
        draw(graphics, BUTTON, ThemeGate.BUTTON, ThemeSlice.nine(x, y, width, height,
                0, state * BUTTON_ROW, ThemeGate.BUTTON.width(), BUTTON_ROW, BUTTON_BORDER));
    }

    /** The switch of a yes/no button, showing yes or no. */
    public static void toggle(GuiGraphics graphics, int x, int y, boolean yes) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(TOGGLE, x, y, yes ? 0.0F : TOGGLE_WIDTH, 0.0F, TOGGLE_WIDTH, TOGGLE_HEIGHT,
                ThemeGate.TOGGLE.width(), ThemeGate.TOGGLE.height());
    }

    /** A text field's frame; the text and the cursor are the field's own. */
    public static void field(GuiGraphics graphics, int x, int y, int width, int height) {
        draw(graphics, FIELD, ThemeGate.FIELD, ThemeSlice.nine(x, y, width, height,
                0, 0, ThemeGate.FIELD.width(), ThemeGate.FIELD.height(), FIELD_BORDER));
    }

    /** The scrollbar's track, {@link #SCROLL_WIDTH} wide, tiled down to the height. */
    public static void scrollTrack(GuiGraphics graphics, int x, int y, int height) {
        draw(graphics, SCROLL, ThemeGate.SCROLL, ThemeSlice.down(x, y, height,
                0, 0, SCROLL_WIDTH, SCROLL_CELL, SCROLL_END));
    }

    /** The scrollbar's thumb, likewise. */
    public static void scrollThumb(GuiGraphics graphics, int x, int y, int height) {
        draw(graphics, SCROLL, ThemeGate.SCROLL, ThemeSlice.down(x, y, height,
                0, SCROLL_CELL, SCROLL_WIDTH, SCROLL_CELL, SCROLL_END));
    }

    /** The arrow that says there is more below the window. */
    public static void scrollArrow(GuiGraphics graphics, int x, int y) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(SCROLL, x, y, 0.0F, SCROLL_CELL * 2, SCROLL_WIDTH, SCROLL_CELL,
                ThemeGate.SCROLL.width(), ThemeGate.SCROLL.height());
    }

    /** A title strip {@link #HEADER_HEIGHT} tall, its ends kept and its middle tiled to the width. */
    public static void header(GuiGraphics graphics, int x, int y, int width) {
        draw(graphics, HEADER, ThemeGate.HEADER, ThemeSlice.across(x, y, width,
                0, 0, ThemeGate.HEADER.width(), HEADER_HEIGHT, HEADER_END));
    }

    /**
     * A title strip behind one line of text that stands on its own - a picker's heading - reaching
     * a little past the text on either side and centred on it from top to bottom.
     */
    public static void headerBehind(GuiGraphics graphics, int textX, int textY, int textWidth) {
        header(graphics, textX - HEADER_PAD, textY - (HEADER_HEIGHT - 8) / 2, textWidth + HEADER_PAD * 2);
    }

    /** Whether there is an icon to draw for this index: on the sheet, and drawn in. */
    public static boolean hasIcon(int index) {
        return available && ThemeIcons.drawn(drawnIcons, index);
    }

    /** Draws an icon at its own 16 pixels, or nothing for an index off the sheet or an empty cell. */
    public static void icon(GuiGraphics graphics, int x, int y, int index) {
        if (!hasIcon(index)) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(ICONS, x, y, ThemeIcons.u(index), ThemeIcons.v(index), ThemeIcons.SIZE, ThemeIcons.SIZE,
                ThemeGate.ICONS.width(), ThemeGate.ICONS.height());
    }

    private static void draw(GuiGraphics graphics, ResourceLocation texture, ThemeGate.Size sheet,
                             List<ThemeSlice.Piece> pieces) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (ThemeSlice.Piece piece : pieces) {
            graphics.blit(texture, piece.x(), piece.y(), piece.u(), piece.v(), piece.width(), piece.height(),
                    sheet.width(), sheet.height());
        }
    }
}

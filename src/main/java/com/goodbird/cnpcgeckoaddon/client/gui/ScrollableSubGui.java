package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.client.gui.theme.GeckoTheme;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeIcons;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeLabel;
import com.goodbird.cnpcgeckoaddon.utils.CrashGuard;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import org.lwjgl.glfw.GLFW;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public abstract class ScrollableSubGui extends GuiBasic {
    private static final int MARGIN = 8;
    private static final int SCROLL_STEP = 24;

    /** The sheet size every {@code GuiGraphics.blit} overload GuiBasic reaches for assumes. */
    private static final int PANEL_SHEET = 256;

    /** How tall the panel drawn into menubg.png is; the rest of the sheet below it is empty. */
    private static final int PANEL_HEIGHT = 217;

    /** The border and rounded corners at each end of the panel, which cannot be stretched. */
    private static final int PANEL_CAP = 4;

    /** The panel's middle, whose rows are all identical, so one slab of it tiles to any height. */
    private static final int PANEL_BODY = PANEL_HEIGHT - PANEL_CAP * 2;

    /** How wide the scrollbar is. */
    private static final int BAR_WIDTH = 6;

    /**
     * How far in from the panel's right edge the bar sits: over the panel's border, and clear of
     * the widest row any screen lays out. Inside the panel rather than beside it, because a
     * grey strip on the dimmed world beside the panel was not read as part of the screen at all,
     * and the button at the foot of the tallest screen went unfound for it.
     */
    private static final int BAR_INSET = 8;

    /** How tall the arrow under the bar is, pointing at the rows still below the window. */
    private static final int ARROW_HEIGHT = 4;

    /**
     * How far from the panel's top left corner a label may start and still be the screen's title.
     * Every screen puts its title 4 to 8 pixels in; the first row of settings starts further down.
     */
    private static final int TITLE_CORNER = 12;

    /** How far in from the panel's sides the theme's title strip runs. */
    private static final int HEADER_INSET = 4;

    /** How far in from the panel's right side a title has to end. */
    private static final int TITLE_MARGIN = 8;

    /** How tall a line of text is drawn; the title strip is centred on it. */
    private static final int TEXT_HEIGHT = 8;

    private int scrollOffset;
    private boolean draggingScrollbar;
    private double dragOffset;
    private boolean relayout;

    @Override
    public void init() {
        super.init();
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll());
        draggingScrollbar = false;
        if (maxScroll() > 0) {
            guiTop = MARGIN - scrollOffset;
        }
    }

    private int maxScroll() {
        return Math.max(0, imageHeight - viewportHeight());
    }

    private int viewportHeight() {
        return Math.max(1, height - MARGIN * 2);
    }

    private int thumbHeight() {
        return Math.min(viewportHeight(), Math.max(16, viewportHeight() * viewportHeight() / imageHeight));
    }

    private int thumbTop() {
        return MARGIN + (viewportHeight() - thumbHeight()) * scrollOffset / maxScroll();
    }

    /** The bar's left edge, just inside the panel's right border. */
    private int barLeft() {
        return guiLeft + imageWidth - BAR_INSET;
    }

    private void scrollTo(int offset) {
        int next = Mth.clamp(offset, 0, maxScroll());
        int shift = scrollOffset - next;
        if (shift == 0) {
            return;
        }
        scrollOffset = next;
        guiTop += shift;
        Set<AbstractWidget> widgets = Collections.newSetFromMap(new IdentityHashMap<>());
        widgets.addAll(wrapper.npcbuttons.values());
        widgets.addAll(wrapper.textfields.values());
        widgets.addAll(wrapper.labels.values());
        widgets.addAll(wrapper.sliders.values());
        widgets.forEach(widget -> widget.setY(widget.getY() + shift));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (super.mouseScrolled(mouseX, mouseY, horizontal, vertical)) {
            return true;
        }
        if (maxScroll() == 0 || vertical == 0) {
            return false;
        }
        scrollTo(scrollOffset - (int) Math.round(vertical * SCROLL_STEP));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int left = barLeft();
        if (!hasSubGui() && maxScroll() > 0 && button == 0 && mouseX >= left && mouseX < left + BAR_WIDTH
                && mouseY >= MARGIN && mouseY < height - MARGIN) {
            draggingScrollbar = true;
            dragOffset = mouseY >= thumbTop() && mouseY < thumbTop() + thumbHeight()
                    ? mouseY - thumbTop() : thumbHeight() / 2.0;
            dragScrollbar(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void dragScrollbar(double mouseY) {
        int travel = viewportHeight() - thumbHeight();
        if (travel > 0) {
            scrollTo((int) Math.round((mouseY - MARGIN - dragOffset) * maxScroll() / travel));
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar && button == 0 && !hasSubGui()) {
            dragScrollbar(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingScrollbar && button == 0) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        boolean handled = super.keyPressed(key, scanCode, modifiers);
        if (hasSubGui() || maxScroll() == 0) {
            return handled;
        }
        if (!handled && (key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_PAGE_DOWN)) {
            scrollTo(scrollOffset + (key == GLFW.GLFW_KEY_PAGE_UP ? -1 : 1) * viewportHeight());
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB && getFocused() instanceof AbstractWidget widget) {
            if (widget.getY() < MARGIN) {
                scrollTo(scrollOffset + widget.getY() - MARGIN);
            } else if (widget.getBottom() > height - MARGIN) {
                scrollTo(scrollOffset + widget.getBottom() - height + MARGIN);
            }
        }
        return handled;
    }

    /**
     * Rebuilds the screen before the next frame is drawn.
     *
     * <p>A screen whose set of rows depends on a setting has to lay itself out again when that
     * setting changes, and {@code buttonEvent} runs from inside the click walk over
     * {@code children()} - clearing that list there, which {@code init} does, is a crash.
     * {@link #render} is the next moment nothing is iterating.</p>
     */
    protected void requestLayout() {
        relayout = true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (relayout && !hasSubGui()) {
            relayout = false;
            init();
        }
        // Asked once, so a frame never mixes the theme's panel with the old scrollbar.
        boolean themed = background != null && GeckoTheme.enabled();
        if (themed) {
            renderThemed(graphics, mouseX, mouseY, partialTick);
        } else if (background != null && imageWidth <= PANEL_SHEET && imageHeight > PANEL_HEIGHT) {
            renderTall(graphics, mouseX, mouseY, partialTick);
        } else {
            super.render(graphics, mouseX, mouseY, partialTick);
        }
        if (!hasSubGui() && maxScroll() > 0) {
            // Over the widgets, since it is drawn after them; the bar's column is clear of them.
            int left = barLeft();
            if (themed && renderThemedScrollbar(graphics, left)) {
                return;
            }
            graphics.fill(left, MARGIN, left + BAR_WIDTH, height - MARGIN, 0xFF303030);
            graphics.fill(left, thumbTop(), left + BAR_WIDTH, thumbTop() + thumbHeight(), 0xFFB0B0B0);
            if (scrollOffset < maxScroll()) {
                drawDownArrow(graphics, left, height - MARGIN + 1);
            }
        }
    }

    /**
     * A small arrow in the bottom margin under the bar, shown while there are rows below the
     * window: the bar alone says the same, and was not read.
     */
    private static void drawDownArrow(GuiGraphics graphics, int left, int top) {
        for (int row = 0; row < ARROW_HEIGHT; row++) {
            // Each row a pixel narrower on either side than the one above, down to a point.
            graphics.fill(left - 1 + row, top + row, left + BAR_WIDTH + 1 - row, top + row + 1, 0xFFB0B0B0);
        }
    }

    /**
     * Draws the panel GuiBasic would have drawn, and draws it in one piece.
     *
     * <p>GuiBasic lays the background down as a single quad, and the blit it uses hardcodes a
     * {@value #PANEL_SHEET}px sheet. The panel inside menubg.png is only {@value #PANEL_HEIGHT}px
     * of that, so a taller screen samples past the bottom of the texture - which carries no
     * mcmeta and therefore no clamp, and so repeats. What that draws is a transparent band where
     * the panel art runs out, a second copy of the panel's top border starting at
     * {@value #PANEL_SHEET}px, and every row that lands in the band left sitting on the dimmed
     * world.</p>
     *
     * <p>The dim and the panel have to go down in that order and before the widgets, and
     * GuiBasic draws both itself, so it is told it has neither for the length of the call.</p>
     */
    private void renderTall(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ResourceLocation panel = background;
        boolean dim = drawDefaultBackground;
        if (dim && !hasSubGui()) {
            renderBackground(graphics, mouseX, mouseY, partialTick);
        }
        int body = imageHeight - PANEL_CAP * 2;
        graphics.blit(panel, guiLeft, guiTop, 0, 0, imageWidth, PANEL_CAP);
        for (int drawn = 0; drawn < body; drawn += PANEL_BODY) {
            graphics.blit(panel, guiLeft, guiTop + PANEL_CAP + drawn, 0, PANEL_CAP,
                    imageWidth, Math.min(PANEL_BODY, body - drawn));
        }
        graphics.blit(panel, guiLeft, guiTop + imageHeight - PANEL_CAP, 0, PANEL_HEIGHT - PANEL_CAP,
                imageWidth, PANEL_CAP);
        background = null;
        drawDefaultBackground = false;
        try {
            super.render(graphics, mouseX, mouseY, partialTick);
        } finally {
            background = panel;
            drawDefaultBackground = dim;
        }
    }

    /**
     * The icon the theme puts before this screen's title: the ability or the section of the boss
     * menus the screen belongs to, or {@link ThemeIcons#NONE}.
     */
    protected int themeIcon() {
        return ScreenIcons.of(this);
    }

    /**
     * Draws the screen on the theme's panel instead of menubg.png: the same rectangle, so nothing
     * on it moves, nine-sliced to any height - which the tall-screen tiling above only exists to
     * work around for menubg.png. Behind the title go the theme's strip and this screen's icon.
     *
     * <p>As with the tall screens, the dim and the panel go down first and GuiBasic is told it
     * has neither for the length of its own call.</p>
     */
    private void renderThemed(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ResourceLocation panel = background;
        boolean dim = drawDefaultBackground;
        if (dim && !hasSubGui()) {
            renderBackground(graphics, mouseX, mouseY, partialTick);
        }
        ThemeLabel title = themeTitle();
        int indent = 0;
        try {
            GeckoTheme.panel(graphics, guiLeft, guiTop, imageWidth, imageHeight);
            if (title != null) {
                indent = renderHeader(graphics, title);
            }
        } catch (Throwable error) {
            CrashGuard.caught("client.gui.theme.panel", error);
        }
        background = null;
        drawDefaultBackground = false;
        // The title makes room for its icon for this frame only; the label itself stays put.
        if (indent > 0) {
            title.setX(title.getX() + indent);
        }
        try {
            super.render(graphics, mouseX, mouseY, partialTick);
        } finally {
            if (indent > 0) {
                title.setX(title.getX() - indent);
            }
            background = panel;
            drawDefaultBackground = dim;
        }
    }

    /**
     * The theme's strip across the panel behind the title, and the screen's icon at the title's
     * left where there is one and the title still fits beside it.
     *
     * @return how far the title has to move right for the icon: 0 without one
     */
    private int renderHeader(GuiGraphics graphics, ThemeLabel title) {
        int top = title.getY() - (GeckoTheme.HEADER_HEIGHT - TEXT_HEIGHT) / 2;
        GeckoTheme.header(graphics, guiLeft + HEADER_INSET, top, imageWidth - HEADER_INSET * 2);
        int icon = themeIcon();
        if (!GeckoTheme.hasIcon(icon)
                || !ThemeIcons.titleFits(title.getX(), font.width(title.getMessage()), titleRoom(title))) {
            return 0;
        }
        GeckoTheme.icon(graphics, title.getX(), top, icon);
        return ThemeIcons.INDENT;
    }

    /**
     * The screen's title, if it has one: the white label in the panel's top left corner, which is
     * how every screen here titles itself. The topmost, should two qualify.
     */
    private ThemeLabel themeTitle() {
        ThemeLabel title = null;
        for (GuiLabel label : wrapper.labels.values()) {
            if (label instanceof ThemeLabel themed && themed.visible && themed.enabled && themed.isTitleColored()
                    && inTitleCorner(themed) && (title == null || themed.getY() < title.getY())) {
                title = themed;
            }
        }
        return title;
    }

    private boolean inTitleCorner(GuiLabel label) {
        int right = label.getX() - guiLeft;
        int down = label.getY() - guiTop;
        return right >= 0 && right <= TITLE_CORNER && down >= 0 && down <= TITLE_CORNER;
    }

    /** Where the title has to end: before the next thing on its row, or at the panel's inner edge. */
    private int titleRoom(GuiLabel title) {
        int room = guiLeft + imageWidth - TITLE_MARGIN;
        int top = title.getY();
        int bottom = top + font.lineHeight;
        Set<AbstractWidget> widgets = Collections.newSetFromMap(new IdentityHashMap<>());
        widgets.addAll(wrapper.npcbuttons.values());
        widgets.addAll(wrapper.textfields.values());
        widgets.addAll(wrapper.labels.values());
        for (AbstractWidget widget : widgets) {
            // A label is as tall as its line, whatever height it was built with.
            int widgetBottom = widget.getY() + Math.max(widget.getHeight(), font.lineHeight);
            if (widget != title && widget.visible && widget.getX() > title.getX()
                    && widget.getY() < bottom && widgetBottom > top) {
                room = Math.min(room, widget.getX() - 2);
            }
        }
        return room;
    }

    /**
     * The scrollbar out of the theme's pieces, drawn at their own width and centred on the bar's
     * column - which, and what a click there does, stays exactly where it was. The arrow for the
     * rows below the window sits at the foot of the track, under the thumb.
     *
     * @return false if drawing failed, for the plain bar to be drawn instead
     */
    private boolean renderThemedScrollbar(GuiGraphics graphics, int left) {
        try {
            int x = left + BAR_WIDTH / 2 - GeckoTheme.SCROLL_WIDTH / 2;
            GeckoTheme.scrollTrack(graphics, x, MARGIN, viewportHeight());
            if (scrollOffset < maxScroll()) {
                GeckoTheme.scrollArrow(graphics, x, height - MARGIN - GeckoTheme.SCROLL_CELL);
            }
            GeckoTheme.scrollThumb(graphics, x, thumbTop(), thumbHeight());
            return true;
        } catch (Throwable error) {
            CrashGuard.caught("client.gui.theme.scrollbar", error);
            return false;
        }
    }
}

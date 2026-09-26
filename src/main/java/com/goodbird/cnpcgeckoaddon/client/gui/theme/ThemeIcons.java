package com.goodbird.cnpcgeckoaddon.client.gui.theme;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;

import java.util.function.IntBinaryOperator;

/**
 * Where an icon sits on the theme's icon sheet, and which icon stands for what.
 *
 * <p>The sheet is sixteen 16-pixel icons to a row. Its first cells follow
 * {@link BossAbilityKind} one for one, so an ability's number is its icon's number and a new
 * ability needs only its drawing; the fourth row holds the boss menus' sections. The art is
 * drawn to this layout, so these numbers are a contract with it rather than a choice to revise
 * here.</p>
 *
 * <p>Kept free of the game's classes, so the layout is covered by a plain test.</p>
 */
public final class ThemeIcons {

    /** No icon: the button or the title is drawn as it would be without one. */
    public static final int NONE = -1;

    /** An icon's side, in pixels. */
    public static final int SIZE = 16;

    /** The sheet's side, in pixels. */
    public static final int SHEET = 256;

    public static final int PER_ROW = SHEET / SIZE;
    public static final int CELLS = PER_ROW * PER_ROW;

    /** How far a title moves right to make room for its icon: the icon and a two-pixel gap. */
    public static final int INDENT = SIZE + 2;

    // The sections of the boss' menus: the fourth row of the sheet, in the order it is drawn in.
    public static final int PHASES = 48;
    public static final int TOTEMS = 49;
    public static final int CHEST = 50;
    public static final int AGGRO_ZONE = 51;
    public static final int BOSS_BAR = 52;
    public static final int HEALTH_LINK = 53;
    public static final int TUNING = 54;
    public static final int TELEPORT_PATHS = 55;
    public static final int EFFECTS = 56;
    public static final int POINTS = 57;
    public static final int CUES = 58;
    public static final int IMMUNITIES = 59;
    public static final int TELEGRAPH = 60;
    public static final int COMBOS = 61;
    public static final int FINISH = 62;
    public static final int CARRY = 63;

    private ThemeIcons() {
    }

    /** Whether the index names a cell of the sheet at all. */
    public static boolean onSheet(int index) {
        return index >= 0 && index < CELLS;
    }

    /** The left edge of the index's cell on the sheet. */
    public static int u(int index) {
        return index % PER_ROW * SIZE;
    }

    /** The top edge of the index's cell on the sheet. */
    public static int v(int index) {
        return index / PER_ROW * SIZE;
    }

    /** The icon of an ability kind, or {@link #NONE} for a number that is not a kind. */
    public static int ofKind(int kind) {
        return kind >= 0 && kind < BossAbilityKind.COUNT ? kind : NONE;
    }

    /**
     * Which cells of a sheet have anything drawn in them: bit {@code i} of the answer is set when
     * cell {@code i} holds a pixel that is not fully transparent.
     *
     * <p>An empty cell - an ability whose icon has not been drawn yet - is a button or a title
     * without an icon, rather than a sixteen-pixel gap before its text.</p>
     *
     * @param alphaAt a pixel's alpha, 0..255, by its x and y on the sheet
     */
    public static long[] drawnCells(IntBinaryOperator alphaAt) {
        long[] drawn = new long[CELLS / Long.SIZE];
        for (int cell = 0; cell < CELLS; cell++) {
            if (anyPixel(alphaAt, u(cell), v(cell))) {
                drawn[cell / Long.SIZE] |= 1L << (cell % Long.SIZE);
            }
        }
        return drawn;
    }

    private static boolean anyPixel(IntBinaryOperator alphaAt, int left, int top) {
        for (int y = top; y < top + SIZE; y++) {
            for (int x = left; x < left + SIZE; x++) {
                if ((alphaAt.applyAsInt(x, y) & 0xFF) != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether the index names a cell of the sheet that has a drawing in it. */
    public static boolean drawn(long[] cells, int index) {
        return onSheet(index) && cells != null && index / Long.SIZE < cells.length
                && (cells[index / Long.SIZE] & 1L << (index % Long.SIZE)) != 0;
    }

    /**
     * Whether a title moved right by {@link #INDENT} to make room for its icon still ends by
     * {@code room} - the next thing on its row, or the panel's inner edge. A title that would
     * not keeps its place and goes without the icon: the header is decoration, the title is not.
     */
    public static boolean titleFits(int titleX, int titleWidth, int room) {
        return titleX + INDENT + titleWidth <= room;
    }
}

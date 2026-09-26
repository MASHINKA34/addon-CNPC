package com.goodbird.cnpcgeckoaddon.client.gui.theme;

import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeSlice.Piece;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How the theme's textures are cut over the addon's panels, buttons and fields. A gap between two
 * pieces is a line of the world showing through the panel; an overlap or a piece sampled from the
 * wrong part of the sheet is a seam or a stray border in the middle of a button.
 */
class ThemeSliceTest {

    @Test
    @DisplayName("a tall panel is covered pixel for pixel, its corners as drawn and its middle tiled from the middle")
    void panelCoversItsRectangle() {
        // The boss menu: 256 wide and taller than the sheet many times over.
        List<Piece> pieces = ThemeSlice.nine(10, 20, 256, 322, 0, 0, 64, 64, 8);
        assertCovered(pieces, 10, 20, 256, 322);
        assertSampledFrom(pieces, 0, 0, 64, 64);
        assertPiece(pieces, 10, 20, 0, 0, 8, 8);
        assertPiece(pieces, 10 + 248, 20, 56, 0, 8, 8);
        assertPiece(pieces, 10, 20 + 314, 0, 56, 8, 8);
        assertPiece(pieces, 10 + 248, 20 + 314, 56, 56, 8, 8);
        for (Piece piece : pieces) {
            boolean inner = piece.x() > 10 && piece.x() < 10 + 248 && piece.y() > 20 && piece.y() < 20 + 314;
            if (inner) {
                assertEquals(8, piece.u(), "the middle is tiled from the middle: " + piece);
                assertEquals(8, piece.v(), "the middle is tiled from the middle: " + piece);
                assertTrue(piece.width() <= 48 && piece.height() <= 48, "no tile larger than the middle: " + piece);
            }
        }
    }

    @Test
    @DisplayName("a button takes the row of its state and keeps its 4-pixel border at the grid's size and at full width")
    void buttonRowsAndBorders() {
        for (int state = 0; state < 3; state++) {
            // The phase grid's buttons: 114 by 24, taller than a row of button.png.
            List<Piece> pieces = ThemeSlice.nine(0, 0, 114, 24, 0, state * 20, 200, 20, 4);
            assertCovered(pieces, 0, 0, 114, 24);
            assertSampledFrom(pieces, 0, state * 20, 200, 20);
            assertPiece(pieces, 0, 0, 0, state * 20, 4, 4);
            assertPiece(pieces, 110, 20, 196, state * 20 + 16, 4, 4);
        }
        // Wider than the texture: the middle is tiled across rather than stretched.
        List<Piece> wide = ThemeSlice.nine(0, 0, 300, 20, 0, 20, 200, 20, 4);
        assertCovered(wide, 0, 0, 300, 20);
        assertSampledFrom(wide, 0, 20, 200, 20);
    }

    @Test
    @DisplayName("a text field keeps its 3-pixel frame on every side")
    void fieldFrame() {
        List<Piece> pieces = ThemeSlice.nine(5, 7, 70, 20, 0, 0, 200, 20, 3);
        assertCovered(pieces, 5, 7, 70, 20);
        assertSampledFrom(pieces, 0, 0, 200, 20);
        assertPiece(pieces, 5, 7, 0, 0, 3, 3);
        assertPiece(pieces, 5 + 67, 7, 197, 0, 3, 3);
        assertPiece(pieces, 5, 7 + 17, 0, 17, 3, 3);
        assertPiece(pieces, 5 + 67, 7 + 17, 197, 17, 3, 3);
    }

    @Test
    @DisplayName("a rectangle smaller than its two borders splits them between its sides, and an empty one draws nothing")
    void smallAndEmptyRectangles() {
        List<Piece> pieces = ThemeSlice.nine(0, 0, 5, 3, 0, 0, 64, 64, 8);
        assertCovered(pieces, 0, 0, 5, 3);
        assertSampledFrom(pieces, 0, 0, 64, 64);
        // The near side keeps the first texels, the far side its last ones.
        assertPiece(pieces, 0, 0, 0, 0, 2, 1);
        assertPiece(pieces, 2, 1, 61, 62, 3, 2);
        assertTrue(ThemeSlice.nine(0, 0, 0, 10, 0, 0, 64, 64, 8).isEmpty());
        assertTrue(ThemeSlice.nine(0, 0, 10, -4, 0, 0, 64, 64, 8).isEmpty());
        assertTrue(ThemeSlice.across(0, 0, 10, 0, 0, 256, 0, 8).isEmpty());
    }

    @Test
    @DisplayName("the title strip keeps its 8-pixel ends and tiles the 240 pixels between them")
    void headerAcross() {
        List<Piece> pieces = ThemeSlice.across(4, 1, 248, 0, 0, 256, 16, 8);
        assertCovered(pieces, 4, 1, 248, 16);
        assertSampledFrom(pieces, 0, 0, 256, 16);
        assertPiece(pieces, 4, 1, 0, 0, 8, 16);
        assertPiece(pieces, 4 + 240, 1, 248, 0, 8, 16);
        assertPiece(pieces, 12, 1, 8, 0, 232, 16);
        List<Piece> wide = ThemeSlice.across(0, 0, 600, 0, 0, 256, 16, 8);
        assertCovered(wide, 0, 0, 600, 16);
        assertSampledFrom(wide, 0, 0, 256, 16);
    }

    @Test
    @DisplayName("the scrollbar's track and thumb run down from their own cell and no other")
    void scrollDown() {
        for (int cell = 0; cell < 2; cell++) {
            List<Piece> pieces = ThemeSlice.down(3, 8, 300, 0, cell * 16, 16, 16, 4);
            assertCovered(pieces, 3, 8, 16, 300);
            assertSampledFrom(pieces, 0, cell * 16, 16, 16);
            assertPiece(pieces, 3, 8, 0, cell * 16, 16, 4);
            assertPiece(pieces, 3, 8 + 296, 0, cell * 16 + 12, 16, 4);
        }
    }

    /** Every pixel of the rectangle is drawn by exactly one piece, and no piece reaches outside it. */
    private static void assertCovered(List<Piece> pieces, int x, int y, int width, int height) {
        int[][] hits = new int[height][width];
        for (Piece piece : pieces) {
            assertTrue(piece.width() > 0 && piece.height() > 0, "empty piece " + piece);
            assertTrue(piece.x() >= x && piece.y() >= y && piece.x() + piece.width() <= x + width
                    && piece.y() + piece.height() <= y + height, piece + " reaches outside the rectangle");
            for (int row = piece.y(); row < piece.y() + piece.height(); row++) {
                for (int column = piece.x(); column < piece.x() + piece.width(); column++) {
                    hits[row - y][column - x]++;
                }
            }
        }
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                assertEquals(1, hits[row][column], "pixel " + (x + column) + ", " + (y + row));
            }
        }
    }

    /** Every piece samples texels of its own region of the sheet. */
    private static void assertSampledFrom(List<Piece> pieces, int u, int v, int width, int height) {
        for (Piece piece : pieces) {
            assertTrue(piece.u() >= u && piece.v() >= v && piece.u() + piece.width() <= u + width
                    && piece.v() + piece.height() <= v + height, piece + " samples outside its region");
        }
    }

    private static void assertPiece(List<Piece> pieces, int x, int y, int u, int v, int width, int height) {
        Piece wanted = new Piece(x, y, u, v, width, height);
        assertTrue(pieces.contains(wanted), "no " + wanted + " among " + pieces);
    }
}

package com.goodbird.cnpcgeckoaddon.client.gui.theme;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The icon sheet's layout, which the art is drawn to: an ability's number is its cell, the menu
 * sections fill the fourth row, and an index with no drawing is no icon at all rather than a
 * sixteen-pixel hole before a button's text.
 */
class ThemeIconIndexTest {

    @Test
    @DisplayName("every ability kind is its own cell, sixteen to a row, in BossAbilityKind's order")
    void kindsAreTheirOwnCells() {
        for (int kind = 0; kind < BossAbilityKind.COUNT; kind++) {
            int icon = ThemeIcons.ofKind(kind);
            assertEquals(kind, icon);
            assertTrue(ThemeIcons.onSheet(icon));
            assertEquals(kind % 16 * 16, ThemeIcons.u(icon), "column of kind " + kind);
            assertEquals(kind / 16 * 16, ThemeIcons.v(icon), "row of kind " + kind);
        }
        // The vents, the newest kind: the thirteenth cell of the second row.
        assertEquals(12 * 16, ThemeIcons.u(ThemeIcons.ofKind(BossAbilityKind.VENT)));
        assertEquals(16, ThemeIcons.v(ThemeIcons.ofKind(BossAbilityKind.VENT)));
        assertTrue(BossAbilityKind.COUNT <= ThemeIcons.PHASES,
                "the kinds have run into the sections' row: the sheet needs a new layout");
        assertEquals(ThemeIcons.NONE, ThemeIcons.ofKind(-1));
        assertEquals(ThemeIcons.NONE, ThemeIcons.ofKind(BossAbilityKind.COUNT));
    }

    @Test
    @DisplayName("the menu sections are cells 48 to 63, the fourth row, in the order the art lists them")
    void sectionsFillTheFourthRow() {
        int[] sections = {
                ThemeIcons.PHASES, ThemeIcons.TOTEMS, ThemeIcons.CHEST, ThemeIcons.AGGRO_ZONE,
                ThemeIcons.BOSS_BAR, ThemeIcons.HEALTH_LINK, ThemeIcons.TUNING, ThemeIcons.TELEPORT_PATHS,
                ThemeIcons.EFFECTS, ThemeIcons.POINTS, ThemeIcons.CUES, ThemeIcons.IMMUNITIES,
                ThemeIcons.TELEGRAPH, ThemeIcons.COMBOS, ThemeIcons.FINISH, ThemeIcons.CARRY};
        for (int i = 0; i < sections.length; i++) {
            assertEquals(48 + i, sections[i], "section " + i);
            assertEquals(i * 16, ThemeIcons.u(sections[i]));
            assertEquals(48, ThemeIcons.v(sections[i]));
        }
    }

    @Test
    @DisplayName("an index off the sheet has no icon, and neither has a cell left empty")
    void offTheSheetOrEmptyIsNoIcon() {
        assertTrue(ThemeIcons.onSheet(0));
        assertTrue(ThemeIcons.onSheet(255));
        assertFalse(ThemeIcons.onSheet(ThemeIcons.NONE));
        assertFalse(ThemeIcons.onSheet(256));
        assertFalse(ThemeIcons.onSheet(Integer.MAX_VALUE));
        assertFalse(ThemeIcons.onSheet(Integer.MIN_VALUE));

        // One pixel in the far corner of cell 5, one in the first pixel of cell 50, nothing else.
        long[] drawn = ThemeIcons.drawnCells((x, y) ->
                x == 5 * 16 + 15 && y == 15 || x == 2 * 16 && y == 3 * 16 ? 255 : 0);
        assertTrue(ThemeIcons.drawn(drawn, 5));
        assertTrue(ThemeIcons.drawn(drawn, 50));
        assertFalse(ThemeIcons.drawn(drawn, 4), "the cell before the pixel");
        assertFalse(ThemeIcons.drawn(drawn, 6), "the cell after it");
        assertFalse(ThemeIcons.drawn(drawn, 21), "the cell under it");
        assertFalse(ThemeIcons.drawn(drawn, BossAbilityKind.VENT), "a kind with no drawing yet");
        assertFalse(ThemeIcons.drawn(drawn, ThemeIcons.NONE));
        assertFalse(ThemeIcons.drawn(drawn, 256));
        assertFalse(ThemeIcons.drawn(null, 5), "no sheet read, no icons");

        long[] empty = ThemeIcons.drawnCells((x, y) -> 0);
        for (int cell = 0; cell < ThemeIcons.CELLS; cell++) {
            assertFalse(ThemeIcons.drawn(empty, cell), "cell " + cell + " of a transparent sheet");
        }
    }

    @Test
    @DisplayName("a title moves over for its icon only while it still ends where it has to")
    void titleMakesRoomOnlyIfItFits() {
        assertTrue(ThemeIcons.titleFits(8, 100, 8 + ThemeIcons.INDENT + 100));
        assertFalse(ThemeIcons.titleFits(8, 100, 8 + ThemeIcons.INDENT + 99));
        assertEquals(ThemeIcons.SIZE + 2, ThemeIcons.INDENT);
    }
}

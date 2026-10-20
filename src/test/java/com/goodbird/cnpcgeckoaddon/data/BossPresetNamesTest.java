package com.goodbird.cnpcgeckoaddon.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a boss preset may be called. The name ends up as a file on the server, typed into a screen or
 * a command by whoever builds the boss, so the cleaning is what keeps a file name to letters a file
 * system takes - and what refuses a name rather than guessing one.
 */
class BossPresetNamesTest {

    @Test
    @DisplayName("a typed name is lowered, its spaces become one underscore and the rest is dropped")
    void cleansTypedNames() {
        assertEquals("demo_2", BossPresetNames.clean("Demo 2!"));
        assertEquals("my_boss", BossPresetNames.clean("  My   Boss  "));
        assertEquals("upper-case_ok", BossPresetNames.clean("UPPER-case_ok"));
        assertEquals("demo", BossPresetNames.clean("demo !"));
        assertEquals("a_b", BossPresetNames.clean("a\tb"));
        assertEquals("abc", BossPresetNames.clean("a.b/c"));
    }

    @Test
    @DisplayName("nothing usable left is a refusal, not a guess")
    void refusesEmptyNames() {
        assertEquals("", BossPresetNames.clean(null));
        assertEquals("", BossPresetNames.clean(""));
        assertEquals("", BossPresetNames.clean("   "));
        assertEquals("", BossPresetNames.clean("!!!"));
        assertEquals("", BossPresetNames.clean("Босс"), "only a-z, 0-9, _ and - are kept");
        assertEquals("", BossPresetNames.clean("../.."));
    }

    @Test
    @DisplayName("a name is cut to 32 characters, never on a separator")
    void cutsLongNames() {
        assertEquals("a".repeat(32), BossPresetNames.clean("a".repeat(40)));
        assertEquals("a".repeat(31), BossPresetNames.clean("a".repeat(31) + " b"));
        assertEquals("a".repeat(30) + "_b", BossPresetNames.clean("a".repeat(30) + " bcd"));
    }

    @Test
    @DisplayName("the names Windows keeps for its devices are refused")
    void refusesDeviceNames() {
        assertEquals("", BossPresetNames.clean("CON"));
        assertEquals("", BossPresetNames.clean("nul"));
        assertEquals("", BossPresetNames.clean("Lpt1"));
        assertEquals("con1", BossPresetNames.clean("con1"));
        assertEquals("console", BossPresetNames.clean("console"));
    }

    @Test
    @DisplayName("cleaning a clean name changes nothing")
    void cleaningIsStable() {
        for (String typed : new String[]{"Demo 2!", "  My   Boss  ", "a".repeat(40), "x-y_z 9"}) {
            String once = BossPresetNames.clean(typed);
            assertEquals(once, BossPresetNames.clean(once), typed);
            assertTrue(BossPresetNames.isValid(once), typed);
        }
    }

    @Test
    @DisplayName("only a clean name is a valid one")
    void validNames() {
        assertTrue(BossPresetNames.isValid("demo"));
        assertTrue(BossPresetNames.isValid("demo_2-b"));
        assertFalse(BossPresetNames.isValid("Demo"));
        assertFalse(BossPresetNames.isValid("a.b"));
        assertFalse(BossPresetNames.isValid("../x"));
        assertFalse(BossPresetNames.isValid(""));
        assertFalse(BossPresetNames.isValid(null));
        assertFalse(BossPresetNames.isValid("a".repeat(33)));
        assertFalse(BossPresetNames.isValid("aux"));
    }

    @Test
    @DisplayName("a file is a preset only by a valid name and the .snbt ending")
    void presetFiles() {
        assertEquals("demo", BossPresetNames.nameOfFile("demo.snbt"));
        assertEquals("demo.snbt", BossPresetNames.fileName("demo"));
        assertNull(BossPresetNames.nameOfFile("demo.txt"));
        assertNull(BossPresetNames.nameOfFile("Demo.snbt"));
        assertNull(BossPresetNames.nameOfFile("demo.snbt.tmp"));
        assertNull(BossPresetNames.nameOfFile(".snbt"));
        assertNull(BossPresetNames.nameOfFile(null));
    }
}

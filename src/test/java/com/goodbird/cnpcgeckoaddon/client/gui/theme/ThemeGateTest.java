package com.goodbird.cnpcgeckoaddon.client.gui.theme;

import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeGate.Size;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeGate.Verdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When the addon's screens are drawn in its own theme. The art ships apart from the code, so the
 * common case - the textures not there yet, or a pack that dropped one - has to leave every
 * screen exactly as CustomNPCs draws it, with a line in the log saying why.
 */
class ThemeGateTest {

    private static Map<String, Size> everyFile() {
        return new HashMap<>(ThemeGate.FILES);
    }

    @Test
    @DisplayName("the theme is the seven files of the art's contract, at the sizes they are drawn to")
    void theContract() {
        assertEquals(Map.of(
                "panel.png", new Size(64, 64),
                "button.png", new Size(200, 60),
                "toggle.png", new Size(48, 20),
                "field.png", new Size(200, 20),
                "scroll.png", new Size(16, 48),
                "header.png", new Size(256, 16),
                "icons.png", new Size(256, 256)), ThemeGate.FILES);
    }

    @Test
    @DisplayName("with every file there at its size the theme is available, and on unless the player turned it off")
    void everyFileTurnsItOn() {
        Verdict verdict = ThemeGate.check(everyFile()::get);
        assertTrue(verdict.available());
        assertTrue(verdict.problems().isEmpty());
        assertTrue(ThemeGate.enabled(verdict, true));
        assertFalse(ThemeGate.enabled(verdict, false), "the config's switch turns it off");
    }

    @Test
    @DisplayName("without the art the theme is off whatever the config says, and the log gets one line naming every file")
    void noFilesNoTheme() {
        Verdict verdict = ThemeGate.check(name -> null);
        assertFalse(verdict.available());
        assertFalse(ThemeGate.enabled(verdict, true));
        String line = ThemeGate.logLine(verdict);
        assertFalse(line.contains("\n") || line.contains("\r"), "one line: " + line);
        for (String name : ThemeGate.FILES.keySet()) {
            assertTrue(line.contains(name + " missing"), line);
        }
    }

    @Test
    @DisplayName("any single file missing turns the whole theme off")
    void anyMissingFileTurnsItOff() {
        for (String name : ThemeGate.FILES.keySet()) {
            Map<String, Size> found = everyFile();
            found.remove(name);
            Verdict verdict = ThemeGate.check(found::get);
            assertFalse(verdict.available(), name + " is missing");
            assertFalse(ThemeGate.enabled(verdict, true), name + " is missing");
            assertEquals(1, verdict.problems().size());
            assertTrue(ThemeGate.logLine(verdict).contains(name), ThemeGate.logLine(verdict));
        }
    }

    @Test
    @DisplayName("a file at another size is as good as missing: it would be cut in the wrong places")
    void wrongSizeTurnsItOff() {
        Map<String, Size> found = everyFile();
        found.put("button.png", new Size(200, 20));
        Verdict verdict = ThemeGate.check(found::get);
        assertFalse(verdict.available());
        assertTrue(ThemeGate.logLine(verdict).contains("button.png is 200x20, not 200x60"), ThemeGate.logLine(verdict));
    }

    @Test
    @DisplayName("on the theme's dark panel a label too dark to read turns light grey, a bright one keeps its colour")
    void labelColoursOnThePanel() {
        assertEquals(0xD0D0D0, ThemeGate.panelTextColor(0x404040), "CustomNPCs' default label grey");
        assertEquals(0xD0D0D0, ThemeGate.panelTextColor(0x000000));
        assertEquals(0xFFFFFF, ThemeGate.panelTextColor(0xFFFFFF), "a title");
        assertEquals(0xA0A0A0, ThemeGate.panelTextColor(0xA0A0A0), "a hint");
        assertEquals(0xE23A2E, ThemeGate.panelTextColor(0xE23A2E), "a warning's red");
        assertEquals(0xFFD0D0D0, ThemeGate.panelTextColor(0xFF404040), "the alpha is kept");
    }
}

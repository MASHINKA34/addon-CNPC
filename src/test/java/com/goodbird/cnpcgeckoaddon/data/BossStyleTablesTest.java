package com.goodbird.cnpcgeckoaddon.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The five artwork tables, held to the one contract they all promise.
 *
 * <p>Every style id in a boss save is a plain string that came off a button, out of a tag
 * written by an older build, or out of a pack that has since lost the artwork. Each table
 * answers with its first entry when it does not recognise one, which is what keeps a missing
 * texture from becoming a crash - and what makes an id that stopped being recognised look
 * exactly like a builder's choice being quietly reset instead.</p>
 */
class BossStyleTablesTest {

    /** One artwork table: its ids, and the lookup that has to survive anything. */
    private record Table(String name, Supplier<List<String>> ids, Function<String, String> normalize) {
    }

    private static final List<Table> TABLES = List.of(
            new Table("AreaVfxStyles", () -> idsOf(AreaVfxStyles.values(), AreaVfxStyles.Style::id),
                    AreaVfxStyles::normalize),
            new Table("BossBarStyles", () -> idsOf(BossBarStyles.values(), BossBarStyles.Style::id),
                    BossBarStyles::normalize),
            new Table("BoulderStyles", () -> idsOf(BoulderStyles.values(), BoulderStyles.Style::id),
                    BoulderStyles::normalize),
            new Table("HookCordStyles", () -> idsOf(HookCordStyles.values(), HookCordStyles.Style::id),
                    HookCordStyles::normalize),
            new Table("BossChestStyles", () -> idsOf(BossChestStyles.values(), BossChestStyles.Style::id),
                    BossChestStyles::normalize));

    private static <T> List<String> idsOf(List<T> styles, Function<T, String> id) {
        List<String> ids = new ArrayList<>();
        for (T style : styles) {
            ids.add(id.apply(style));
        }
        return ids;
    }

    @Test
    @DisplayName("every table offers at least one style, and no id twice")
    void tablesAreNonEmptyAndUnique() {
        for (Table table : TABLES) {
            List<String> ids = table.ids().get();
            assertFalse(ids.isEmpty(), table.name() + " offers no styles at all");
            Set<String> unique = new HashSet<>(ids);
            assertEquals(ids.size(), unique.size(),
                    table.name() + " lists an id twice, so one of them can never be picked");
            for (String id : ids) {
                assertNotNull(id, table.name() + " has a style with no id");
                assertFalse(id.isBlank(), table.name() + " has a style with a blank id");
            }
        }
    }

    @Test
    @DisplayName("a style that exists is handed straight back")
    void knownIdsSurviveNormalisation() {
        for (Table table : TABLES) {
            for (String id : table.ids().get()) {
                assertEquals(id, table.normalize().apply(id),
                        table.name() + " changed " + id + ", which is one of its own styles");
            }
        }
    }

    @Test
    @DisplayName("anything else falls back to the first style rather than throwing")
    void unknownIdsFallBackToTheFirstStyle() {
        String[] rubbish = {null, "", "   ", "no_such_style", "minecraft:stone", "NONE",
                "../../etc/passwd", " ", "a".repeat(4096)};
        for (Table table : TABLES) {
            String fallback = table.ids().get().getFirst();
            for (String id : rubbish) {
                String got = table.normalize().apply(id);
                assertEquals(fallback, got, table.name() + " answered " + got
                        + " for an id it does not know: " + (id == null ? "null" : "\"" + id + "\""));
            }
        }
    }

    @Test
    @DisplayName("normalising is idempotent, so a saved id never drifts on reload")
    void normalisationIsIdempotent() {
        for (Table table : TABLES) {
            List<String> candidates = new ArrayList<>(table.ids().get());
            candidates.add("unknown");
            for (String id : candidates) {
                String once = table.normalize().apply(id);
                assertEquals(once, table.normalize().apply(once),
                        table.name() + " keeps changing " + id + " every time it is loaded");
            }
        }
    }

    @Test
    @DisplayName("the off switches read as off, and every other style as on")
    void theOffSwitchesAreRecognised() {
        assertFalse(AreaVfxStyles.isVisible(AreaVfxStyles.NONE));
        assertFalse(AreaVfxStyles.isVisible("no_such_style"),
                "an unknown vfx id falls back to none, so nothing should be scheduled for it");
        assertFalse(BossBarStyles.isEnabled(BossBarStyles.NONE));
        // The particle cord is the one that is not drawn from a texture.
        assertFalse(HookCordStyles.isTextured(HookCordStyles.PARTICLES));
        for (String id : idsOf(HookCordStyles.values(), HookCordStyles.Style::id)) {
            if (!HookCordStyles.PARTICLES.equals(id)) {
                assertTrue(HookCordStyles.isTextured(id), id + " should be drawn from its texture");
            }
        }
    }
}

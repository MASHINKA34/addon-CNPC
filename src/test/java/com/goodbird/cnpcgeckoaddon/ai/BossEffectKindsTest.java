package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every ability the rotation casts either leaves an effect running that its profile's end animation
 * waits for - a case of {@code TeleportPathController.isEffectRunning} - or leaves nothing and is
 * one of {@link BossAbilityKind#INSTANT_ABILITIES}, whose end animation the profile editor says never
 * plays. Exactly one of the two: a new ability has to take a side, or the editor tells the builder
 * the wrong thing about it.
 */
class BossEffectKindsTest {

    /** The kinds with a case of their own in isEffectRunning, written out here on purpose. */
    private static final Set<Integer> RUNNING = Set.of(
            BossAbilityKind.HOOK, BossAbilityKind.CAPTURE, BossAbilityKind.LEAP, BossAbilityKind.DASH,
            BossAbilityKind.CONE, BossAbilityKind.RANGED, BossAbilityKind.PLATFORM, BossAbilityKind.HURRICANE,
            BossAbilityKind.SHADOW, BossAbilityKind.SEISMIC, BossAbilityKind.RIFT, BossAbilityKind.VENT,
            BossAbilityKind.GEYSER, BossAbilityKind.BOULDER_RAIN, BossAbilityKind.TETHER, BossAbilityKind.GRAVITY,
            BossAbilityKind.MARK, BossAbilityKind.HUNT, BossAbilityKind.BEAM, BossAbilityKind.COCOON);

    private static final Path CONTROLLER = Path
            .of(System.getProperty("cnpcgeckoaddon.projectDir", "."))
            .resolve(Path.of("src", "main", "java", "com", "goodbird", "cnpcgeckoaddon", "ai",
                    "TeleportPathController.java"));

    private static final Pattern CASE = Pattern.compile("case\\s+([A-Z_]+)\\s*->");

    @Test
    @DisplayName("every cast ability is either running after its cast or instant, and never both")
    void everyCastAbilityTakesOneSide() {
        Set<Integer> instant = new TreeSet<>();
        for (int kind : BossAbilityKind.INSTANT_ABILITIES) {
            assertTrue(instant.add(kind), "kind " + kind + " is listed twice as instant");
        }
        for (int kind : BossAbilityKind.COMBO_ABILITIES) {
            boolean running = RUNNING.contains(kind);
            boolean isInstant = instant.contains(kind);
            assertTrue(running ^ isInstant, BossAbilityKind.LABELS[kind] + (running
                    ? " is both running after its cast and instant" : " is neither running after its cast nor instant"));
            assertEquals(isInstant, (BossAbilityKind.INSTANT_ALL & 1L << kind) != 0, "the mask says the same");
        }
        assertEquals(BossAbilityKind.COMBO_ABILITIES.length, RUNNING.size() + instant.size(),
                "nothing outside the cast abilities on either side");
    }

    @Test
    @DisplayName("the instant ones are the slam, the swing, the spit, the summon, the corridor, the stone and the cover")
    void theInstantOnes() {
        assertEquals(Set.of(BossAbilityKind.AREA, BossAbilityKind.MELEE, BossAbilityKind.FLUID, BossAbilityKind.SUMMON,
                        BossAbilityKind.LINE, BossAbilityKind.BOULDER, BossAbilityKind.COVER),
                Set.copyOf(Arrays.stream(BossAbilityKind.INSTANT_ABILITIES).boxed().toList()));
    }

    @Test
    @DisplayName("the list written here is the cases isEffectRunning really has")
    void theListIsTheController() throws IOException {
        String code = Files.readString(CONTROLLER, StandardCharsets.UTF_8);
        int start = code.indexOf("private boolean isEffectRunning(");
        assertTrue(start >= 0, "isEffectRunning is no longer in " + CONTROLLER);
        int end = code.indexOf("default -> false;", start);
        assertTrue(end > start, "isEffectRunning no longer ends in default -> false");
        Set<Integer> cases = new TreeSet<>();
        Matcher matcher = CASE.matcher(code.substring(start, end));
        while (matcher.find()) {
            cases.add(BossAbility.valueOf(matcher.group(1)).kind());
        }
        assertEquals(new TreeSet<>(RUNNING), cases);
    }
}

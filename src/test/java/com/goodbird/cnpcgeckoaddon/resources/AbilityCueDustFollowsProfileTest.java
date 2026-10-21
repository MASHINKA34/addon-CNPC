package com.goodbird.cnpcgeckoaddon.resources;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Holds a boss' cues set to the ability's dust to the colour of the ability's profile.
 *
 * <p>A cue's dust looked up by kind - {@code cue.emitDust(..., BossAbilityKind.GEYSER)} - is the
 * kind's colour whatever the builder gave the ability's profile, and nothing on screen says the puff
 * kept the old colour while the warning took the new one. The callers hand in the dust of the phase
 * the ability was cast in instead; this keeps a new ability, or a new cue of an old one, from going
 * back to the kind. The barrier paints in the boss bar's accent and the plain npc's lob has no
 * profile at all, so both keep theirs.</p>
 */
class AbilityCueDustFollowsProfileTest {

    private static final Path AI = Path
            .of(System.getProperty("cnpcgeckoaddon.projectDir", "."))
            .resolve(Path.of("src", "main", "java", "com", "goodbird", "cnpcgeckoaddon", "ai"));

    /** Who may still colour a cue by kind, and why: see the class comment. */
    private static final Set<String> EXEMPT = Set.of("BossBarrierRuntime.java", "NpcRangedAttackGoal.java");

    private static final Pattern CALL = Pattern.compile("\\.(emitDust|emitRising|resolve)\\s*\\(");
    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\n]*");
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);

    @Test
    @DisplayName("no boss ability's cue in ai/ takes its dust by kind rather than from the cast's phase")
    void everyAbilityCueTakesTheProfilesDust() throws IOException {
        Set<String> offenders = new TreeSet<>();
        int sources = 0;
        int calls = 0;
        try (Stream<Path> files = Files.list(AI)) {
            for (Path source : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                sources++;
                String name = source.getFileName().toString();
                String code = LINE_COMMENT.matcher(BLOCK_COMMENT.matcher(
                        Files.readString(source, StandardCharsets.UTF_8)).replaceAll(" ")).replaceAll(" ");
                Matcher matcher = CALL.matcher(code);
                while (matcher.find()) {
                    calls++;
                    for (String argument : topLevelArguments(code, matcher.end())) {
                        if (argument.startsWith("BossAbilityKind.") && !EXEMPT.contains(name)) {
                            offenders.add(name + ": ." + matcher.group(1) + "(..., " + argument + ")");
                        }
                    }
                }
            }
        }
        assertTrue(sources > 50, "only " + sources + " sources were found under " + AI);
        assertTrue(calls > 30, "only " + calls + " cue calls were found, so the pattern no longer finds them");
        assertTrue(offenders.isEmpty(), "hand the cue BossTelegraphUtil.dust(phase, kind) or the cast's "
                + "settings.dust(kind) instead: " + offenders);
    }

    @Test
    @DisplayName("the argument reader sees a kind passed straight in, and not one inside a dust lookup")
    void theReaderTellsTheTwoApart() {
        String direct = "cue.emitDust(level, x, y, z, 0.0D, f(1, 2), 0.0D, 0.0D, BossAbilityKind.LEAP);";
        String nested = "cue.emitDust(level, x, y, z, 0.0D, 0.0D, 0.0D, 0.0D, BossTelegraphUtil.dust(phase, "
                + "BossAbilityKind.LEAP));";
        assertTrue(topLevelArguments(direct, direct.indexOf('(') + 1).contains("BossAbilityKind.LEAP"));
        assertTrue(topLevelArguments(nested, nested.indexOf('(') + 1).stream()
                .noneMatch(argument -> argument.startsWith("BossAbilityKind.")));
    }

    /** The arguments of the call whose opening bracket ends just before {@code from}, at its own depth. */
    private static List<String> topLevelArguments(String code, int from) {
        List<String> arguments = new ArrayList<>();
        int depth = 0;
        int start = from;
        for (int i = from; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '"') {
                i = skipString(code, i);
            } else if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                if (depth == 0) {
                    arguments.add(code.substring(start, i).trim());
                    return arguments;
                }
                depth--;
            } else if (c == ',' && depth == 0) {
                arguments.add(code.substring(start, i).trim());
                start = i + 1;
            }
        }
        return arguments;
    }

    private static int skipString(String code, int open) {
        for (int i = open + 1; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == '"') {
                return i;
            }
        }
        return code.length();
    }
}

package com.goodbird.cnpcgeckoaddon.resources;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Asserts that no screen puts an English sentence on the screen directly.
 *
 * <p>{@link LanguageParityTest} answers the other half of the question - that every key the
 * sources name is defined in both locales - and cannot see this half at all: a label built
 * from a literal names no key, so it is invisible to a check that walks keys. Both times a
 * screen was written that way it survived every review and both locale files, and turned up
 * only as an English word sitting in the middle of a Russian editor.</p>
 *
 * <p>Literals are recognised as text rather than as keys the way a reader does: a capital
 * letter followed by a space or a colon is a sentence, and a run of dotted lowercase is an
 * id. That leaves format strings, single letters and ids alone without a list to maintain.</p>
 */
class GuiTextIsTranslatableTest {

    private static final Path GUI = Path
            .of(System.getProperty("cnpcgeckoaddon.projectDir", "."))
            .resolve(Path.of("src", "main", "java", "com", "goodbird", "cnpcgeckoaddon", "client", "gui"));

    private static final Pattern LITERAL = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\n]*");
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);

    /**
     * Text the screens are allowed to hold, each because translating it would say less than
     * the character itself does.
     */
    private static final Set<String> ALLOWED = Set.of("X");

    @Test
    @DisplayName("no screen holds display text instead of a translation key")
    void everyScreenUsesKeys() throws IOException {
        Set<String> offenders = new TreeSet<>();
        try (Stream<Path> sources = Files.walk(GUI)) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                String code = stripComments(Files.readString(source, StandardCharsets.UTF_8));
                Matcher matcher = LITERAL.matcher(code);
                while (matcher.find()) {
                    String literal = matcher.group(1);
                    if (looksLikeDisplayText(literal)) {
                        offenders.add(GUI.getFileName().resolve(source.getFileName()) + ": \"" + literal + "\"");
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "these read as English on screen rather than as translation keys: " + offenders);
    }

    /**
     * Whether this literal would be read as a sentence by whoever sees it: it opens with a
     * capital and then either carries a space or ends the way a field label does.
     */
    private static boolean looksLikeDisplayText(String literal) {
        if (literal.length() < 3 || ALLOWED.contains(literal)) {
            return false;
        }
        if (!Character.isUpperCase(literal.charAt(0)) || !Character.isLetter(literal.charAt(1))) {
            return false;
        }
        // A dotted id may well start with a capital; it is still an id and not a sentence.
        if (literal.indexOf('.') >= 0 && literal.indexOf(' ') < 0) {
            return false;
        }
        return literal.indexOf(' ') >= 0 || literal.endsWith(":");
    }

    private static String stripComments(String code) {
        return LINE_COMMENT.matcher(BLOCK_COMMENT.matcher(code).replaceAll(" ")).replaceAll(" ");
    }
}

package com.goodbird.cnpcgeckoaddon;

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
 * Keeps {@link IntegrationCheck} covering every interface a mixin bolts onto CustomNPCs.
 *
 * <p>The check is only worth having if it is complete: a hook added later and left out of it
 * is exactly the silent half-broken install it exists to report, except now with a line at
 * startup saying everything is fine. So the list is compared against the mixins themselves
 * rather than trusted - a new {@code implements} on a mixin fails here until it is named.</p>
 *
 * <p>Read out of the sources the way {@code LanguageParityTest} reads them: the mixin classes
 * cannot be loaded in a plain JVM, and their {@code implements} clause is the whole fact
 * being checked.</p>
 */
class IntegrationCheckTest {

    private static final Path SOURCES = Path
            .of(System.getProperty("cnpcgeckoaddon.projectDir", "."))
            .resolve(Path.of("src", "main", "java", "com", "goodbird", "cnpcgeckoaddon"));

    /** The {@code implements A, B} clause of a mixin class declaration, over several lines. */
    private static final Pattern IMPLEMENTS = Pattern.compile(
            "class\\s+\\w+[^{]*?\\bimplements\\s+([\\w\\s,.<>]+?)\\s*\\{", Pattern.DOTALL);


    @Test
    @DisplayName("every interface a mixin adds to CustomNPCs is on the startup check")
    void everyMixinInterfaceIsChecked() throws IOException {
        String check = Files.readString(SOURCES.resolve("IntegrationCheck.java"), StandardCharsets.UTF_8);
        Set<String> unchecked = new TreeSet<>();
        try (Stream<Path> sources = Files.walk(SOURCES.resolve("mixin"))) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                String code = Files.readString(source, StandardCharsets.UTF_8);
                if (!code.contains("@Mixin")) {
                    continue;
                }
                Matcher matcher = IMPLEMENTS.matcher(code);
                while (matcher.find()) {
                    for (String name : matcher.group(1).split(",")) {
                        String simple = name.trim();
                        simple = simple.substring(simple.lastIndexOf('.') + 1);
                        // Only the addon's own interfaces: a mixin also re-declares the
                        // vanilla ones its target already implements, and those are the
                        // game's own contract rather than a hook this addon installed.
                        if (simple.isEmpty()
                                || !Files.exists(SOURCES.resolve("mixin").resolve(simple + ".java"))) {
                            continue;
                        }
                        if (!check.contains(simple + ".class")) {
                            unchecked.add(simple + "  (" + source.getFileName() + ")");
                        }
                    }
                }
            }
        }
        assertTrue(unchecked.isEmpty(),
                "these are added to CustomNPCs by a mixin but never verified at startup: " + unchecked);
    }

    @Test
    @DisplayName("the startup check reads the sources it claims to")
    void theCheckIsNotEmpty() throws IOException {
        String check = Files.readString(SOURCES.resolve("IntegrationCheck.java"), StandardCharsets.UTF_8);
        int hooks = check.split("new Hook\\(", -1).length - 1;
        assertTrue(hooks >= 8, "the startup check lists only " + hooks + " hooks");
    }
}

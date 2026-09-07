package com.goodbird.cnpcgeckoaddon.resources;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageParityTest {

    private static final Pattern KEY = Pattern.compile("\"(cnpcgeckoaddon\\.[a-zA-Z0-9_.]+)\"");
    private static final Path SOURCES = Path
            .of(System.getProperty("cnpcgeckoaddon.projectDir", "."))
            .resolve(Path.of("src", "main", "java"));

    private static Set<String> keysOf(String locale) {
        String resource = "/assets/cnpcgeckoaddon/lang/" + locale + ".json";
        try (InputStream input = LanguageParityTest.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException(resource + " is missing from the jar");
            }
            JsonObject json = JsonParser
                    .parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            return new TreeSet<>(json.keySet());
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    @Test
    @DisplayName("both locales carry exactly the same keys")
    void localesAgree() {
        Set<String> english = keysOf("en_us");
        Set<String> russian = keysOf("ru_ru");
        assertFalse(english.isEmpty(), "en_us parsed to nothing");

        Set<String> missing = new TreeSet<>(english);
        missing.removeAll(russian);
        assertTrue(missing.isEmpty(), "ru_ru is missing: " + missing);

        Set<String> extra = new TreeSet<>(russian);
        extra.removeAll(english);
        assertTrue(extra.isEmpty(), "ru_ru has keys en_us does not: " + extra);
    }

    @Test
    @DisplayName("every translation key the sources name is defined")
    void everyReferencedKeyIsTranslated() throws IOException {
        Set<String> english = keysOf("en_us");
        Set<String> undefined = new TreeSet<>();
        try (Stream<Path> sources = Files.walk(SOURCES)) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                Matcher matcher = KEY.matcher(Files.readString(source, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    String key = matcher.group(1);
                    // A key built by appending a style id is only ever a prefix here.
                    if (!key.endsWith(".") && !english.contains(key)) {
                        undefined.add(key + "  (" + SOURCES.relativize(source) + ")");
                    }
                }
            }
        }
        assertTrue(undefined.isEmpty(), "used in code but absent from en_us: " + undefined);
    }
}

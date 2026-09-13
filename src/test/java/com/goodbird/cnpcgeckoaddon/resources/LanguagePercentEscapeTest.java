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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Asserts that no translation holds a per cent sign the formatters will choke on.
 *
 * <p>A screen puts a label up with {@code I18n.get(key)}, which hands the translation to
 * {@code String.format} whether it has arguments or not. "Width / sag (%)" is not a format
 * string, so formatting it throws and the player reads "Format error: ..." where the label
 * should be. The vanilla text path is no kinder: {@code TranslatableContents} accepts only
 * {@code %s} and {@code %%}, and gives up on the rest.</p>
 *
 * <p>Both paths print {@code %%} as a single per cent sign, so that is what a translation has
 * to hold - and it has to hold it in every locale, because a screen reading fine in English
 * while the same label is broken in Russian is exactly how the last one was missed. Fixing it
 * in the data rather than in the screens is what makes this a rule the next label obeys for
 * free, whichever of the two dozen screens it is added to.</p>
 */
class LanguagePercentEscapeTest {

    /**
     * One per cent token at a time: an escaped pair, an argument, or a bare sign. Matching the
     * pair first is what stops the second half of a {@code %%} from reading as a bare sign.
     */
    private static final Pattern PERCENT = Pattern.compile("%%|%(?:\\d+\\$)?[sd]|%");

    private static Map<String, String> valuesOf(String locale) {
        String resource = "/assets/cnpcgeckoaddon/lang/" + locale + ".json";
        try (InputStream input = LanguagePercentEscapeTest.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException(resource + " is missing from the jar");
            }
            JsonObject json = JsonParser
                    .parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            Map<String, String> values = new LinkedHashMap<>();
            json.entrySet().forEach(entry -> values.put(entry.getKey(), entry.getValue().getAsString()));
            return values;
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    @Test
    @DisplayName("no translation holds a bare per cent sign")
    void everyPercentSignIsEscaped() {
        TreeSet<String> bare = new TreeSet<>();
        for (String locale : new String[]{"en_us", "ru_ru"}) {
            Map<String, String> values = valuesOf(locale);
            assertTrue(values.size() > 100, locale + " parsed to almost nothing");
            values.forEach((key, value) -> {
                Matcher matcher = PERCENT.matcher(value);
                while (matcher.find()) {
                    if ("%".equals(matcher.group())) {
                        bare.add(locale + ": " + key + " = \"" + value + "\"");
                    }
                }
            });
        }
        assertTrue(bare.isEmpty(),
                "a lone % breaks String.format, so these show as \"Format error\" on the screen "
                        + "that puts them up - write it as %% instead: " + bare);
    }

    @Test
    @DisplayName("an escaped per cent sign reaches the screen as one sign")
    void everyEscapedSignFormatsToOne() {
        for (String locale : new String[]{"en_us", "ru_ru"}) {
            valuesOf(locale).forEach((key, value) -> {
                if (!value.contains("%%") || takesArguments(value)) {
                    return;
                }
                // Exactly what I18n.get does to a label with nothing to fill in.
                assertEquals(value.replace("%%", "%"), String.format(value),
                        locale + ": " + key + " is not printed the way it is written");
            });
        }
    }

    /** Whether the value has a slot for something, which a label put up on its own has not. */
    private static boolean takesArguments(String value) {
        Matcher matcher = PERCENT.matcher(value);
        while (matcher.find()) {
            if (!"%%".equals(matcher.group())) {
                return true;
            }
        }
        return false;
    }
}

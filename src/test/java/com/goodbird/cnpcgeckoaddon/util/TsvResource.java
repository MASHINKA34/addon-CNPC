package com.goodbird.cnpcgeckoaddon.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Reads one of the addon's own tab separated tables the way the resolvers read them. */
public final class TsvResource {

    public record Row(String key, String value, int line) {
    }

    private TsvResource() {
    }

    public static List<Row> read(String resource) {
        List<Row> rows = new ArrayList<>();
        try (InputStream input = TsvResource.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException(resource + " is missing from the jar");
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                int number = 0;
                while ((line = reader.readLine()) != null) {
                    number++;
                    if (line.isBlank() || line.startsWith("#")) {
                        continue;
                    }
                    int separator = line.indexOf('\t');
                    if (separator <= 0 || separator == line.length() - 1) {
                        continue;
                    }
                    rows.add(new Row(line.substring(0, separator),
                            line.substring(separator + 1), number));
                }
            }
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        return rows;
    }
}

package com.goodbird.cnpcgeckoaddon.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves the default hitbox size recorded for a bundled mob model.
 *
 * <p>Every bundled model used to fall back to the same 0.7 by 2.0 box, which is
 * a humanoid size and is wrong for almost every imported mob: the same box
 * covered a 1.6 block soul and a 14 block dragon. The sizes in
 * META-INF/MOBMODEL_HITBOXES.tsv are derived from the geometry itself by
 * scripts/generate_mob_hitboxes.py, so a model's default box now wraps the
 * model that is actually drawn.</p>
 *
 * <p>The table is read from the addon jar rather than from the resource pack,
 * so it resolves identically on a dedicated server and on a client.</p>
 */
public final class MobModelHitboxResolver {
    private static final String TABLE = "/META-INF/MOBMODEL_HITBOXES.tsv";

    private static volatile Map<String, float[]> sizes;

    private MobModelHitboxResolver() {
    }

    /**
     * @return the recorded {@code {width, height}} in blocks, or null when the
     * model is not one of the bundled ones.
     */
    public static float[] resolve(String model) {
        if (model == null || model.isEmpty()) {
            return null;
        }
        float[] size = table().get(model);
        return size == null ? null : new float[]{size[0], size[1]};
    }

    public static boolean isKnown(String model) {
        return model != null && !model.isEmpty() && table().containsKey(model);
    }

    private static Map<String, float[]> table() {
        Map<String, float[]> loaded = sizes;
        if (loaded == null) {
            synchronized (MobModelHitboxResolver.class) {
                loaded = sizes;
                if (loaded == null) {
                    loaded = load();
                    sizes = loaded;
                }
            }
        }
        return loaded;
    }

    private static Map<String, float[]> load() {
        Map<String, float[]> loaded = new HashMap<>();
        try (InputStream input = MobModelHitboxResolver.class.getResourceAsStream(TABLE)) {
            if (input == null) {
                return Map.of();
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank() || line.startsWith("#")) {
                        continue;
                    }
                    String[] fields = line.split("\t");
                    if (fields.length != 3) {
                        continue;
                    }
                    try {
                        float width = Float.parseFloat(fields[1]);
                        float height = Float.parseFloat(fields[2]);
                        if (width > 0.0F && height > 0.0F) {
                            loaded.put(fields[0], new float[]{width, height});
                        }
                    } catch (NumberFormatException ignored) {
                        // A malformed row must not cost every other model its size.
                    }
                }
            }
        } catch (IOException | RuntimeException ignored) {
            return Map.of();
        }
        return Map.copyOf(loaded);
    }
}

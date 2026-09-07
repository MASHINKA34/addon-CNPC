package com.goodbird.cnpcgeckoaddon.utils;

import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * Decides which of a mod's texture files belongs to one of its models, by name alone.
 *
 * <p>The bundles carry hundreds of models and hundreds of textures with no mapping between
 * them beyond what their authors called the files, so a model with no recorded default is
 * matched against every texture in its namespace and the closest name wins. Everything here
 * is a pure function of two strings, which is what makes the scoring testable at all -
 * {@link MobModelTextureResolver} itself cannot be, because it has to walk the live resource
 * packs to know what exists.</p>
 */
public final class MobModelNameMatcher {

    /** Below this the closest name is still not close enough, and the npc skin is kept. */
    public static final int MATCH_THRESHOLD = 3200;

    private MobModelNameMatcher() {
    }

    public static int score(String modelName, ResourceLocation texture) {
        String textureName = fileStem(texture.getPath(), ".png");
        String modelNormalized = normalize(modelName);
        String textureNormalized = normalize(textureName);
        String modelCanonical = canonicalModel(modelName);
        String textureCanonical = stripTrailingDigits(textureNormalized);

        int score;
        if (modelNormalized.equals(textureNormalized)) {
            score = 10000;
        } else if (modelCanonical.equals(textureCanonical)) {
            score = 9600;
        } else if (modelCanonical.length() >= 4 && textureCanonical.length() >= 4
                && (modelCanonical.contains(textureCanonical) || textureCanonical.contains(modelCanonical))) {
            int shortest = Math.min(modelCanonical.length(), textureCanonical.length());
            int longest = Math.max(modelCanonical.length(), textureCanonical.length());
            score = 8200 + (shortest * 1200 / longest);
        } else {
            int longest = Math.max(modelCanonical.length(), textureCanonical.length());
            score = longest == 0
                    ? 0
                    : (longest - levenshtein(modelCanonical, textureCanonical)) * 7000 / longest;
        }

        if (isAuxiliaryTexture(textureName)) {
            score -= 5000;
        }
        return score;
    }

    public static boolean isAuxiliaryTexture(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith("_e")
                || lower.endsWith("_eyes")
                || lower.contains("_eyes_")
                || lower.endsWith("_layer")
                || lower.contains("_layer_")
                || lower.endsWith("_overlay")
                || lower.contains("_overlay_")
                || lower.endsWith("_mask")
                || lower.contains("_glow")
                || lower.contains("glowmask")
                || lower.contains("emissive")
                || lower.contains("specular")
                || lower.contains("_normal");
    }

    static String canonicalModel(String value) {
        String normalized = normalize(value);
        if (normalized.startsWith("model")) {
            normalized = normalized.substring(5);
        }

        boolean changed;
        do {
            changed = false;
            for (String suffix : new String[]{"model", "rework", "entity", "adapted", "focused"}) {
                if (normalized.endsWith(suffix) && normalized.length() > suffix.length()) {
                    normalized = normalized.substring(0, normalized.length() - suffix.length());
                    changed = true;
                }
            }
        } while (changed);

        return stripTrailingDigits(normalized);
    }

    public static String normalize(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char character = Character.toLowerCase(value.charAt(i));
            if (Character.isLetterOrDigit(character)) {
                result.append(character);
            }
        }
        return result.toString();
    }

    static String stripTrailingDigits(String value) {
        int end = value.length();
        while (end > 0 && Character.isDigit(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }

    public static String fileStem(String path, String suffix) {
        String name = fileName(path);
        return name.endsWith(suffix) ? name.substring(0, name.length() - suffix.length()) : name;
    }

    public static String fileName(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    static int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int column = 0; column <= right.length(); column++) {
            previous[column] = column;
        }

        for (int row = 1; row <= left.length(); row++) {
            current[0] = row;
            for (int column = 1; column <= right.length(); column++) {
                int substitution = previous[column - 1]
                        + (left.charAt(row - 1) == right.charAt(column - 1) ? 0 : 1);
                current[column] = Math.min(
                        Math.min(current[column - 1] + 1, previous[column] + 1),
                        substitution);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }}

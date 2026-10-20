package com.goodbird.cnpcgeckoaddon.data;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What a boss preset may be called: the name a builder typed, made into one a file can carry on any
 * server - lower case letters, digits, {@code _} and {@code -}, at most {@value #MAX_LENGTH} of them.
 *
 * <p>The same rule on both sides: the presets screen checks a name before it asks, the server cleans
 * it again before it touches a file, and the command goes through it too. A name with nothing left
 * after cleaning is refused rather than guessed at.</p>
 */
public final class BossPresetNames {

    /** The longest name a preset may have. */
    public static final int MAX_LENGTH = 32;

    /** What every preset file ends with; the name is what comes before it. */
    public static final String EXTENSION = ".snbt";

    private static final Pattern VALID = Pattern.compile("[a-z0-9_-]{1," + MAX_LENGTH + "}");

    /**
     * Names Windows keeps for its devices, whatever comes after them: a preset called {@code con}
     * would write to the console there instead of to a file, so none of them is a preset name.
     */
    private static final Set<String> RESERVED = Set.of("con", "prn", "aux", "nul",
            "com0", "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
            "lpt0", "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9");

    private BossPresetNames() {
    }

    /**
     * The name a typed one is saved under: lower case, every run of spaces one {@code _}, anything
     * else that is not a letter, digit, {@code _} or {@code -} dropped, cut to {@value #MAX_LENGTH}
     * characters - {@code "Demo 2!"} is {@code demo_2}. Empty when nothing usable is left, which is
     * a refusal.
     */
    public static String clean(String typed) {
        if (typed == null) {
            return "";
        }
        String lower = typed.trim().toLowerCase(Locale.ROOT);
        StringBuilder name = new StringBuilder();
        boolean pendingSpace = false;
        for (int i = 0; i < lower.length() && name.length() < MAX_LENGTH; i++) {
            char c = lower.charAt(i);
            if (Character.isWhitespace(c)) {
                // A run of spaces is one separator, and only between two kept characters.
                pendingSpace = !name.isEmpty();
                continue;
            }
            if (!isAllowed(c)) {
                continue;
            }
            if (pendingSpace) {
                // No room for the separator and a character after it: the name ends here rather than on a "_".
                if (name.length() + 2 > MAX_LENGTH) {
                    break;
                }
                name.append('_');
                pendingSpace = false;
            }
            name.append(c);
        }
        String cleaned = name.toString();
        return isValid(cleaned) ? cleaned : "";
    }

    /** Whether this is a name a preset may already have: what {@link #clean} gives back unchanged. */
    public static boolean isValid(String name) {
        return name != null && VALID.matcher(name).matches() && !RESERVED.contains(name);
    }

    /** The preset a file in the presets folder holds, or null for a file that is not one. */
    public static String nameOfFile(String fileName) {
        if (fileName == null || !fileName.endsWith(EXTENSION)) {
            return null;
        }
        String name = fileName.substring(0, fileName.length() - EXTENSION.length());
        return isValid(name) ? name : null;
    }

    /** The file a preset of this name lives in, inside the presets folder. */
    public static String fileName(String name) {
        return name + EXTENSION;
    }

    private static boolean isAllowed(char c) {
        return c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '-';
    }
}

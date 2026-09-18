package com.goodbird.cnpcgeckoaddon.utils;

import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The one door resource ids built out of somebody else's text come through.
 *
 * <p>{@link ResourceLocation#parse} and {@link ResourceLocation#fromNamespaceAndPath} throw on
 * the first character an id may not hold - a capital, a space, a backslash - and the text
 * handed to the addon comes out of scripts, editor fields and hand-written NBT, on threads
 * where an exception is a crash. Here the same text comes back as the caller's fallback, with
 * one line in the log saying which text it was. {@code ResourceLocationParseTest} keeps every
 * other call in the sources to the throwing two down to ids spelt out in the code.</p>
 */
public final class ResourceIds {

    private static final Logger LOGGER = LoggerFactory.getLogger("cnpcgeckoaddon");

    /** A script can make up a new bad id every tick; past this many the log has made its point. */
    private static final int MAX_REPORTED = 64;

    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    /** A script says the same thing again every time it runs: one line per owner in ten seconds. */
    private static final long SCRIPT_WARN_INTERVAL_NANOS = 10_000_000_000L;

    /** Past this many owners the oldest answers are forgotten rather than kept for good. */
    private static final int MAX_SCRIPT_OWNERS = 1024;

    private static final Map<String, Long> SCRIPT_WARNED_AT = new ConcurrentHashMap<>();

    private ResourceIds() {
    }

    /**
     * {@code namespace:path}, or {@code fallback} when either half is not a legal one.
     *
     * @param fallback what to use instead; may be null where the caller treats that as "none"
     */
    public static ResourceLocation pathOrDefault(String namespace, String path, ResourceLocation fallback) {
        ResourceLocation built = namespace == null || path == null
                ? null : ResourceLocation.tryBuild(namespace, path);
        if (built == null) {
            report(namespace + ":" + path, fallback);
            return fallback;
        }
        return built;
    }

    /**
     * A whole id as it was typed, or {@code fallback} when it is not a legal one.
     *
     * <p>Surrounding blanks are forgiven, the way every editor field of the addon forgives them.</p>
     */
    public static ResourceLocation parseOrDefault(String id, ResourceLocation fallback) {
        ResourceLocation parsed = id == null ? null : ResourceLocation.tryParse(id.trim());
        if (parsed == null) {
            report(String.valueOf(id), fallback);
            return fallback;
        }
        return parsed;
    }

    /**
     * An id a script handed over, or {@code fallback} when it is not a legal one.
     *
     * <p>For the script API, whose calls come again every time the script runs: a bad id is said
     * at most once in ten seconds per {@code owner} - a block's position, say - rather than once
     * for good, so a script fixed and broken again is heard again.</p>
     */
    public static ResourceLocation parseFromScript(String id, ResourceLocation fallback, String what, String owner) {
        ResourceLocation parsed = id == null ? null : ResourceLocation.tryParse(id.trim());
        if (parsed != null) {
            return parsed;
        }
        long now = System.nanoTime();
        String key = owner + "/" + what;
        Long last = SCRIPT_WARNED_AT.get(key);
        if (last == null || now - last >= SCRIPT_WARN_INTERVAL_NANOS) {
            if (SCRIPT_WARNED_AT.size() >= MAX_SCRIPT_OWNERS) {
                SCRIPT_WARNED_AT.clear();
            }
            SCRIPT_WARNED_AT.put(key, now);
            LOGGER.warn("A script set the {} of {} to '{}', which is not a valid resource id; using {} instead",
                    what, owner, id, fallback);
        }
        return fallback;
    }

    /** Whether {@link #parseOrDefault} would take this text as it is. */
    public static boolean isValid(String id) {
        return id != null && ResourceLocation.tryParse(id.trim()) != null;
    }

    private static void report(String text, ResourceLocation fallback) {
        if (REPORTED.size() < MAX_REPORTED && REPORTED.add(text)) {
            LOGGER.warn("'{}' is not a valid resource id, using {} instead", text, fallback);
        }
    }
}

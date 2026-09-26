package com.goodbird.cnpcgeckoaddon.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The addon's settings that are one player's own business, kept in
 * {@code config/cnpcgeckoaddon-client.toml}.
 *
 * <p>Only how this player's screens look belongs here, never anything a boss or an npc does:
 * that lives on the npc and travels with it to every player and every server. Registered for
 * the physical client only; nothing here touches a client class, so the common entrypoint can
 * name it.</p>
 */
public final class AddonClientConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue GUI_THEME = BUILDER
            .comment("true: the addon's screens are drawn in its own theme, when the theme's textures are present;",
                    "false: they keep the look CustomNPCs gives its screens.",
                    "The switch in the boss menu changes this while the game runs.")
            .define("gui.theme", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private AddonClientConfig() {
    }

    /**
     * Whether the player wants the addon's theme. Before the file is read - or on a side that never
     * reads it - that is the default, the theme.
     */
    public static boolean guiTheme() {
        return !SPEC.isLoaded() || GUI_THEME.get();
    }

    /** Changes the choice and writes it to the file, so it outlives the session. */
    public static void setGuiTheme(boolean value) {
        if (SPEC.isLoaded()) {
            GUI_THEME.set(value);
            GUI_THEME.save();
        }
    }
}

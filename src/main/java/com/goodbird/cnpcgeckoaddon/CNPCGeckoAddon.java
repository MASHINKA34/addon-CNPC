package com.goodbird.cnpcgeckoaddon;

import com.goodbird.cnpcgeckoaddon.config.AddonClientConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(CNPCGeckoAddon.MODID)
public class CNPCGeckoAddon {
    public static final String MODID = "cnpcgeckoaddon";

    public CNPCGeckoAddon(ModContainer container, Dist dist) {
        if (dist.isClient()) {
            // How this player's screens look: config/cnpcgeckoaddon-client.toml.
            container.registerConfig(ModConfig.Type.CLIENT, AddonClientConfig.SPEC);
        }
    }
}

package com.astranautics;

import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AstraNautics - minimal Forge 1.20.1 mod entry point.
 *
 * <p>This build only proves that the mod loads inside Minecraft Forge 1.20.1.
 * Nothing is registered (no blocks, items, entities or world generation).</p>
 */
@Mod(AstraNautics.MODID)
public class AstraNautics {
    public static final String MODID = "astranautics";
    private static final Logger LOGGER = LoggerFactory.getLogger("AstraNautics");

    public AstraNautics() {
        LOGGER.info("AstraNautics loaded (Minecraft 1.20.1 / Forge)");
    }
}

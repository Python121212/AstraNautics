package com.astranautics;

import net.minecraftforge.fml.ModList;

/** MOD 情報取得用の小さなヘルパー。 */
public final class ModListHelper {

    private ModListHelper() {
    }

    /** この MOD のバージョン文字列（取得できなければ "unknown"）。 */
    public static String version() {
        return ModList.get()
                .getModContainerById(AstraNautics.MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
    }
}

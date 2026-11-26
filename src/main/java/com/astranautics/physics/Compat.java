package com.astranautics.physics;

import com.astranautics.AstraNautics;
import com.astranautics.Config;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * 他 MOD との互換レイヤー。
 *
 * <p>「すべてのブロックに対応」を掲げつつ、特殊な BlockEntity を持つブロック
 * （メカニズム MOD のマシン等）でトラブルが起きた場合は、コンフィグで除外できます。</p>
 *
 * <pre>
 *   [compatibility]
 *     blockBlacklist = ["create:mechanical_bearing"]
 *     modBlacklist   = ["some_heavy_mod"]
 * </pre>
 */
public final class Compat {

    private Compat() {
    }

    /** コンフィグ（ブロック／MOD 単位）で禁止されているか。 */
    public static boolean isBlocked(BlockState state) {
        Block block = state.getBlock();
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
        if (id == null) {
            return false;
        }
        try {
            List<? extends String> blockedIds = Config.COMMON.blockBlacklist.get();
            List<? extends String> blockedMods = Config.COMMON.modBlacklist.get();
            if (blockedMods.contains(id.getNamespace())) {
                return true;
            }
            for (String entry : blockedIds) {
                if (entry != null && entry.equals(id.toString())) {
                    return true;
                }
            }
            return false;
        } catch (Exception exception) {
            // コンフィグ未ロード時（データパック読み込みなど）は許可
            AstraNautics.LOGGER.debug("[AstraNautics] 互換設定の読み取りに失敗", exception);
            return false;
        }
    }

    /** そのブロックがアストラノーティクスのものか。 */
    public static boolean isOwnBlock(BlockState state) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return id != null && AstraNautics.MOD_ID.equals(id.getNamespace());
    }

    /** クラスタの最大サイズ。 */
    public static int maxClusterSize() {
        try {
            return Config.COMMON.maxClusterSize.get();
        } catch (Exception exception) {
            return 512;
        }
    }

    /** 浮力が有効か。 */
    public static boolean buoyancyEnabled() {
        try {
            return Config.COMMON.enableBuoyancy.get();
        } catch (Exception exception) {
            return true;
        }
    }
}

package com.astranautics.physics;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ブロック素材ごとの物性値（AstraLite 用）。
 *
 * <p>鉄は重く弾まない、氷は滑る、スライムはよく跳ねる、羊毛は軽い…といった差を
 * ブロックの種類から推定します。将来クラスタ剛体へ拡張するときは、
 * この値をセルごとに合算して「構造体の質量・摩擦・反発」を決めます。</p>
 */
public record MaterialProfile(
        double mass,
        double friction,     // 0 = つるつる, 1 = よく止まる
        double restitution,  // 反発係数
        double drag,         // 空気抵抗の強さ（1 = 普通）
        boolean buoyant      // 浮きやすい（水中で上に押される）
) {

    public static final MaterialProfile DEFAULT =
            new MaterialProfile(1.0D, 0.75D, 0.30D, 0.01D, false);

    /** ブロックから物性を推定する（毎 tick 呼ばないよう、BlockEntity 側でキャッシュする）。 */
    public static MaterialProfile of(BlockState state) {
        if (state == null || state.isAir()) {
            return DEFAULT;
        }
        Block block = state.getBlock();

        // --- 特別扱い ---
        if (block == Blocks.SLIME_BLOCK) {
            return new MaterialProfile(1.1D, 0.90D, 0.85D, 0.02D, false);
        }
        if (block == Blocks.HONEY_BLOCK) {
            return new MaterialProfile(1.2D, 0.98D, 0.02D, 0.04D, false);
        }
        if (block == Blocks.ICE || block == Blocks.PACKED_ICE
                || block == Blocks.BLUE_ICE || block == Blocks.FROSTED_ICE) {
            return new MaterialProfile(0.9D, 0.02D, 0.18D, 0.005D, false);
        }
        if (block == Blocks.SOUL_SAND || block == Blocks.MUD || block == Blocks.SOUL_SOIL) {
            return new MaterialProfile(1.3D, 0.95D, 0.05D, 0.05D, false);
        }
        if (block == Blocks.SPONGE || block == Blocks.WET_SPONGE) {
            return new MaterialProfile(0.5D, 0.85D, 0.10D, 0.08D, true);
        }

        SoundType sound = state.getSoundType();

        // --- 金属系：重い・よく滑る・弾まない ---
        if (sound == SoundType.METAL || sound == SoundType.COPPER
                || sound == SoundType.NETHERITE_BLOCK || sound == SoundType.ANVIL
                || sound == SoundType.LODESTONE || sound == SoundType.CHAIN) {
            return new MaterialProfile(2.4D, 0.45D, 0.12D, 0.008D, false);
        }
        // --- ガラス・アメジスト：軽め・よく滑る ---
        if (sound == SoundType.GLASS || sound == SoundType.AMETHYST) {
            return new MaterialProfile(0.7D, 0.35D, 0.20D, 0.006D, false);
        }
        // --- ウール・カーペット：軽い・摩擦大・弾まない ---
        if (sound == SoundType.WOOL || sound == SoundType.MOSS_CARPET
                || sound == SoundType.WET_GRASS) {
            return new MaterialProfile(0.35D, 0.95D, 0.02D, 0.10D, false);
        }
        // --- 草・土・砂：普通 ---
        if (sound == SoundType.GRASS || sound == SoundType.GRAVEL || sound == SoundType.SAND
                || sound == SoundType.SOUL_SAND || sound == SoundType.ROOTED_DIRT
                || sound == SoundType.CROP || sound == SoundType.HARD_CROP) {
            return new MaterialProfile(0.9D, 0.80D, 0.15D, 0.03D, false);
        }
        // --- 木：軽め ---
        if (sound == SoundType.WOOD || sound == SoundType.CHERRY_WOOD
                || sound == SoundType.BAMBOO_WOOD || sound == SoundType.NETHER_WOOD
                || sound == SoundType.BAMBOO || sound == SoundType.SCAFFOLDING) {
            return new MaterialProfile(0.6D, 0.65D, 0.28D, 0.015D, true);
        }
        // --- 石・レンガ：重い ---
        if (sound == SoundType.STONE || sound == SoundType.DEEPSLATE
                || sound == SoundType.DEEPSLATE_BRICKS || sound == SoundType.DEEPSLATE_TILES
                || sound == SoundType.TUFF || sound == SoundType.CALCITE
                || sound == SoundType.BASALT || sound == SoundType.POLISHED_DEEPSLATE
                || sound == SoundType.NETHER_BRICKS || sound == SoundType.BONE_BLOCK) {
            return new MaterialProfile(2.0D, 0.80D, 0.10D, 0.01D, false);
        }
        // --- 雪・粉雪：とても軽い ---
        if (sound == SoundType.SNOW || sound == SoundType.POWDER_SNOW) {
            return new MaterialProfile(0.2D, 0.70D, 0.05D, 0.20D, true);
        }

        // --- タグによるフォールバック ---
        if (state.is(BlockTags.LOGS)) {
            return new MaterialProfile(0.6D, 0.65D, 0.28D, 0.015D, true);
        }
        if (state.is(BlockTags.ICE)) {
            return new MaterialProfile(0.9D, 0.02D, 0.18D, 0.005D, false);
        }
        if (state.is(BlockTags.WOOL)) {
            return new MaterialProfile(0.35D, 0.95D, 0.02D, 0.10D, false);
        }
        if (state.is(BlockTags.SAND)) {
            return new MaterialProfile(0.9D, 0.85D, 0.12D, 0.03D, false);
        }

        return DEFAULT;
    }
}

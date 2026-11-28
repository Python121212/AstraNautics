package com.astranautics.physics;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * How heavy a block is.
 *
 * <p>Every block has a weight. This used to be a single formula over hardness alone, which made
 * a bed and an anvil differ by less than a factor of two and left most blocks feeling alike. The
 * numbers here are relative densities (water = 1) taken from the material a block is obviously
 * made of, so wool is nearly nothing, stone is solid, and a block of iron is an anchor.
 * {@code physics.massScale} multiplies the whole lot, so a pack can make every ship float or
 * sink as one without touching the ratios between blocks.</p>
 *
 * <p>Blocks that no rule recognises still get a weight from their hardness, so "all blocks have
 * a mass" keeps holding for other mods' blocks.</p>
 */
public final class BlockMasses {
    /** Fallback density range, so an unknown block is never weightless or absurd. */
    private static final double MIN_FALLBACK = 0.2D;
    private static final double MAX_FALLBACK = 6.0D;

    private BlockMasses() {
    }

    /** Relative density of a block, before the global scale is applied. */
    public static double density(BlockState state) {
        Block block = state.getBlock();

        // --- by tag first: those also catch other mods' blocks that reuse the vanilla tags --
        if (state.is(BlockTags.LEAVES) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)
                || state.is(BlockTags.CROPS) || state.is(BlockTags.REPLACEABLE_BY_TREES)) {
            return 0.15D;
        }
        if (state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS)) {
            return 0.25D;
        }
        if (state.is(BlockTags.BUTTONS) || state.is(BlockTags.RAILS) || state.is(BlockTags.TORCHES)) {
            return 0.3D;
        }
        if (state.is(BlockTags.PLANKS) || state.is(BlockTags.WOODEN_SLABS) || state.is(BlockTags.WOODEN_STAIRS)) {
            return 0.6D;
        }
        if (state.is(BlockTags.LOGS) || state.is(BlockTags.BAMBOO_BLOCKS)) {
            return 0.7D;
        }
        if (state.is(BlockTags.IMPERMEABLE) || block == Blocks.GLASS) {
            return 0.8D;
        }
        if (state.is(BlockTags.SAND) || state.is(BlockTags.DIRT) || block == Blocks.GRAVEL) {
            return 1.4D;
        }
        if (state.is(BlockTags.TERRACOTTA) || state.is(BlockTags.CONCRETE) || state.is(BlockTags.CONCRETE_POWDER)) {
            return 1.8D;
        }
        if (state.is(BlockTags.STONE_BRICKS) || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.BASE_STONE_NETHER)) {
            return 2.2D;
        }
        if (state.is(BlockTags.ICE)) {
            return 0.95D;
        }
        if (state.is(BlockTags.STONE_SLABS) || state.is(BlockTags.STONE_STAIRS)) {
            return 2.2D;
        }

        // --- then by name, for the blocks no tag describes ------------------------------
        String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
        return densityOfName(path, block.defaultDestroyTime());
    }

    /**
     * Density from a block's registry name and hardness.
     *
     * <p>Kept free of any registry lookup so it can be tested for what it is: a table of
     * materials.</p>
     */
    static double densityOfName(String path, float hardness) {
        if (path.endsWith("_wool")) {
            return 0.25D;
        }
        if (path.endsWith("_planks")) {
            return 0.6D;
        }
        if (path.endsWith("_log") || path.endsWith("_wood") || path.equals("log") || path.equals("wood")) {
            return 0.7D;
        }
        if (path.endsWith("_leaves") || path.equals("leaves")) {
            return 0.15D;
        }
        if (path.endsWith("_concrete") || path.endsWith("_concrete_powder")) {
            return 1.8D;
        }
        if (path.endsWith("_terracotta") || path.equals("terracotta")) {
            return 1.8D;
        }
        if (path.endsWith("_glass") || path.equals("glass") || path.equals("tinted_glass")) {
            return 0.8D;
        }

        // --- the heavy ones: a metal cube is the densest thing a ship can be built of ---
        if (path.equals("gold_block")) {
            return 12.0D;
        }
        if (path.equals("netherite_block") || path.equals("obsidian") || path.equals("crying_obsidian")) {
            return 9.0D;
        }
        if (path.equals("iron_block")) {
            return 7.0D;
        }
        if (path.equals("copper_block") || path.equals("diamond_block") || path.equals("emerald_block")) {
            return 6.5D;
        }
        if (path.equals("ancient_debris")) {
            return 6.0D;
        }
        if (path.equals("lapis_block") || path.equals("redstone_block") || path.equals("quartz_block")) {
            return 2.6D;
        }
        if (path.equals("stone") || path.equals("deepslate") || path.equals("granite")
                || path.equals("diorite") || path.equals("andesite") || path.equals("tuff")
                || path.equals("calcite") || path.equals("stone_bricks")) {
            return 2.2D;
        }
        if (path.equals("dirt") || path.equals("sand") || path.equals("gravel")
                || path.equals("clay") || path.equals("grass_block")) {
            return 1.4D;
        }
        if (path.equals("amethyst_block")) {
            return 1.6D;
        }
        if (path.equals("ice") || path.equals("packed_ice") || path.equals("blue_ice")) {
            return 0.95D;
        }

        // --- anything else: hardness, the way it used to be ------------------------------
        if (hardness < 0.0F) {
            // Bedrock and friends: unbreakable, and heavy with it.
            return 9.0D;
        }
        return Math.min(MAX_FALLBACK, Math.max(MIN_FALLBACK, 0.4D + 0.55D * hardness));
    }

    /** The mass of one block of this state, with the configured scale applied. */
    public static double massOf(BlockState state, double scale) {
        return massOfDensity(density(state), scale);
    }

    /** Applies the global scale to a density, never letting a block become weightless. */
    static double massOfDensity(double density, double scale) {
        return Math.max(0.01D, density * scale);
    }
}

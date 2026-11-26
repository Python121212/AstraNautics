package com.astranautics.registry;

import com.astranautics.AstraNautics;
import com.astranautics.block.PhysicalizedBlock;
import com.astranautics.block.EnginePartBlock;
import com.astranautics.block.NozzleMoldBlock;
import com.astranautics.block.ShipHelmBlock;
import com.astranautics.block.ThrusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** ブロックの登録。 */
public final class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, AstraNautics.MOD_ID);

    /** 物理化されたブロック（見た目は元ブロック、動きはエンティティ描画）。 */
    public static final RegistryObject<Block> PHYSICALIZED = BLOCKS.register("physicalized",
            () -> new PhysicalizedBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(0.2F)
                    .sound(SoundType.METAL)
                    .noCollission()
                    .noOcclusion()
                    .dynamicShape()));

    /** 空中で物理化したときに出現する「物理コア」。 */
    public static final RegistryObject<Block> PHYSICS_CORE = BLOCKS.register("physics_core",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN)
                    .strength(0.6F, 6.0F)
                    .sound(SoundType.AMETHYST)
                    .lightLevel(state -> 7)));

    /** スラスター（船の推力源）。 */
    public static final RegistryObject<Block> THRUSTER = BLOCKS.register("thruster",
            () -> new ThrusterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(ThrusterBlock.LIT) ? 12 : 0)));

    /** 舵輪（船の操縦席）。 */
    public static final RegistryObject<Block> SHIP_HELM = BLOCKS.register("ship_helm",
            () -> new ShipHelmBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.0F, 4.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    /** ノズル金型（金属を入れてノズルを押し出す）。 */
    public static final RegistryObject<Block> NOZZLE_MOLD = BLOCKS.register("nozzle_mold",
            () -> new NozzleMoldBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)));

    /* ------------------------------------------------------------------ */
    /* 自作エンジンの部品                                                   */
    /* ------------------------------------------------------------------ */

    /** 部品ブロック（エンジンの構成要素）。 */
    public static final java.util.Map<com.astranautics.engine.EnginePart, RegistryObject<Block>>
            ENGINE_PARTS = new java.util.EnumMap<>(com.astranautics.engine.EnginePart.class);

    static {
        for (com.astranautics.engine.EnginePart part : com.astranautics.engine.EnginePart.values()) {
            if (part == com.astranautics.engine.EnginePart.NOZZLE) {
                // ノズルだけ「形状」を持てる専用ブロック
                ENGINE_PARTS.put(part, BLOCKS.register("engine_" + part.id,
                        () -> new com.astranautics.block.NozzleBlock(
                                BlockBehaviour.Properties.of()
                                        .mapColor(MapColor.METAL)
                                        .strength(2.5F, 6.0F)
                                        .sound(SoundType.METAL))));
                continue;
            }
            ENGINE_PARTS.put(part, BLOCKS.register("engine_" + part.id,
                    () -> new EnginePartBlock(part, BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(2.5F, 6.0F)
                            .sound(SoundType.METAL))));
        }
    }

    /** 部品のブロックを取得する。 */
    public static Block enginePart(com.astranautics.engine.EnginePart part) {
        RegistryObject<Block> block = ENGINE_PARTS.get(part);
        return block == null ? null : block.get();
    }

    private ModBlocks() {
    }
}

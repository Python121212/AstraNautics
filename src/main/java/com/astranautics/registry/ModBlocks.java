package com.astranautics.registry;

import com.astranautics.AstraNautics;
import com.astranautics.block.PhysicalizedBlock;
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

    private ModBlocks() {
    }
}

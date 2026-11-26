package com.astranautics.registry;

import com.astranautics.AstraNautics;
import com.astranautics.block.PhysicalizedBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** ブロックエンティティの登録。 */
public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, AstraNautics.MOD_ID);

    public static final RegistryObject<BlockEntityType<PhysicalizedBlockEntity>> PHYSICALIZED =
            BLOCK_ENTITIES.register("physicalized",
                    () -> BlockEntityType.Builder
                            .of(PhysicalizedBlockEntity::new, ModBlocks.PHYSICALIZED.get())
                            .build(null));

    private ModBlockEntities() {
    }
}

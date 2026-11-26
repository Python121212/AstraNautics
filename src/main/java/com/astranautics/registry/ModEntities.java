package com.astranautics.registry;

import com.astranautics.AstraNautics;
import com.astranautics.physics.ShipEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** エンティティの登録（航行する船）。 */
public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, AstraNautics.MOD_ID);

    /** 航行中の船（クラスタ剛体）。 */
    public static final RegistryObject<EntityType<ShipEntity>> SHIP = ENTITIES.register("ship",
            () -> EntityType.Builder.<ShipEntity>of(ShipEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("astranautics:ship"));

    /** 艦載砲の弾。 */
    public static final RegistryObject<EntityType<com.astranautics.physics.TurretShotEntity>>
            TURRET_SHOT = ENTITIES.register("turret_shot",
                    () -> EntityType.Builder
                            .<com.astranautics.physics.TurretShotEntity>of(
                                    com.astranautics.physics.TurretShotEntity::new,
                                    MobCategory.MISC)
                            .sized(0.4F, 0.4F)
                            .clientTrackingRange(6)
                            .updateInterval(1)
                            .build("astranautics:turret_shot"));

    private ModEntities() {
    }
}

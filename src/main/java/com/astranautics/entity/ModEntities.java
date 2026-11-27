package com.astranautics.entity;

import com.astranautics.AstraNautics;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, AstraNautics.MODID);

    public static final RegistryObject<EntityType<PhysicsShip>> GRAVITY_SHIP =
            ENTITIES.register("physics_ship", () -> EntityType.Builder
                    .<PhysicsShip>of(PhysicsShip::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .build("physics_ship"));

    public static void register(IEventBus eventBus) {
        ENTITIES.register(eventBus);
    }
}

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

    public static final RegistryObject<EntityType<GravityShip>> GRAVITY_SHIP =
            ENTITIES.register("gravity_ship", () -> EntityType.Builder
                    .<GravityShip>of(GravityShip::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .build("gravity_ship"));

    public static void register(IEventBus eventBus) {
        ENTITIES.register(eventBus);
    }
}

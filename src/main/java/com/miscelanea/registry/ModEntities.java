package com.miscelanea.registry;

import com.miscelanea.Miscelanea;
import com.miscelanea.aspira.DesatascadorEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Miscelanea.MOD_ID);

    public static final RegistryObject<EntityType<DesatascadorEntity>> DESATASCADOR = ENTITIES.register("desatascador",
            () -> EntityType.Builder.<DesatascadorEntity>of(DesatascadorEntity::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build("desatascador"));

    public static final RegistryObject<EntityType<com.miscelanea.carcel.CarroChivato>> CARRO_CHIVATO = ENTITIES.register("carro_chivato",
            () -> EntityType.Builder.<com.miscelanea.carcel.CarroChivato>of(com.miscelanea.carcel.CarroChivato::new, MobCategory.MISC)
                    .sized(0.98F, 1.6F)
                    .clientTrackingRange(8)
                    .build("carro_chivato"));

    public static final RegistryObject<EntityType<com.miscelanea.carcel.Arrojable>> ARROJABLE = ENTITIES.register("arrojable",
            () -> EntityType.Builder.<com.miscelanea.carcel.Arrojable>of(com.miscelanea.carcel.Arrojable::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("arrojable"));

    private ModEntities() {}
}

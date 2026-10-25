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

    public static final RegistryObject<EntityType<com.miscelanea.tinta.TintaProyectil>> TINTA = ENTITIES.register("tinta",
            () -> EntityType.Builder.<com.miscelanea.tinta.TintaProyectil>of(com.miscelanea.tinta.TintaProyectil::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F).clientTrackingRange(6).updateInterval(1).build("tinta"));

    private ModEntities() {}
}

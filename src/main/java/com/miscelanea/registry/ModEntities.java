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

    public static final RegistryObject<EntityType<com.miscelanea.armas.GanchoEntity>> GANCHO = ENTITIES.register("gancho",
            () -> EntityType.Builder.<com.miscelanea.armas.GanchoEntity>of(com.miscelanea.armas.GanchoEntity::new, MobCategory.MISC)
                    .sized(0.3F, 0.3F).clientTrackingRange(8).updateInterval(1).build("gancho"));

    public static final RegistryObject<EntityType<com.miscelanea.armas.CoheteEntity>> COHETE = ENTITIES.register("cohete",
            () -> EntityType.Builder.<com.miscelanea.armas.CoheteEntity>of(com.miscelanea.armas.CoheteEntity::new, MobCategory.MISC)
                    .sized(0.3F, 0.3F).clientTrackingRange(8).updateInterval(1).build("cohete"));

    public static final RegistryObject<EntityType<com.miscelanea.llave.LlaveLanzada>> LLAVE_LANZADA = ENTITIES.register("llave_lanzada",
            () -> EntityType.Builder.<com.miscelanea.llave.LlaveLanzada>of(com.miscelanea.llave.LlaveLanzada::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(8).updateInterval(1).build("llave_lanzada"));

    public static final RegistryObject<EntityType<com.miscelanea.llave.HechizoEntity>> HECHIZO = ENTITIES.register("hechizo",
            () -> EntityType.Builder.<com.miscelanea.llave.HechizoEntity>of(com.miscelanea.llave.HechizoEntity::new, MobCategory.MISC)
                    .sized(0.3F, 0.3F).clientTrackingRange(8).updateInterval(1).build("hechizo"));

    public static final RegistryObject<EntityType<com.miscelanea.hacha.HachaLanzada>> HACHA_LANZADA = ENTITIES.register("hacha_lanzada",
            () -> EntityType.Builder.<com.miscelanea.hacha.HachaLanzada>of(com.miscelanea.hacha.HachaLanzada::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(10).updateInterval(1).build("hacha_lanzada"));

    public static final RegistryObject<EntityType<com.miscelanea.bichos.BichoEntity>> BICHO = ENTITIES.register("bicho",
            () -> EntityType.Builder.<com.miscelanea.bichos.BichoEntity>of(com.miscelanea.bichos.BichoEntity::new, MobCategory.AMBIENT)
                    .sized(0.35F, 0.3F).clientTrackingRange(6).build("bicho"));

    private ModEntities() {}
}

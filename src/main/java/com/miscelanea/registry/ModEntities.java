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

    public static final RegistryObject<EntityType<com.miscelanea.carcel.enemigos.Guardia>> GUARDIA = ENTITIES.register("guardia",
            () -> EntityType.Builder.<com.miscelanea.carcel.enemigos.Guardia>of(com.miscelanea.carcel.enemigos.Guardia::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F).clientTrackingRange(10).build("guardia"));
    public static final RegistryObject<EntityType<com.miscelanea.carcel.enemigos.Griton>> GRITON = ENTITIES.register("griton",
            () -> EntityType.Builder.<com.miscelanea.carcel.enemigos.Griton>of(com.miscelanea.carcel.enemigos.Griton::new, MobCategory.MONSTER)
                    .sized(0.6F, 2.2F).clientTrackingRange(10).build("griton"));
    public static final RegistryObject<EntityType<com.miscelanea.carcel.enemigos.Jefe>> JEFE = ENTITIES.register("jefe",
            () -> EntityType.Builder.<com.miscelanea.carcel.enemigos.Jefe>of(com.miscelanea.carcel.enemigos.Jefe::new, MobCategory.MONSTER)
                    .sized(0.9F, 2.4F).clientTrackingRange(12).build("jefe"));

    public static final RegistryObject<EntityType<com.miscelanea.carcel.enemigos.Pusher>> PUSHER = ENTITIES.register("pusher",
            () -> EntityType.Builder.<com.miscelanea.carcel.enemigos.Pusher>of(com.miscelanea.carcel.enemigos.Pusher::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F).clientTrackingRange(10).build("pusher"));
    public static final RegistryObject<EntityType<com.miscelanea.carcel.enemigos.Skinner>> SKINNER = ENTITIES.register("skinner",
            () -> EntityType.Builder.<com.miscelanea.carcel.enemigos.Skinner>of(com.miscelanea.carcel.enemigos.Skinner::new, MobCategory.MONSTER)
                    .sized(0.7F, 2.3F).clientTrackingRange(12).build("skinner"));
    public static final RegistryObject<EntityType<com.miscelanea.programa.RigProyectil>> RIG_PROYECTIL = ENTITIES.register("rig_proyectil",
            () -> EntityType.Builder.<com.miscelanea.programa.RigProyectil>of(com.miscelanea.programa.RigProyectil::new, MobCategory.MISC)
                    .sized(0.3F, 0.3F).clientTrackingRange(6).updateInterval(5).build("rig_proyectil"));

    /** Atributos de los enemigos (bus del mod). */
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD)
    public static final class Atributos {
        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void registrar(net.minecraftforge.event.entity.EntityAttributeCreationEvent e) {
            e.put(GUARDIA.get(), com.miscelanea.carcel.enemigos.Guardia.atributos().build());
            e.put(GRITON.get(), com.miscelanea.carcel.enemigos.Griton.atributos().build());
            e.put(JEFE.get(), com.miscelanea.carcel.enemigos.Jefe.atributos().build());
            e.put(PUSHER.get(), com.miscelanea.carcel.enemigos.Pusher.atributos().build());
            e.put(SKINNER.get(), com.miscelanea.carcel.enemigos.Skinner.atributos().build());
        }
    }

    private ModEntities() {}
}

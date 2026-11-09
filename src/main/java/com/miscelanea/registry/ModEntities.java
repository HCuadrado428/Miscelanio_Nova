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

    public static final RegistryObject<EntityType<com.miscelanea.tirolina.Polea>> POLEA = ENTITIES.register("polea",
            () -> EntityType.Builder.<com.miscelanea.tirolina.Polea>of(com.miscelanea.tirolina.Polea::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F).clientTrackingRange(10).updateInterval(1).noSummon().build("polea"));
    public static final RegistryObject<EntityType<com.miscelanea.feria.AsientoFeria>> ASIENTO_FERIA = ENTITIES.register("asiento_feria",
            () -> EntityType.Builder.<com.miscelanea.feria.AsientoFeria>of(com.miscelanea.feria.AsientoFeria::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(10).updateInterval(1).noSummon().build("asiento_feria"));
    public static final RegistryObject<EntityType<com.miscelanea.feria.AsientoFeria>> CABINA_NORIA = ENTITIES.register("cabina_noria",
            () -> EntityType.Builder.<com.miscelanea.feria.AsientoFeria>of(com.miscelanea.feria.AsientoFeria::new, MobCategory.MISC)
                    .sized(1.9F, 2.6F).clientTrackingRange(12).updateInterval(1).noSummon().build("cabina_noria"));
    public static final RegistryObject<EntityType<com.miscelanea.feria.CocheChoque>> COCHE_CHOQUE = ENTITIES.register("coche_choque",
            () -> EntityType.Builder.<com.miscelanea.feria.CocheChoque>of(com.miscelanea.feria.CocheChoque::new, MobCategory.MISC)
                    .sized(1.3F, 0.7F).clientTrackingRange(10).updateInterval(1).build("coche_choque"));
    public static final RegistryObject<EntityType<com.miscelanea.feria.VagonetaBruja>> VAGONETA_BRUJA = ENTITIES.register("vagoneta_bruja",
            () -> EntityType.Builder.<com.miscelanea.feria.VagonetaBruja>of(com.miscelanea.feria.VagonetaBruja::new, MobCategory.MISC)
                    .sized(0.98F, 0.7F).clientTrackingRange(8).build("vagoneta_bruja"));
    public static final RegistryObject<EntityType<com.miscelanea.bolos.BolaBolos>> BOLA_BOLOS = ENTITIES.register("bola_bolos",
            () -> EntityType.Builder.<com.miscelanea.bolos.BolaBolos>of(com.miscelanea.bolos.BolaBolos::new, MobCategory.MISC)
                    .sized(0.34F, 0.34F).clientTrackingRange(8).updateInterval(1).noSummon().build("bola_bolos"));
    public static final RegistryObject<EntityType<com.miscelanea.dardos.Dardo>> DARDO = ENTITIES.register("dardo",
            () -> EntityType.Builder.<com.miscelanea.dardos.Dardo>of(com.miscelanea.dardos.Dardo::new, MobCategory.MISC)
                    .sized(0.2F, 0.2F).clientTrackingRange(6).updateInterval(1).noSummon().build("dardo"));
    public static final RegistryObject<EntityType<com.miscelanea.feria.BalonEntity>> BALON = ENTITIES.register("balon",
            () -> EntityType.Builder.<com.miscelanea.feria.BalonEntity>of(com.miscelanea.feria.BalonEntity::new, MobCategory.MISC)
                    .sized(0.24F, 0.24F).clientTrackingRange(8).updateInterval(1).noSummon().build("balon"));
    public static final RegistryObject<EntityType<com.miscelanea.petanca.BolaPetanca>> BOLA_PETANCA = ENTITIES.register("bola_petanca",
            () -> EntityType.Builder.<com.miscelanea.petanca.BolaPetanca>of(com.miscelanea.petanca.BolaPetanca::new, MobCategory.MISC)
                    .sized(0.26F, 0.26F).clientTrackingRange(8).updateInterval(1).noSummon().build("bola_petanca"));
    public static final RegistryObject<EntityType<com.miscelanea.muebles.Asiento>> ASIENTO = ENTITIES.register("asiento",
            () -> EntityType.Builder.<com.miscelanea.muebles.Asiento>of(com.miscelanea.muebles.Asiento::new, MobCategory.MISC)
                    .sized(0.01F, 0.01F).clientTrackingRange(10).noSummon().build("asiento"));
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

    public static final RegistryObject<EntityType<com.miscelanea.armas.PlasmaEntity>> PLASMA = ENTITIES.register("plasma",
            () -> EntityType.Builder.<com.miscelanea.armas.PlasmaEntity>of(com.miscelanea.armas.PlasmaEntity::new, MobCategory.MISC)
                    .sized(0.7F, 0.7F).clientTrackingRange(10).updateInterval(1).build("plasma"));
    public static final RegistryObject<EntityType<com.miscelanea.armas.VorticeEntity>> VORTICE = ENTITIES.register("vortice",
            () -> EntityType.Builder.<com.miscelanea.armas.VorticeEntity>of(com.miscelanea.armas.VorticeEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(10).updateInterval(1).build("vortice"));
    public static final RegistryObject<EntityType<com.miscelanea.armas.OndaEntity>> ONDA = ENTITIES.register("onda",
            () -> EntityType.Builder.<com.miscelanea.armas.OndaEntity>of(com.miscelanea.armas.OndaEntity::new, MobCategory.MISC)
                    .sized(0.2F, 0.2F).clientTrackingRange(8).updateInterval(10).build("onda"));
    public static final RegistryObject<EntityType<com.miscelanea.armas.MazoLanzado>> MAZO_LANZADO = ENTITIES.register("mazo_lanzado",
            () -> EntityType.Builder.<com.miscelanea.armas.MazoLanzado>of(com.miscelanea.armas.MazoLanzado::new, MobCategory.MISC)
                    .sized(0.9F, 0.9F).clientTrackingRange(10).updateInterval(1).build("mazo_lanzado"));
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

    // ---- Pistola (bengala del revólver: se ve de lejos)
    public static final RegistryObject<EntityType<com.miscelanea.armasfuego.pistola.BengalaEntity>> REVOLVER_BENGALA = ENTITIES.register("revolver_bengala",
            () -> EntityType.Builder.<com.miscelanea.armasfuego.pistola.BengalaEntity>of(com.miscelanea.armasfuego.pistola.BengalaEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F).clientTrackingRange(16).updateInterval(2).fireImmune().build("revolver_bengala"));
    // ---- Rifle
    public static final RegistryObject<EntityType<com.miscelanea.armasfuego.rifle.BalaRifle>> BALA_RIFLE = ENTITIES.register("bala_rifle",
            () -> EntityType.Builder.<com.miscelanea.armasfuego.rifle.BalaRifle>of(com.miscelanea.armasfuego.rifle.BalaRifle::new, MobCategory.MISC)
                    .sized(0.1F, 0.1F).clientTrackingRange(12).updateInterval(1).noSummon().build("bala_rifle"));
    public static final RegistryObject<EntityType<com.miscelanea.armasfuego.rifle.GanchoTirolina>> GANCHO_TIROLINA = ENTITIES.register("gancho_tirolina",
            () -> EntityType.Builder.<com.miscelanea.armasfuego.rifle.GanchoTirolina>of(com.miscelanea.armasfuego.rifle.GanchoTirolina::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F).clientTrackingRange(10).updateInterval(1).noSummon().build("gancho_tirolina"));

    private ModEntities() {}
}

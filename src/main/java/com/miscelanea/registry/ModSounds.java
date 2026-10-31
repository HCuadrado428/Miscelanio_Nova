package com.miscelanea.registry;

import com.miscelanea.Miscelanea;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Miscelanea.MOD_ID);

    /** Zumbido de la aspiradora. */
    public static final RegistryObject<SoundEvent> ASPIRAR = register("aspirar");
    /** Pitido de oídos del flashbang. */
    public static final RegistryObject<SoundEvent> PITIDO = register("pitido");
    /** Chispazo corto de la porra. */
    public static final RegistryObject<SoundEvent> CHISPAZO = register("chispazo");
    /** Descarga larga del agarre. */
    public static final RegistryObject<SoundEvent> DESCARGA = register("descarga");

    /** Zumbido del rayo de la medigun (en bucle). */
    public static final RegistryObject<SoundEvent> MEDIGUN_RAYO = register("medigun_rayo");
    public static final RegistryObject<SoundEvent> MEDIGUN_CONECTAR = register("medigun_conectar");
    public static final RegistryObject<SoundEvent> UBER_ACTIVAR = register("uber_activar");
    public static final RegistryObject<SoundEvent> UBER_LISTA = register("uber_lista");
    public static final RegistryObject<SoundEvent> UBER_REBOTE = register("uber_rebote");

    public static final RegistryObject<SoundEvent> NUCLEO_CARGA = register("nucleo_carga");
    /** Rugido del rayo del núcleo (en bucle; lo para el cliente al acabar). */
    public static final RegistryObject<SoundEvent> NUCLEO_RAYO = register("nucleo_rayo");
    public static final RegistryObject<SoundEvent> NUCLEO_FIN = register("nucleo_fin");

    public static final RegistryObject<SoundEvent> ESCOPETA_DISPARO = register("escopeta_disparo");
    public static final RegistryObject<SoundEvent> ESCOPETA_RECARGA = register("escopeta_recarga");
    public static final RegistryObject<SoundEvent> GANCHO_LANZAR = register("gancho_lanzar");
    public static final RegistryObject<SoundEvent> GANCHO_ENGANCHAR = register("gancho_enganchar");
    public static final RegistryObject<SoundEvent> LANZACOHETES_DISPARO = register("lanzacohetes_disparo");
    public static final RegistryObject<SoundEvent> LANZACOHETES_RECARGA = register("lanzacohetes_recarga");
    public static final RegistryObject<SoundEvent> COHETE_EXPLOSION = register("cohete_explosion");

    public static final RegistryObject<SoundEvent> LLAVE_GOLPE = register("llave_golpe");
    public static final RegistryObject<SoundEvent> LLAVE_IMPACTO = register("llave_impacto");
    public static final RegistryObject<SoundEvent> LLAVE_REMATE = register("llave_remate");
    public static final RegistryObject<SoundEvent> LLAVE_LANZAR = register("llave_lanzar");
    public static final RegistryObject<SoundEvent> HECHIZO_PIRO = register("hechizo_piro");
    public static final RegistryObject<SoundEvent> HECHIZO_HIELO = register("hechizo_hielo");
    public static final RegistryObject<SoundEvent> HECHIZO_CURA = register("hechizo_cura");
    public static final RegistryObject<SoundEvent> CERRADURA = register("cerradura");

    public static final RegistryObject<SoundEvent> HACHA_GOLPE = register("hacha_golpe");
    public static final RegistryObject<SoundEvent> HACHA_IMPACTO = register("hacha_impacto");
    public static final RegistryObject<SoundEvent> HACHA_LANZAR = register("hacha_lanzar");
    public static final RegistryObject<SoundEvent> HACHA_VOLVER = register("hacha_volver");
    public static final RegistryObject<SoundEvent> HACHA_ATRAPAR = register("hacha_atrapar");
    public static final RegistryObject<SoundEvent> ESCUDO_BLOQUEO = register("escudo_bloqueo");
    public static final RegistryObject<SoundEvent> ESCUDO_PARRY = register("escudo_parry");
    public static final RegistryObject<SoundEvent> ESCUDO_ABRIR = register("escudo_abrir");
    public static final RegistryObject<SoundEvent> RUNICO = register("runico");
    public static final RegistryObject<SoundEvent> ESCARCHA = register("escarcha");
    public static final RegistryObject<SoundEvent> PUNETAZO = register("punetazo");

    @SuppressWarnings("unchecked")
    public static final RegistryObject<SoundEvent>[] RADIO = new RegistryObject[]{register("radio_0"), register("radio_1"), register("radio_2")};
    public static final RegistryObject<SoundEvent> RADIO_ESTATICA = register("radio_estatica");
    public static final RegistryObject<SoundEvent> PIPBOY_ABRIR = register("pipboy_abrir");
    public static final RegistryObject<SoundEvent> PIPBOY_CLIC = register("pipboy_clic");
    public static final RegistryObject<SoundEvent> PIPBOY_ENCENDER = register("pipboy_encender");
    public static final RegistryObject<SoundEvent> PIPBOY_PONER = register("pipboy_poner");


    // ---- Consola de bolsillo
    public static final RegistryObject<SoundEvent> GB_MOVER = register("gb_mover");
    public static final RegistryObject<SoundEvent> GB_GIRAR = register("gb_girar");
    public static final RegistryObject<SoundEvent> GB_LINEA = register("gb_linea");
    public static final RegistryObject<SoundEvent> GB_COMER = register("gb_comer");
    public static final RegistryObject<SoundEvent> GB_PUNTO = register("gb_punto");
    public static final RegistryObject<SoundEvent> GB_REBOTE = register("gb_rebote");
    public static final RegistryObject<SoundEvent> GB_DISPARO = register("gb_disparo");
    public static final RegistryObject<SoundEvent> GB_EXPLOSION = register("gb_explosion");
    public static final RegistryObject<SoundEvent> GB_ALETEO = register("gb_aleteo");
    public static final RegistryObject<SoundEvent> GB_FIN = register("gb_fin");
    public static final RegistryObject<SoundEvent> GB_INICIO = register("gb_inicio");
    public static final RegistryObject<SoundEvent> GB_MUSICA = register("gb_musica");

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name,
                () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(Miscelanea.MOD_ID, name)));
    }

    private ModSounds() {}
}

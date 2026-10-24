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

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name,
                () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(Miscelanea.MOD_ID, name)));
    }

    private ModSounds() {}
}

package com.miscelanea.client.voz.plugin;

import com.miscelanea.client.voz.VozCliente;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import java.util.UUID;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Plugin de Simple Voice Chat (dependencia OPCIONAL). Esta clase es la ÚNICA que toca la API del voicechat y no la
 * referencia nadie del mod: la descubre el propio voicechat por la anotación cuando está instalado; si no lo está,
 * nunca se carga y no pasa nada. Lo que hace: cuando llega el audio de otro jugador
 * ({@link ClientReceiveSoundEvent}, en el cliente de quien escucha), se lo pasa a {@link VozCliente} para que le
 * aplique las voces que ese jugador tenga. El micro propio no se procesa ni se señala en pantalla (uno se oye
 * normal y nada avisa de que su voz suena distinta). En un servidor dedicado no registra nada (los eventos que usa
 * son de cliente) y así no carga clases de cliente.
 */
@ForgeVoicechatPlugin
public class MiscelaneaVozPlugin implements VoicechatPlugin {
    @Override
    public String getPluginId() {
        return "miscelanea";
    }

    @Override
    public void initialize(VoicechatApi api) {
        if (FMLEnvironment.dist.isClient()) activo();
    }

    @Override
    public void registerEvents(EventRegistration r) {
        if (!FMLEnvironment.dist.isClient()) return;
        // el voicechat avisa con la clase exacta de cada sonido (de jugador cercano, de posición o de grupo): registrar
        // solo ClientReceiveSoundEvent no recibía nada, por eso las voces no cambiaban
        r.registerEvent(ClientReceiveSoundEvent.EntitySound.class, e -> recibir(e, e.getId()));
        r.registerEvent(ClientReceiveSoundEvent.LocationalSound.class, e -> recibir(e, e.getId()));
        r.registerEvent(ClientReceiveSoundEvent.StaticSound.class, e -> recibir(e, e.getId()));
    }

    // en métodos aparte para que la clase de cliente solo se cargue si se llega a ejecutar (nunca en el servidor)
    private static void activo() {
        VozCliente.voicechatActivo();
    }

    private static void recibir(ClientReceiveSoundEvent e, UUID hablante) {
        short[] audio = e.getRawAudio();
        if (audio == null || audio.length == 0 || hablante == null) return;
        // el hombre de estática graba (sin procesar) lo que dicen los de alrededor, para imitarlo
        com.miscelanea.client.estatica.ImitarVoz.grabar(hablante, audio);
        // se modifica en el sitio; setRawAudio por si la implementación copia el array
        if (VozCliente.procesar(hablante, audio)) e.setRawAudio(audio);
    }
}

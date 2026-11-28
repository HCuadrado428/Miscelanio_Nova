package com.miscelanea.rave.voz;

import com.miscelanea.rave.AudioDj;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Plugin de Simple Voice Chat (opcional) para la mesa de DJ: un canal de sonido de posición por cada mesa o altavoz que
 * emite el audio del ordenador del DJ. Es la única clase de la rave que toca la API del voicechat; si no está instalado,
 * nunca se carga y la mesa dice que hace falta.
 */
@ForgeVoicechatPlugin
public class RaveVozPlugin implements VoicechatPlugin {
    private static final Map<BlockPos, LocationalAudioChannel> CANALES = new HashMap<>();
    private static final Map<BlockPos, UUID> SALVO = new HashMap<>();
    /** Id de la categoría de volumen del voicechat (como mucho 16 letras minúsculas o guiones bajos). */
    private static final String CATEGORIA = "mesa_dj";

    @Override
    public String getPluginId() {
        return "miscelanea_rave";
    }

    @Override
    public void registerEvents(EventRegistration r) {
        r.registerEvent(VoicechatServerStartedEvent.class, e -> {
            VoicechatServerApi api = e.getVoicechat();
            // categoría de volumen propia: cada jugador la sube o la baja en "Ajustar volúmenes" del voicechat (hasta 400 %)
            boolean categoria = false;
            try {
                api.registerVolumeCategory(api.volumeCategoryBuilder().setId(CATEGORIA).setName("Mesa de DJ")
                        .setDescription("El ordenador del DJ por la mesa y los altavoces").build());
                categoria = true;
            } catch (Throwable t) {
                // voicechat antiguo: suena con el volumen general
            }
            final boolean conCategoria = categoria;
            AudioDj.salida = new AudioDj.Salida() {
                @Override
                public void enviar(ServerLevel l, BlockPos fuente, double distancia, UUID salvo, byte[] opus) {
                    synchronized (CANALES) {
                        LocationalAudioChannel c = CANALES.get(fuente);
                        if (c == null) {
                            UUID id = UUID.nameUUIDFromBytes(("miscelanea_rave:" + fuente.asLong()).getBytes(StandardCharsets.UTF_8));
                            c = api.createLocationalAudioChannel(id, api.fromServerLevel(l),
                                    api.createPosition(fuente.getX() + 0.5, fuente.getY() + 0.8, fuente.getZ() + 0.5));
                            if (c == null) return;
                            if (conCategoria) c.setCategory(CATEGORIA);
                            CANALES.put(fuente.immutable(), c);
                        }
                        // quien emite ya lo oye en su ordenador: a él no se le manda (si no, se oiría dos veces)
                        if (!salvo.equals(SALVO.get(fuente))) {
                            SALVO.put(fuente.immutable(), salvo);
                            c.setFilter(p -> !p.getUuid().equals(salvo));
                        }
                        c.setDistance((float) distancia);
                        c.send(opus);
                    }
                }

                @Override
                public void cerrar(BlockPos fuente) {
                    synchronized (CANALES) {
                        LocationalAudioChannel c = CANALES.remove(fuente);
                        SALVO.remove(fuente);
                        if (c != null) c.flush();
                    }
                }
            };
        });
        r.registerEvent(VoicechatServerStoppedEvent.class, e -> {
            AudioDj.salida = null;
            synchronized (CANALES) {
                CANALES.clear();
                SALVO.clear();
            }
        });
    }
}

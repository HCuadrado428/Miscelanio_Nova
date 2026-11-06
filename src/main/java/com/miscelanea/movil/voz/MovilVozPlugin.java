package com.miscelanea.movil.voz;

import com.miscelanea.movil.MovilVoz;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.LocationalSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.PacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;

import java.util.UUID;

/**
 * Plugin de Simple Voice Chat para las llamadas del móvil (lado servidor; dependencia OPCIONAL: esta clase solo la carga
 * el voicechat si está instalado, y nadie del mod la referencia). Va aparte del plugin de las voces
 * ({@code client/voz/plugin/MiscelaneaVozPlugin}), con su propio id.
 * <ul>
 *   <li>Cada paquete de micro de alguien en llamada se manda también a su pareja como sonido estático (sin posición: se
 *       oye a cualquier distancia o dimensión).</li>
 *   <li>La copia normal por proximidad que le llegaría a la pareja se cancela, para no oírlo doble.</li>
 *   <li>Si los dos están ya en el mismo grupo del voicechat no se hace nada (ya se oyen).</li>
 *   <li>Notas de voz: da el códec Opus del voicechat y, en el cliente, copia el micro del jugador mientras graba.</li>
 * </ul>
 */
@ForgeVoicechatPlugin
public class MovilVozPlugin implements VoicechatPlugin {
    private static volatile VoicechatServerApi api;

    @Override
    public String getPluginId() {
        return "miscelanea_movil";
    }

    /** Da al móvil el códec Opus del voicechat (para las notas de voz). */
    @Override
    public void initialize(VoicechatApi api) {
        MovilVoz.nuevoCodificador = () -> {
            OpusEncoder e = api.createEncoder();
            return new MovilVoz.Codificador() {
                @Override
                public byte[] codificar(short[] pcm) {
                    return e.encode(pcm);
                }

                @Override
                public void cerrar() {
                    e.close();
                }
            };
        };
        MovilVoz.nuevoDecodificador = () -> {
            OpusDecoder d = api.createDecoder();
            return new MovilVoz.Decodificador() {
                @Override
                public short[] decodificar(byte[] opus) {
                    return d.decode(opus);
                }

                @Override
                public void cerrar() {
                    d.close();
                }
            };
        };
    }

    @Override
    public void registerEvents(EventRegistration r) {
        // micro del jugador local (cliente) mientras graba una nota de voz
        r.registerEvent(ClientSoundEvent.class, e -> {
            var c = MovilVoz.microCliente;
            short[] a = e.getRawAudio();
            if (c != null && a != null && a.length > 0) c.accept(a.clone());
        });
        r.registerEvent(VoicechatServerStartedEvent.class, e -> {
            api = e.getVoicechat();
            MovilVoz.activar(MovilVozPlugin::conectado);
        });
        r.registerEvent(VoicechatServerStoppedEvent.class, e -> {
            api = null;
            MovilVoz.desactivar();
        });
        r.registerEvent(MicrophonePacketEvent.class, MovilVozPlugin::micro);
        r.registerEvent(EntitySoundPacketEvent.class, MovilVozPlugin::quitarCopia);
        r.registerEvent(LocationalSoundPacketEvent.class, MovilVozPlugin::quitarCopia);
    }

    private static boolean conectado(UUID u) {
        VoicechatServerApi a = api;
        if (a == null) return false;
        VoicechatConnection c = a.getConnectionOf(u);
        return c != null && c.isInstalled() && !c.isDisabled();
    }

    private static boolean mismoGrupo(VoicechatConnection a, VoicechatConnection b) {
        Group ga = a.getGroup(), gb = b.getGroup();
        return ga != null && gb != null && ga.getId().equals(gb.getId());
    }

    private static void micro(MicrophonePacketEvent e) {
        VoicechatServerApi a = api;
        VoicechatConnection yo = e.getSenderConnection();
        if (a == null || yo == null) return;
        UUID otro = MovilVoz.SOCIOS.get(yo.getPlayer().getUuid());
        if (otro == null) return;
        VoicechatConnection oc = a.getConnectionOf(otro);
        if (oc == null || !oc.isInstalled() || mismoGrupo(yo, oc)) return;
        try {
            a.sendStaticSoundPacketTo(oc, e.getPacket().toStaticSoundPacket());
        } catch (Throwable ignored) {
        }
    }

    private static void quitarCopia(PacketEvent<?> e) {
        VoicechatConnection de = e.getSenderConnection(), para = e.getReceiverConnection();
        if (de == null || para == null) return;
        UUID otro = MovilVoz.SOCIOS.get(de.getPlayer().getUuid());
        if (otro != null && otro.equals(para.getPlayer().getUuid()) && !mismoGrupo(de, para)) e.cancel();
    }
}

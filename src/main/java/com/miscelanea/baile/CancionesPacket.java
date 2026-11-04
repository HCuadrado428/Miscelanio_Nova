package com.miscelanea.baile;

import com.miscelanea.Miscelanea;
import com.miscelanea.network.ModNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Servidor → cliente al entrar: las canciones de baile que tiene el servidor (id, título, BPM y duración). La recreativa Paso Doble
 * las enseña en este orden (el mismo para los dos jugadores de una partida a dos) aunque al cliente le falte alguna:
 * esas salen en gris y no se pueden bailar.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public class CancionesPacket {
    public record Tema(String id, String titulo, String tituloClave, float bpm, float duracion) {}

    /** Cliente: lo último recibido del servidor (null si aún nada). */
    public static volatile List<Tema> servidor;
    private final List<Tema> temas;

    public CancionesPacket(List<Tema> temas) {
        this.temas = temas;
    }

    public CancionesPacket(FriendlyByteBuf b) {
        int n = Math.min(b.readVarInt(), 4096);
        List<Tema> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add(new Tema(b.readUtf(128), b.readUtf(256), b.readUtf(128), b.readFloat(), b.readFloat()));
        temas = l;
    }

    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(temas.size());
        for (Tema t : temas) {
            b.writeUtf(t.id, 128);
            b.writeUtf(t.titulo.length() > 256 ? t.titulo.substring(0, 256) : t.titulo, 256);
            b.writeUtf(t.tituloClave == null ? "" : t.tituloClave, 128);
            b.writeFloat(t.bpm);
            b.writeFloat(t.duracion);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        servidor = Collections.unmodifiableList(temas);
    }

    public static List<Tema> deServidor() {
        List<Tema> l = new ArrayList<>();
        for (CancionesBaile.Cancion c : CancionesBaile.lista())
            if (c.id.length() <= 128) l.add(new Tema(c.id, c.titulo, c.tituloClave == null ? "" : c.tituloClave, c.bpm, c.duracion));
        return l;
    }

    @SubscribeEvent
    public static void entrar(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p)
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new CancionesPacket(deServidor()));
    }
}

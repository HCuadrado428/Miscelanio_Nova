package com.miscelanea.voz;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Servidor → cliente: las voces activas de un jugador (lista vacía = voz normal). Se manda a quien lo ve (y a él) cada
 * vez que cambia, al empezar a verlo y al entrar.
 */
public class VozEstadoPacket {
    public final UUID jugador;
    public final List<VozEntrada> voces;

    public VozEstadoPacket(UUID jugador, List<VozEntrada> voces) {
        this.jugador = jugador;
        this.voces = voces;
    }

    public VozEstadoPacket(FriendlyByteBuf b) {
        jugador = b.readUUID();
        int n = b.readVarInt();
        voces = new ArrayList<>(n);
        for (int i = 0; i < n; i++) voces.add(new VozEntrada(b.readUtf(64), b.readByte()));
    }

    public void encode(FriendlyByteBuf b) {
        b.writeUUID(jugador);
        b.writeVarInt(voces.size());
        for (VozEntrada v : voces) {
            b.writeUtf(v.id(), 64);
            b.writeByte(v.amp());
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.voz.VozCliente.recibir(this));
    }
}

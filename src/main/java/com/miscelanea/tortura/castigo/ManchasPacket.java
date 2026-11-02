package com.miscelanea.tortura.castigo;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Servidor → cliente: las manchas de tomate / huevo de un jugador (lista vacía = limpio). */
public class ManchasPacket {
    public final int entidad;
    public final List<Mancha> lista;

    public ManchasPacket(int entidad, List<Mancha> lista) {
        this.entidad = entidad;
        this.lista = lista;
    }

    public ManchasPacket(FriendlyByteBuf b) {
        this.entidad = b.readVarInt();
        int n = Math.min(Manchas.MAX, b.readVarInt());
        this.lista = new ArrayList<>(n);
        for (int i = 0; i < n; i++) lista.add(Mancha.leer(b));
    }

    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(entidad);
        int n = Math.min(Manchas.MAX, lista.size());
        b.writeVarInt(n);
        for (int i = 0; i < n; i++) lista.get(i).escribir(b);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.tortura.castigo.ManchasCliente.recibir(this));
    }
}

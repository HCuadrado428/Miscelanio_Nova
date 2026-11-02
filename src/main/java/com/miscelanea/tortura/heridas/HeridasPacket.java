package com.miscelanea.tortura.heridas;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → clientes: la lista completa de heridas (y vendas) de un jugador. */
public class HeridasPacket {
    private final int jugador;
    private final HeridasDatos datos;

    public HeridasPacket(int jugador, HeridasDatos datos) {
        this.jugador = jugador;
        this.datos = datos;
    }

    public HeridasPacket(FriendlyByteBuf b) {
        this.jugador = b.readInt();
        this.datos = HeridasDatos.leer(b);
    }

    public void encode(FriendlyByteBuf b) {
        b.writeInt(jugador);
        datos.escribir(b);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.tortura.heridas.HeridasCliente.recibir(jugador, datos));
    }
}

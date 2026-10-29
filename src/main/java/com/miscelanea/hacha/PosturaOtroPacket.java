package com.miscelanea.hacha;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → clientes: la postura de otro jugador con el hacha, o una acción suya (para ver sus animaciones). */
public class PosturaOtroPacket {
    private final int jugador;
    private final byte tipo, valor;   // tipo 0 = postura, 1 = acción

    public PosturaOtroPacket(int jugador, byte tipo, byte valor) {
        this.jugador = jugador;
        this.tipo = tipo;
        this.valor = valor;
    }

    public PosturaOtroPacket(FriendlyByteBuf b) {
        this.jugador = b.readInt();
        this.tipo = b.readByte();
        this.valor = b.readByte();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeInt(jugador);
        b.writeByte(tipo);
        b.writeByte(valor);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.combate.HachaCliente.deServidor(jugador, tipo, valor));
    }
}

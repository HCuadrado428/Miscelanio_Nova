package com.miscelanea.llave;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → clientes cercanos: otro jugador ha hecho una acción (para ver su animación). */
public class AnimPacket {
    private final int jugador, accion;

    public AnimPacket(int jugador, int accion) {
        this.jugador = jugador;
        this.accion = accion;
    }

    public AnimPacket(FriendlyByteBuf b) {
        this.jugador = b.readInt();
        this.accion = b.readByte();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeInt(jugador);
        b.writeByte(accion);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.combate.LlaveCliente.animDeOtro(jugador, accion));
    }
}

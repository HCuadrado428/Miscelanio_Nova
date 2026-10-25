package com.miscelanea.programa;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → clientes: el jugador 'id' hace la acción 'accion' durante 'ticks' (0 = parar). */
public class AccionPacket {
    public final int id, accion, ticks;

    public AccionPacket(int id, int accion, int ticks) {
        this.id = id;
        this.accion = accion;
        this.ticks = ticks;
    }

    public AccionPacket(FriendlyByteBuf b) {
        this(b.readVarInt(), b.readVarInt(), b.readVarInt());
    }

    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(id);
        b.writeVarInt(accion);
        b.writeVarInt(ticks);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.programa.ClientAcciones.recibir(this));
    }
}

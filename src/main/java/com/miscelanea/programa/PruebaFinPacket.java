package com.miscelanea.programa;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → cliente: pantalla final (completada o fallida, nota, tiempo, muertes). */
public class PruebaFinPacket {
    public final boolean exito;
    public final String nota;
    public final int segundos, muertes;

    public PruebaFinPacket(boolean exito, String nota, int segundos, int muertes) {
        this.exito = exito;
        this.nota = nota;
        this.segundos = segundos;
        this.muertes = muertes;
    }

    public PruebaFinPacket(FriendlyByteBuf buf) {
        this(buf.readBoolean(), buf.readUtf(8), buf.readVarInt(), buf.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(exito);
        buf.writeUtf(nota, 8);
        buf.writeVarInt(segundos);
        buf.writeVarInt(muertes);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.programa.ProgramaHud.fin(this));
    }
}

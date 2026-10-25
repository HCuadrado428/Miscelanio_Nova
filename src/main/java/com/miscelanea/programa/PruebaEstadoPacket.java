package com.miscelanea.programa;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → cliente: estado de la prueba para la interfaz (etapa, subpasos, inicio...). */
public class PruebaEstadoPacket {
    public final boolean activo, intro;
    public final int etapa, a, b, manivela, muertes;
    public final long inicio;

    public PruebaEstadoPacket(boolean activo, boolean intro, int etapa, int a, int b, int manivela, long inicio, int muertes) {
        this.activo = activo;
        this.intro = intro;
        this.etapa = etapa;
        this.a = a;
        this.b = b;
        this.manivela = manivela;
        this.inicio = inicio;
        this.muertes = muertes;
    }

    public PruebaEstadoPacket(FriendlyByteBuf buf) {
        this(buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readLong(), buf.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(activo);
        buf.writeBoolean(intro);
        buf.writeVarInt(etapa);
        buf.writeVarInt(a);
        buf.writeVarInt(b);
        buf.writeVarInt(manivela);
        buf.writeLong(inicio);
        buf.writeVarInt(muertes);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.programa.ProgramaHud.estado(this));
    }
}

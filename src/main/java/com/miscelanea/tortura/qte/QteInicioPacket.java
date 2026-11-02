package com.miscelanea.tortura.qte;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/** Servidor → cliente: empieza este minijuego. */
public class QteInicioPacket {
    public final int id, rondas;
    public final QteTipo tipo;
    public final float dificultad;
    public final long semilla;
    @Nullable public final Component titulo;

    public QteInicioPacket(int id, QteTipo tipo, float dificultad, int rondas, long semilla, @Nullable Component titulo) {
        this.id = id;
        this.tipo = tipo;
        this.dificultad = dificultad;
        this.rondas = rondas;
        this.semilla = semilla;
        this.titulo = titulo;
    }

    public QteInicioPacket(FriendlyByteBuf b) {
        this.id = b.readVarInt();
        this.tipo = QteTipo.de(b.readByte());
        this.dificultad = b.readFloat();
        this.rondas = b.readByte();
        this.semilla = b.readLong();
        this.titulo = b.readBoolean() ? b.readComponent() : null;
    }

    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(id);
        b.writeByte(tipo.ordinal());
        b.writeFloat(dificultad);
        b.writeByte(rondas);
        b.writeLong(semilla);
        b.writeBoolean(titulo != null);
        if (titulo != null) b.writeComponent(titulo);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.tortura.qte.QteCliente.iniciar(this));
    }
}

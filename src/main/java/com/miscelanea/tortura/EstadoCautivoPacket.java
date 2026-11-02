package com.miscelanea.tortura;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → cliente: trampa (ticks que quedan), grilletes y quién tira de la cadena (id de entidad o -1). */
public class EstadoCautivoPacket {
    public final int entidad, trampa, cadena;
    public final boolean sinCajas, grilletes;

    public EstadoCautivoPacket(int entidad, int trampa, boolean sinCajas, boolean grilletes, int cadena) {
        this.entidad = entidad;
        this.trampa = trampa;
        this.sinCajas = sinCajas;
        this.grilletes = grilletes;
        this.cadena = cadena;
    }

    public EstadoCautivoPacket(FriendlyByteBuf b) {
        this.entidad = b.readVarInt();
        this.trampa = b.readVarInt();
        this.sinCajas = b.readBoolean();
        this.grilletes = b.readBoolean();
        this.cadena = b.readVarInt();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(entidad);
        b.writeVarInt(Math.max(0, trampa));
        b.writeBoolean(sinCajas);
        b.writeBoolean(grilletes);
        b.writeVarInt(cadena);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.tortura.TorturaCliente.recibir(this));
    }
}

package com.miscelanea.tortura;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Servidor → cliente: trampa (ticks que quedan), grilletes y quién tira de la cadena (id de entidad o -1); y el lazo:
 * estado (0 nada, 1 enlazado, 2 derribado, 3 maniatado, ver {@link Lazo}), quién lo lleva (id o -1) y los ticks que
 * quedan del estado (derribado: hasta levantarse; maniatado: hasta soltarse solo).
 */
public class EstadoCautivoPacket {
    public final int entidad, trampa, cadena;
    public final boolean sinCajas, grilletes;
    public final int lazo, lazoCaptor, lazoTicks;

    public EstadoCautivoPacket(int entidad, int trampa, boolean sinCajas, boolean grilletes, int cadena, int lazo, int lazoCaptor, int lazoTicks) {
        this.entidad = entidad;
        this.trampa = trampa;
        this.sinCajas = sinCajas;
        this.grilletes = grilletes;
        this.cadena = cadena;
        this.lazo = lazo;
        this.lazoCaptor = lazoCaptor;
        this.lazoTicks = lazoTicks;
    }

    public EstadoCautivoPacket(FriendlyByteBuf b) {
        this.entidad = b.readVarInt();
        this.trampa = b.readVarInt();
        this.sinCajas = b.readBoolean();
        this.grilletes = b.readBoolean();
        this.cadena = b.readVarInt();
        this.lazo = b.readByte();
        this.lazoCaptor = b.readVarInt();
        this.lazoTicks = b.readVarInt();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(entidad);
        b.writeVarInt(Math.max(0, trampa));
        b.writeBoolean(sinCajas);
        b.writeBoolean(grilletes);
        b.writeVarInt(cadena);
        b.writeByte(lazo);
        b.writeVarInt(lazoCaptor);
        b.writeVarInt(Math.max(0, lazoTicks));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.tortura.TorturaCliente.recibir(this));
    }
}

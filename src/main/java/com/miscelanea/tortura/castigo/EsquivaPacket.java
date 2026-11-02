package com.miscelanea.tortura.castigo;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → cliente: esa entidad (en el cepo) aparta la cabeza hacia un lado (+1 su derecha, -1 su izquierda). */
public class EsquivaPacket {
    public final int entidad, lado;

    public EsquivaPacket(int entidad, int lado) {
        this.entidad = entidad;
        this.lado = lado;
    }

    public EsquivaPacket(FriendlyByteBuf b) {
        this.entidad = b.readVarInt();
        this.lado = b.readByte();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(entidad);
        b.writeByte(lado);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.tortura.castigo.CastigoCliente.esquiva(entidad, lado));
    }
}

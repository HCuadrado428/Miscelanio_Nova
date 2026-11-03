package com.miscelanea.tortura.efectos;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Servidor → cliente: una entidad empieza o deja de ser fantasma. Hace falta porque los efectos de poción solo se
 * sincronizan con el cliente del propio afectado; los que lo ven necesitan saberlo para esconderlo.
 */
public class FantasmaPacket {
    public final int entidad;
    public final boolean activo;

    public FantasmaPacket(int entidad, boolean activo) {
        this.entidad = entidad;
        this.activo = activo;
    }

    public FantasmaPacket(FriendlyByteBuf b) {
        this(b.readVarInt(), b.readBoolean());
    }

    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(entidad);
        b.writeBoolean(activo);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.tortura.efectos.FantasmaCliente.recibir(this));
    }
}

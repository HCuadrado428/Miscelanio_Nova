package com.miscelanea.tortura;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Servidor → cliente: empieza (o se anula) la ejecución de la trampa de oso de una entidad. Se manda a la víctima y a
 * todos los que la ven. Lleva los ticks que ya han pasado (no la hora absoluta), porque el reloj del cliente puede ir
 * un poco desfasado y porque a quien empieza a verla a mitad de secuencia le llega ya empezada.
 */
public class EjecucionPacket {
    public final int entidad;
    public final boolean activa;
    public final int transcurrido;

    public EjecucionPacket(int entidad, boolean activa, int transcurrido) {
        this.entidad = entidad;
        this.activa = activa;
        this.transcurrido = transcurrido;
    }

    public EjecucionPacket(FriendlyByteBuf b) {
        this.entidad = b.readVarInt();
        this.activa = b.readBoolean();
        this.transcurrido = b.readVarInt();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(entidad);
        b.writeBoolean(activa);
        b.writeVarInt(Math.max(0, transcurrido));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.tortura.EjecucionCliente.recibir(this));
    }
}

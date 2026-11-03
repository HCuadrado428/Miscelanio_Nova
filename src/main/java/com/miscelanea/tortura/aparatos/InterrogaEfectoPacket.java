package com.miscelanea.tortura.aparatos;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Servidor → víctima: efectos de pantalla. DESPERTAR (la adrenalina la reanima: destello blanco y sacudida; valor =
 * ticks), GOLPE (le acaban de usar una herramienta: valor = ordinal de {@link Herramienta}), SUELTA (se ha soltado del
 * gancho y cae).
 */
public class InterrogaEfectoPacket {
    public static final int DESPERTAR = 1, GOLPE = 2, SUELTA = 3;
    public final int entidad, efecto, valor;

    public InterrogaEfectoPacket(int entidad, int efecto, int valor) {
        this.entidad = entidad;
        this.efecto = efecto;
        this.valor = valor;
    }

    public InterrogaEfectoPacket(FriendlyByteBuf b) {
        this.entidad = b.readVarInt();
        this.efecto = b.readByte();
        this.valor = b.readVarInt();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(entidad);
        b.writeByte(efecto);
        b.writeVarInt(Math.max(0, valor));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.tortura.aparatos.InterrogaCliente.efecto(this));
    }
}

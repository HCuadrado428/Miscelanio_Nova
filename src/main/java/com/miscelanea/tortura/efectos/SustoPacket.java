package com.miscelanea.tortura.efectos;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Servidor → cliente: un golpe de efecto en un punto. {@link #ESTALLIDO}: el collar ha reventado (fogonazo, metralla
 * y sangre; a quien está cerca le pitan los oídos). {@link #GAS}: estalla una granada de miedo (nube espesa de gas verde).
 */
public class SustoPacket {
    public static final int ESTALLIDO = 0, GAS = 1;
    public final int tipo;
    public final double x, y, z;

    public SustoPacket(int tipo, double x, double y, double z) {
        this.tipo = tipo;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public SustoPacket(FriendlyByteBuf b) {
        this(b.readByte(), b.readDouble(), b.readDouble(), b.readDouble());
    }

    public void encode(FriendlyByteBuf b) {
        b.writeByte(tipo);
        b.writeDouble(x);
        b.writeDouble(y);
        b.writeDouble(z);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.tortura.efectos.EfectosCliente.susto(this));
    }
}

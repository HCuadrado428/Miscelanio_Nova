package com.miscelanea.tortura.aparatos;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Servidor → clientes: efectos visuales sobre una entidad. DESMAYO (solo a la víctima: pantalla a negro 'valor' ticks),
 * DESCARGA (a todos los que la ven: tiembla y echa chispas; valor = ticks | voltaje &lt;&lt; 16), CORTE (solo a la
 * víctima del péndulo: destello rojo; valor = fuerza 1..3).
 */
public class AparatoEfectoPacket {
    public static final int DESMAYO = 1, DESCARGA = 2, CORTE = 3;
    public final int entidad, efecto, valor;

    public AparatoEfectoPacket(int entidad, int efecto, int valor) {
        this.entidad = entidad;
        this.efecto = efecto;
        this.valor = valor;
    }

    public AparatoEfectoPacket(FriendlyByteBuf b) {
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
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.tortura.aparatos.AparatosCliente.efecto(this));
    }
}

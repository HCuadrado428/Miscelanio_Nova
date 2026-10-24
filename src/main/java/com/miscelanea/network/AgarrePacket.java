package com.miscelanea.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → clientes: estado de un agarre con la porra (para animaciones y cámara). */
public class AgarrePacket {
    public enum Tipo { INICIO, FIN, DESCARGA, FORCEJEO, EJECUCION }

    public final Tipo tipo;
    public final int agresor;
    public final int victima;
    /** Resistencia de la víctima (0–100) en DESCARGA y FORCEJEO. */
    public final float valor;

    public AgarrePacket(Tipo tipo, int agresor, int victima, float valor) {
        this.tipo = tipo;
        this.agresor = agresor;
        this.victima = victima;
        this.valor = valor;
    }

    public AgarrePacket(FriendlyByteBuf buf) {
        this.tipo = buf.readEnum(Tipo.class);
        this.agresor = buf.readVarInt();
        this.victima = buf.readVarInt();
        this.valor = buf.readFloat();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(tipo);
        buf.writeVarInt(agresor);
        buf.writeVarInt(victima);
        buf.writeFloat(valor);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.miscelanea.client.porra.ClientAgarres.recibir(this));
    }
}

package com.miscelanea.dream;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → clientes: estado de un mordisco (para las poses, la cámara, la viñeta y los temblores). */
public class MordiscoPacket {
    public enum Tipo { INICIO, FIN, MORDISCO }

    public final Tipo tipo;
    public final int mordedor, victima;

    public MordiscoPacket(Tipo tipo, int mordedor, int victima) {
        this.tipo = tipo;
        this.mordedor = mordedor;
        this.victima = victima;
    }

    public MordiscoPacket(FriendlyByteBuf b) {
        this.tipo = b.readEnum(Tipo.class);
        this.mordedor = b.readVarInt();
        this.victima = b.readVarInt();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeEnum(tipo);
        b.writeVarInt(mordedor);
        b.writeVarInt(victima);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.dream.ClientMordiscos.recibir(this));
    }
}

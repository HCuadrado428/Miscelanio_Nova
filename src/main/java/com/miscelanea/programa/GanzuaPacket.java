package com.miscelanea.programa;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: resultado del minijuego de la ganzúa sobre una caja cerrada (o que se ha empezado). */
public class GanzuaPacket {
    public static final int EMPEZAR = 0, EXITO = 1, FALLO = 2;
    private final BlockPos pos;
    private final int resultado;

    public GanzuaPacket(BlockPos pos, int resultado) {
        this.pos = pos;
        this.resultado = resultado;
    }

    public GanzuaPacket(FriendlyByteBuf b) {
        this(b.readBlockPos(), b.readVarInt());
    }

    public void encode(FriendlyByteBuf b) {
        b.writeBlockPos(pos);
        b.writeVarInt(resultado);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p != null) CajaCerradaBlock.resultadoGanzua(p, pos, resultado);
    }
}

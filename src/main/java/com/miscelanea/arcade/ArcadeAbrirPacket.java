package com.miscelanea.arcade;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → cliente: abre la pantalla de la recreativa (también actualiza los créditos con empezar=true/false). */
public class ArcadeAbrirPacket {
    public final BlockPos pos;
    public final int creditos;
    public final boolean libre;
    public final int modo;   // 0 abrir, 1 créditos (respuesta a ficha), 2 empezar partida, 3 no hay créditos

    public ArcadeAbrirPacket(BlockPos pos, int creditos, boolean libre) {
        this(pos, creditos, libre, 0);
    }

    public ArcadeAbrirPacket(BlockPos pos, int creditos, boolean libre, int modo) {
        this.pos = pos;
        this.creditos = creditos;
        this.libre = libre;
        this.modo = modo;
    }

    public ArcadeAbrirPacket(FriendlyByteBuf b) {
        this(b.readBlockPos(), b.readVarInt(), b.readBoolean(), b.readByte());
    }

    public void encode(FriendlyByteBuf b) {
        b.writeBlockPos(pos);
        b.writeVarInt(creditos);
        b.writeBoolean(libre);
        b.writeByte(modo);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.arcade.ArcadeCliente.abrir(this));
    }
}

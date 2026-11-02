package com.miscelanea.mesas;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: mandos del air hockey (bits: 1 adelante, 2 atrás, 4 izquierda, 8 derecha, 16 soltar la mesa). */
public class AirHockeyMandoPacket {
    final BlockPos pos;
    final byte mando;

    public AirHockeyMandoPacket(BlockPos pos, int mando) {
        this.pos = pos;
        this.mando = (byte) mando;
    }

    public AirHockeyMandoPacket(FriendlyByteBuf b) {
        this(b.readBlockPos(), b.readByte());
    }

    public void encode(FriendlyByteBuf b) {
        b.writeBlockPos(pos);
        b.writeByte(mando);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p == null || p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 100) return;
        if (p.level().getBlockEntity(pos) instanceof AirHockeyBlockEntity be) be.mando(p, mando);
    }
}

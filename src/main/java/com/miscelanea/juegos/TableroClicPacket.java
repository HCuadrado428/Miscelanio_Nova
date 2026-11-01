package com.miscelanea.juegos;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: he tocado esta casilla del tablero. */
public class TableroClicPacket {
    private final BlockPos pos;
    private final int sq;

    public TableroClicPacket(BlockPos pos, int sq) {
        this.pos = pos;
        this.sq = sq;
    }

    public TableroClicPacket(FriendlyByteBuf b) {
        this(b.readBlockPos(), b.readByte());
    }

    public void encode(FriendlyByteBuf b) {
        b.writeBlockPos(pos);
        b.writeByte(sq);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p == null || sq < 0 || sq > 63 || p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 49) return;
        if (p.level().getBlockEntity(pos) instanceof TableroBlockEntity be) be.clic(p, sq);
    }
}

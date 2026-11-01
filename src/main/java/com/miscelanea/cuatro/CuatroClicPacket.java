package com.miscelanea.cuatro;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: soltar una ficha en una columna del cuatro en raya. */
public class CuatroClicPacket {
    private final BlockPos pos;
    private final int col;

    public CuatroClicPacket(BlockPos pos, int col) {
        this.pos = pos;
        this.col = col;
    }

    public CuatroClicPacket(FriendlyByteBuf b) {
        this.pos = b.readBlockPos();
        this.col = b.readByte();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeBlockPos(pos);
        b.writeByte(col);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p == null || p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64) return;
        if (p.level().getBlockEntity(pos) instanceof CuatroBlockEntity be) be.clic(p, col);
    }
}

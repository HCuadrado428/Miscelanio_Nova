package com.miscelanea.domino;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: clic en el tablero del dominó (coordenadas de textura). */
public class DominoClicPacket {
    private final BlockPos pos;
    private final short tx, ty;

    public DominoClicPacket(BlockPos pos, double tx, double ty) {
        this.pos = pos;
        this.tx = (short) tx;
        this.ty = (short) ty;
    }

    public DominoClicPacket(FriendlyByteBuf b) {
        this.pos = b.readBlockPos();
        this.tx = b.readShort();
        this.ty = b.readShort();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeBlockPos(pos);
        b.writeShort(tx);
        b.writeShort(ty);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p == null || p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64) return;
        if (p.level().getBlockEntity(pos) instanceof DominoBlockEntity be) be.clic(p, tx, ty);
    }
}

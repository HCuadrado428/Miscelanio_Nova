package com.miscelanea.mesas;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: tiro de billar (ángulo en la mesa y fuerza 0..1). */
public class BillarTiroPacket {
    final BlockPos pos;
    final float angulo, fuerza;

    public BillarTiroPacket(BlockPos pos, float angulo, float fuerza) {
        this.pos = pos;
        this.angulo = angulo;
        this.fuerza = fuerza;
    }

    public BillarTiroPacket(FriendlyByteBuf b) {
        this(b.readBlockPos(), b.readFloat(), b.readFloat());
    }

    public void encode(FriendlyByteBuf b) {
        b.writeBlockPos(pos);
        b.writeFloat(angulo);
        b.writeFloat(fuerza);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p == null || p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 100) return;
        if (p.level().getBlockEntity(pos) instanceof BillarBlockEntity be) be.tiro(p, angulo, fuerza);
    }
}

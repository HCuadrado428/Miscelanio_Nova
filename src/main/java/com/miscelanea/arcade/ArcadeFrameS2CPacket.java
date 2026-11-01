package com.miscelanea.arcade;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → clientes cercanos: lo que se ve en la pantalla de una recreativa. */
public class ArcadeFrameS2CPacket {
    final BlockPos pos;
    final byte[] datos;

    public ArcadeFrameS2CPacket(BlockPos pos, byte[] datos) {
        this.pos = pos;
        this.datos = datos;
    }

    public ArcadeFrameS2CPacket(FriendlyByteBuf b) {
        this(b.readBlockPos(), b.readByteArray(200000));
    }

    public void encode(FriendlyByteBuf b) {
        b.writeBlockPos(pos);
        b.writeByteArray(datos);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.arcade.ArcadeCliente.frame(pos, datos));
    }
}

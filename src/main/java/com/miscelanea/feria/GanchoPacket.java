package com.miscelanea.feria;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Mandos de las máquinas altas. Servidor → cliente: ABRIR (toma los mandos del gancho). Cliente → servidor: MOVER,
 * SOLTAR (gancho) y GOLPE (boxeo y martillo: x = tic del juego en que el cliente golpeó).
 */
public class GanchoPacket {
    public static final int ABRIR = 0, MOVER = 1, SOLTAR = 2, GOLPE = 3;
    public final int tipo, x, z;
    public final BlockPos pos;

    public GanchoPacket(int tipo, BlockPos pos, int x, int z) {
        this.tipo = tipo;
        this.pos = pos;
        this.x = x;
        this.z = z;
    }

    public GanchoPacket(FriendlyByteBuf b) {
        this(b.readByte(), b.readBlockPos(), b.readShort(), b.readShort());
    }

    public void encode(FriendlyByteBuf b) {
        b.writeByte(tipo);
        b.writeBlockPos(pos);
        b.writeShort(x);
        b.writeShort(z);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p == null) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.feria.GanchoCliente.abrir(pos));
            return;
        }
        if (p.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) > 25) return;
        if (!(p.level().getBlockEntity(pos) instanceof MaquinaAltaBlockEntity be)) return;
        if (tipo == MOVER) be.moverGarra(p, x / 1000F, z / 1000F);
        else if (tipo == SOLTAR) be.soltarGarra(p);
        else if (tipo == GOLPE) be.golpeCliente(p, x);
    }
}

package com.miscelanea.mesas;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → clientes cercanos: posiciones de la física de una mesa (bolas, barras) en este tic. */
public class MesaFisicaPacket {
    final BlockPos pos;
    final float[] datos;

    public MesaFisicaPacket(BlockPos pos, float[] datos) {
        this.pos = pos;
        this.datos = datos;
    }

    public MesaFisicaPacket(FriendlyByteBuf b) {
        this.pos = b.readBlockPos();
        int n = Math.min(256, b.readVarInt());
        this.datos = new float[n];
        for (int i = 0; i < n; i++) datos[i] = b.readFloat();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeBlockPos(pos);
        b.writeVarInt(datos.length);
        for (float f : datos) b.writeFloat(f);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level != null && mc.level.getBlockEntity(pos) instanceof MesaGrandeBlock.MesaEntidad m) m.fisica(datos);
        });
    }
}

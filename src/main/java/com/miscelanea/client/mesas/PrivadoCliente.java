package com.miscelanea.client.mesas;

import com.miscelanea.mesas.PrivadoPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/** Entrega al tablero del cliente los datos que solo puede ver este jugador. */
public final class PrivadoCliente {
    private PrivadoCliente() {}

    public static void recibir(BlockPos pos, CompoundTag datos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (mc.level.getBlockEntity(pos) instanceof PrivadoPacket.Receptor r) r.recibirPrivado(datos);
    }
}

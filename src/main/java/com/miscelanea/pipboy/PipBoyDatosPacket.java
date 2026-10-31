package com.miscelanea.pipboy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → cliente: tus notas y marcadores del Pip-Boy. */
public class PipBoyDatosPacket {
    private final CompoundTag datos;

    public PipBoyDatosPacket(CompoundTag datos) {
        this.datos = datos;
    }

    public PipBoyDatosPacket(FriendlyByteBuf b) {
        this.datos = b.readNbt();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeNbt(datos);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.pipboy.PipBoyCliente.datos(datos));
    }
}

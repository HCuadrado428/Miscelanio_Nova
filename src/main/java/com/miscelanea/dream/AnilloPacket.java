package com.miscelanea.dream;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → cliente: el anillo de medianoche acaba de llevarte (la pantalla vuelve desde negro). */
public class AnilloPacket {
    /** true = has llegado a la Medianoche; false = has vuelto. */
    public final boolean ida;

    public AnilloPacket(boolean ida) {
        this.ida = ida;
    }

    public AnilloPacket(FriendlyByteBuf b) {
        this.ida = b.readBoolean();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeBoolean(ida);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.dream.AnilloCliente.llegada(ida));
    }
}

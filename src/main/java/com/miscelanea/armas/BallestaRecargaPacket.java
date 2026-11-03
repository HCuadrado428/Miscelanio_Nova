package com.miscelanea.armas;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: tecla de recargar (R) con la ballesta de repetición en la mano. */
public class BallestaRecargaPacket {
    public BallestaRecargaPacket() {}

    public BallestaRecargaPacket(FriendlyByteBuf b) {}

    public void encode(FriendlyByteBuf b) {}

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p == null) return;
        var s = p.getMainHandItem();
        if (s.getItem() instanceof BallestaItem) Ballesta.iniciarRecarga(p, s);
    }
}

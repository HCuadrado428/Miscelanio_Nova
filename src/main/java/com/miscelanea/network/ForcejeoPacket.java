package com.miscelanea.network;

import com.miscelanea.porra.Agarres;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: la víctima de un agarre ha pulsado una tecla para forcejear. */
public class ForcejeoPacket {
    public ForcejeoPacket() {}

    public ForcejeoPacket(FriendlyByteBuf buf) {}

    public void encode(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p != null) Agarres.forcejear(p);
    }
}

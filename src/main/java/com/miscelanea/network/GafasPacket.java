package com.miscelanea.network;

import com.miscelanea.carcel.GafasItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: encender/apagar las gafas de visión nocturna puestas. */
public class GafasPacket {
    public GafasPacket() {}

    public GafasPacket(FriendlyByteBuf buf) {}

    public void encode(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p == null) return;
        var casco = p.getItemBySlot(EquipmentSlot.HEAD);
        if (casco.getItem() instanceof GafasItem) GafasItem.alternar(p, casco);
    }
}

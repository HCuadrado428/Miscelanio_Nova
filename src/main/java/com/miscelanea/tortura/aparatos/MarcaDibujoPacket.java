package com.miscelanea.tortura.aparatos;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: el símbolo 8x8 dibujado en la pantalla del hierro de marcar (bit y*8+x = píxel). */
public class MarcaDibujoPacket {
    public final boolean principal;
    public final long bits;

    public MarcaDibujoPacket(boolean principal, long bits) {
        this.principal = principal;
        this.bits = bits;
    }

    public MarcaDibujoPacket(FriendlyByteBuf b) {
        this.principal = b.readBoolean();
        this.bits = b.readLong();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeBoolean(principal);
        b.writeLong(bits);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p == null) return;
        ItemStack st = p.getItemInHand(principal ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
        if (st.getItem() instanceof HierroMarcarItem) HierroMarcarItem.ponerSimbolo(st, bits);
    }
}

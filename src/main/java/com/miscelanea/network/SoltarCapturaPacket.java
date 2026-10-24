package com.miscelanea.network;

import com.miscelanea.Miscelanea;
import com.miscelanea.aspira.AspiraUtil;
import com.miscelanea.aspira.Deposito;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: el jugador ha pulsado la tecla de soltar captura. */
public class SoltarCapturaPacket {
    public SoltarCapturaPacket() {}

    public SoltarCapturaPacket(FriendlyByteBuf buf) {}

    public void encode(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null || !Deposito.llevaMochila(player)) return;

        Vec3 look = player.getViewVector(1.0F);
        Vec3 pos = AspiraUtil.boca(player).add(look.scale(1.5));
        // Que no aparezca dentro de un bloque: si no hay sitio, en los pies del jugador
        if (!AspiraUtil.lineaDeVision(player, pos)) pos = player.position();

        Deposito.soltar(player, pos, look.scale(0.4).add(0, 0.2, 0));
    }
}

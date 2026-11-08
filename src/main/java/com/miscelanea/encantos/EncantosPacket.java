package com.miscelanea.encantos;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: elegir en la mesa (tipo 0) o la Ráfaga en el aire (tipo 1: el servidor perdona la caída). */
public class EncantosPacket {
    public final int tipo;
    public final BlockPos mesa;
    public final String id;

    public EncantosPacket(int tipo, BlockPos mesa, String id) {
        this.tipo = tipo;
        this.mesa = mesa;
        this.id = id;
    }

    public EncantosPacket(FriendlyByteBuf b) {
        tipo = b.readVarInt();
        mesa = b.readBlockPos();
        id = b.readUtf();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(tipo);
        b.writeBlockPos(mesa);
        b.writeUtf(id);
    }

    public void handle(Supplier<NetworkEvent.Context> c) {
        ServerPlayer p = c.get().getSender();
        c.get().setPacketHandled(true);
        if (p == null) return;
        if (tipo == 0) MesaEncantar.elegir(p, mesa, id);
        else if (tipo == 1 && Encantos.tiene(p, EquipmentSlot.LEGS, Encantos.RAFAGA)) p.fallDistance = 0;
    }
}

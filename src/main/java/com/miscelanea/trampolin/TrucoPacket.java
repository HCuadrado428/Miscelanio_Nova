package com.miscelanea.trampolin;

import com.miscelanea.network.ModNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** Un jugador hace un giro en el trampolín (cliente → servidor → los demás lo ven). */
public class TrucoPacket {
    final int entidad, giros, duracion;

    public TrucoPacket(int entidad, int giros, int duracion) {
        this.entidad = entidad;
        this.giros = giros;
        this.duracion = duracion;
    }

    public TrucoPacket(FriendlyByteBuf b) {
        this(b.readVarInt(), b.readByte(), b.readVarInt());
    }

    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(entidad);
        b.writeByte(giros);
        b.writeVarInt(duracion);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p != null) {
            TrucoPacket m = new TrucoPacket(p.getId(), Math.min(3, giros), Math.min(100, duracion));
            ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> p), m);
        } else {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.trampolin.TrampolinCliente.truco(entidad, giros, duracion));
        }
    }
}

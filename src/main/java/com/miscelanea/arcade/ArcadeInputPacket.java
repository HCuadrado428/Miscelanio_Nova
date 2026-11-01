package com.miscelanea.arcade;

import com.miscelanea.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;

/**
 * Mandos de la partida a dos: cada cliente envía los botones que tendrá pulsados en un paso futuro de la simulación;
 * el servidor los reenvía al otro jugador de la misma recreativa. Los dos simulan el juego a la vez, paso a paso.
 */
public class ArcadeInputPacket {
    final BlockPos pos;
    final int paso, mascara;

    public ArcadeInputPacket(BlockPos pos, int paso, int mascara) {
        this.pos = pos;
        this.paso = paso;
        this.mascara = mascara;
    }

    public ArcadeInputPacket(FriendlyByteBuf b) {
        this(b.readBlockPos(), b.readVarInt(), b.readVarInt());
    }

    public void encode(FriendlyByteBuf b) {
        b.writeBlockPos(pos);
        b.writeVarInt(paso);
        b.writeVarInt(mascara);
    }

    public void handle(java.util.function.Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p != null) {
            if (!(p.level().getBlockEntity(pos) instanceof RecreativaBlockEntity be)) return;
            UUID otro = p.getUUID().equals(be.jugador) ? be.jugador2 : p.getUUID().equals(be.jugador2) ? be.jugador : null;
            if (otro == null) return;
            ServerPlayer o = p.server.getPlayerList().getPlayer(otro);
            if (o != null) ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> o), new ArcadeInputPacket(pos, paso, mascara));
        } else DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.arcade.ArcadeCliente.entrada(pos, paso, mascara));
    }
}

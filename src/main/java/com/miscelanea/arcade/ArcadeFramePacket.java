package com.miscelanea.arcade;

import com.miscelanea.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** Cliente → servidor: imagen de la pantalla de la recreativa (comprimida). El servidor la reenvía a quien esté cerca. */
public class ArcadeFramePacket {
    final BlockPos pos;
    final byte[] datos;

    public ArcadeFramePacket(BlockPos pos, byte[] datos) {
        this.pos = pos;
        this.datos = datos;
    }

    public ArcadeFramePacket(FriendlyByteBuf b) {
        this(b.readBlockPos(), b.readByteArray(200000));
    }

    public void encode(FriendlyByteBuf b) {
        b.writeBlockPos(pos);
        b.writeByteArray(datos);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p == null || !(p.level().getBlockEntity(pos) instanceof RecreativaBlockEntity be)) return;
        if (be.jugador == null || !be.jugador.equals(p.getUUID())) return;
        long ahora = System.currentTimeMillis();
        if (ahora - be.ultimoFrame < 60) return;
        be.ultimoFrame = ahora;
        ServerLevel l = p.serverLevel();
        ArcadeFrameS2CPacket m = new ArcadeFrameS2CPacket(pos, datos);
        for (ServerPlayer o : l.getChunkSource().chunkMap.getPlayers(new ChunkPos(pos), false))
            if (o != p && o.distanceToSqr(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5) < 48 * 48)
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> o), m);
    }
}

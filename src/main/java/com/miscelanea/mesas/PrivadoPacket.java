package com.miscelanea.mesas;

import com.miscelanea.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * Servidor → cliente: datos secretos de una partida que solo puede ver ese jugador (su mano de dominó, sus barcos...).
 * El resto de la partida llega a todos con la actualización normal del bloque, sin esos datos.
 */
public class PrivadoPacket {
    private final BlockPos pos;
    private final CompoundTag datos;

    public PrivadoPacket(BlockPos pos, CompoundTag datos) {
        this.pos = pos;
        this.datos = datos;
    }

    public PrivadoPacket(FriendlyByteBuf b) {
        this.pos = b.readBlockPos();
        CompoundTag t = b.readNbt();
        this.datos = t == null ? new CompoundTag() : t;
    }

    public void encode(FriendlyByteBuf b) {
        b.writeBlockPos(pos);
        b.writeNbt(datos);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        if (ctx.get().getSender() != null) return;   // solo viaja al cliente
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.mesas.PrivadoCliente.recibir(pos, datos));
    }

    public static void enviar(ServerPlayer p, BlockPos pos, CompoundTag datos) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new PrivadoPacket(pos, datos));
    }

    /** Lo implementan las entidades de bloque que reciben datos privados en el cliente. */
    public interface Receptor {
        void recibirPrivado(CompoundTag datos);
    }
}

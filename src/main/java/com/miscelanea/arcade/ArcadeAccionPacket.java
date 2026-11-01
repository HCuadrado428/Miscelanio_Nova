package com.miscelanea.arcade;

import com.miscelanea.network.ModNetwork;
import com.miscelanea.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** Cliente → servidor: cerrar, empezar partida (gasta crédito), meter ficha o enviar puntuación. */
public class ArcadeAccionPacket {
    public static final int CERRAR = 0, EMPEZAR = 1, FICHA = 2, RECORD = 3;
    private final BlockPos pos;
    private final int accion, puntos;
    private final String juego;

    public ArcadeAccionPacket(BlockPos pos, int accion, String juego, int puntos) {
        this.pos = pos;
        this.accion = accion;
        this.juego = juego;
        this.puntos = puntos;
    }

    public ArcadeAccionPacket(FriendlyByteBuf b) {
        this(b.readBlockPos(), b.readByte(), b.readUtf(32), b.readVarInt());
    }

    public void encode(FriendlyByteBuf b) {
        b.writeBlockPos(pos);
        b.writeByte(accion);
        b.writeUtf(juego, 32);
        b.writeVarInt(puntos);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p == null || p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5) > 64) return;
        if (!(p.level().getBlockEntity(pos) instanceof RecreativaBlockEntity be)) return;
        if (be.jugador == null || !be.jugador.equals(p.getUUID())) return;
        boolean libre = p.getAbilities().instabuild;
        switch (accion) {
            case CERRAR -> be.jugador = null;
            case FICHA -> {
                if (RecreativaBlock.gastarFicha(p)) {
                    be.creditos++;
                    be.setChanged();
                    p.level().playSound(null, pos, ModSounds.ARCADE_FICHA.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
                }
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new ArcadeAbrirPacket(pos, be.creditos, libre, 1));
            }
            case EMPEZAR -> {
                boolean ok = libre || be.creditos > 0;
                if (ok && !libre) {
                    be.creditos--;
                    be.setChanged();
                }
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new ArcadeAbrirPacket(pos, be.creditos, libre, ok ? 2 : 3));
            }
            case RECORD -> ArcadeRecords.registrar(p, juego, puntos);
        }
    }
}

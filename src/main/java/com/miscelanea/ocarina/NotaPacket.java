package com.miscelanea.ocarina;

import com.miscelanea.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: he tocado esta nota (0..4). El resto de jugadores la oye y ve la nota musical. */
public class NotaPacket {
    private final int nota;

    public NotaPacket(int nota) {
        this.nota = nota;
    }

    public NotaPacket(FriendlyByteBuf b) {
        this.nota = b.readByte();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeByte(nota);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p == null || nota < 0 || nota > 4 || !OcarinaItem.tocando(p)) return;
        p.level().playSound(p, p.getX(), p.getEyeY(), p.getZ(), ModSounds.OCARINA[nota].get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        var dir = p.getLookAngle();
        p.serverLevel().sendParticles(ParticleTypes.NOTE, p.getX() + dir.x * 0.6, p.getEyeY() + 0.25, p.getZ() + dir.z * 0.6,
                0, nota * 6 / 24.0, 0, 0, 1);
    }
}

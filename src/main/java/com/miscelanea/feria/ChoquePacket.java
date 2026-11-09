package com.miscelanea.feria;

import com.miscelanea.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente (quien conduce) → servidor: el coche de choque ha chocado; el servidor lo hace sonar para los demás. */
public class ChoquePacket {
    final int id;
    final float fuerza;

    public ChoquePacket(int id, float fuerza) {
        this.id = id;
        this.fuerza = fuerza;
    }

    public ChoquePacket(FriendlyByteBuf b) {
        this(b.readVarInt(), b.readFloat());
    }

    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(id);
        b.writeFloat(fuerza);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p == null || !(p.level().getEntity(id) instanceof CocheChoque c) || c.getControllingPassenger() != p) return;
        float f = Math.max(0, Math.min(1, fuerza));
        com.miscelanea.logros.LogrosMiscelanea.dar(p, "choque");
        p.level().playSound(p, c.getX(), c.getY(), c.getZ(), ModSounds.FE_CHOQUE.get(), SoundSource.NEUTRAL, Math.min(1, 0.3F + f * 2), 1F);
        p.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK, c.getX(), c.getY() + 0.5, c.getZ(), 6, 0.4, 0.1, 0.4, 0.05);
    }
}

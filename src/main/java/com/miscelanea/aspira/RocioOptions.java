package com.miscelanea.aspira;

import com.miscelanea.registry.ModParticles;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;

/** Partícula del chorro del lanzallamas de pociones: lleva el color del tanque. */
public record RocioOptions(int color) implements ParticleOptions {
    public static final Codec<RocioOptions> CODEC = RecordCodecBuilder.create(i ->
            i.group(Codec.INT.fieldOf("color").forGetter(RocioOptions::color)).apply(i, RocioOptions::new));

    @SuppressWarnings("deprecation")
    public static final ParticleOptions.Deserializer<RocioOptions> DESERIALIZER = new ParticleOptions.Deserializer<>() {
        @Override
        public RocioOptions fromCommand(ParticleType<RocioOptions> type, StringReader reader) throws CommandSyntaxException {
            reader.expect(' ');
            return new RocioOptions(reader.readInt());
        }

        @Override
        public RocioOptions fromNetwork(ParticleType<RocioOptions> type, FriendlyByteBuf buf) {
            return new RocioOptions(buf.readInt());
        }
    };

    @Override
    public ParticleType<?> getType() {
        return ModParticles.ROCIO.get();
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buf) {
        buf.writeInt(color);
    }

    @Override
    public String writeToString() {
        return "miscelanea:rocio " + color;
    }
}

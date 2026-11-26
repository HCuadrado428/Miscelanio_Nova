package com.miscelanea.rave;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;

/** Partícula de humo de la rave: lleva su color. */
public record HumoOpciones(int color) implements ParticleOptions {
    public static final Codec<HumoOpciones> CODEC = RecordCodecBuilder.create(i ->
            i.group(Codec.INT.fieldOf("color").forGetter(HumoOpciones::color)).apply(i, HumoOpciones::new));

    @SuppressWarnings("deprecation")
    public static final ParticleOptions.Deserializer<HumoOpciones> DESERIALIZER = new ParticleOptions.Deserializer<>() {
        @Override
        public HumoOpciones fromCommand(ParticleType<HumoOpciones> type, StringReader reader) throws CommandSyntaxException {
            reader.expect(' ');
            return new HumoOpciones(reader.readInt());
        }

        @Override
        public HumoOpciones fromNetwork(ParticleType<HumoOpciones> type, FriendlyByteBuf buf) {
            return new HumoOpciones(buf.readInt());
        }
    };

    @Override
    public ParticleType<?> getType() {
        return RaveRegistro.HUMO_PARTICULA.get();
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buf) {
        buf.writeInt(color);
    }

    @Override
    public String writeToString() {
        return "miscelanea:humo_rave " + color;
    }
}

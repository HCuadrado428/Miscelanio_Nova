package com.miscelanea.rave;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;

/** Confeti: lleva su color (0 = de todos los colores). */
public record ConfetiOpciones(int color) implements ParticleOptions {
    public static final Codec<ConfetiOpciones> CODEC = RecordCodecBuilder.create(i ->
            i.group(Codec.INT.fieldOf("color").forGetter(ConfetiOpciones::color)).apply(i, ConfetiOpciones::new));

    @SuppressWarnings("deprecation")
    public static final ParticleOptions.Deserializer<ConfetiOpciones> DESERIALIZER = new ParticleOptions.Deserializer<>() {
        @Override
        public ConfetiOpciones fromCommand(ParticleType<ConfetiOpciones> type, StringReader reader) throws CommandSyntaxException {
            reader.expect(' ');
            return new ConfetiOpciones(reader.readInt());
        }

        @Override
        public ConfetiOpciones fromNetwork(ParticleType<ConfetiOpciones> type, FriendlyByteBuf buf) {
            return new ConfetiOpciones(buf.readInt());
        }
    };

    @Override
    public ParticleType<?> getType() {
        return RaveRegistro.CONFETI_PARTICULA.get();
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buf) {
        buf.writeInt(color);
    }

    @Override
    public String writeToString() {
        return "miscelanea:confeti_rave " + color;
    }
}

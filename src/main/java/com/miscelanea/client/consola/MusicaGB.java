package com.miscelanea.client.consola;

import com.miscelanea.registry.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** Música chiptune de la consola: en bucle, sin posición. */
public class MusicaGB extends AbstractTickableSoundInstance {
    public MusicaGB() {
        super(ModSounds.GB_MUSICA.get(), SoundSource.RECORDS, RandomSource.create());
        this.looping = true;
        this.delay = 0;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.volume = 0.45F;
    }

    @Override
    public void tick() {
        if (net.minecraft.client.Minecraft.getInstance().player == null) stop();
    }

    public void parar() {
        stop();
    }
}

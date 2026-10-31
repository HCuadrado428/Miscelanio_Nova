package com.miscelanea.client.pipboy;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** La música de la radio del Pip-Boy: suena "en tu cabeza" (sin posición), en bucle, con el volumen de música de discos. */
public class RadioSonido extends AbstractTickableSoundInstance {
    public RadioSonido(SoundEvent ev) {
        super(ev, SoundSource.RECORDS, RandomSource.create());
        this.looping = true;
        this.delay = 0;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.volume = 0.75F;
        this.x = 0;
        this.y = 0;
        this.z = 0;
    }

    @Override
    public void tick() {
        if (net.minecraft.client.Minecraft.getInstance().player == null) stop();
    }
}

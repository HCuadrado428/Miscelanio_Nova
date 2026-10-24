package com.miscelanea.client.medigun;

import com.miscelanea.registry.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

/** Zumbido en bucle que sigue al médico mientras su rayo está enganchado. */
public class RayoSonido extends AbstractTickableSoundInstance {
    private final Entity medico;

    public RayoSonido(Entity medico) {
        super(ModSounds.MEDIGUN_RAYO.get(), SoundSource.PLAYERS, RandomSource.create());
        this.medico = medico;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.55F;
        this.x = medico.getX();
        this.y = medico.getY() + 1.2;
        this.z = medico.getZ();
    }

    @Override
    public void tick() {
        if (medico.isRemoved() || !MedigunCliente.curando(medico.getId())) {
            stop();
            return;
        }
        this.x = medico.getX();
        this.y = medico.getY() + 1.2;
        this.z = medico.getZ();
        int c = MedigunCliente.colorUber(medico);
        this.pitch = c >= 0 ? 1.15F : 1.0F;
    }
}

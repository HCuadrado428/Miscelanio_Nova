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
        // Se corta en cuanto el médico suelta el clic (sin esperar al servidor)
        boolean usando = medico instanceof net.minecraft.world.entity.player.Player p && p.isUsingItem()
                && p.getUseItem().getItem() instanceof com.miscelanea.medigun.MedigunItem;
        if (medico.isRemoved() || !usando || !MedigunCliente.curando(medico.getId())) {
            volume -= 0.25F;
            if (volume <= 0.0F) stop();
            return;
        }
        volume = Math.min(0.55F, volume + 0.15F);
        this.x = medico.getX();
        this.y = medico.getY() + 1.2;
        this.z = medico.getZ();
        int c = MedigunCliente.colorUber(medico);
        this.pitch = c >= 0 ? 1.15F : 1.0F;
    }
}

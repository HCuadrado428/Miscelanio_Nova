package com.miscelanea.client.baile;

import com.miscelanea.baile.PistaBaileBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Canción de una pista de baile concreta, con posición en la pista. Se corta sola en cuanto la pista deja de existir
 * (rota, chunk descargado), cuando la pista ya no tiene esa canción, cuando nadie baila o si el jugador se aleja mucho.
 */
public class MusicaPista extends AbstractTickableSoundInstance {
    final BlockPos pista;
    private final String cancion;
    /** ¿Ya se ha visto en la pista (cliente) la canción puesta? Hasta entonces no se compara (el aviso llega antes que los datos). */
    private boolean vista;
    private int edad;

    public MusicaPista(SoundEvent ev, BlockPos pista, String cancion) {
        super(ev, SoundSource.RECORDS, RandomSource.create());
        this.pista = pista.immutable();
        this.cancion = cancion;
        this.looping = false;
        this.delay = 0;
        this.relative = false;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        // volumen 2 → se oye hasta 32 bloques (como el antiguo playSound del servidor)
        this.volume = 2.0F;
        this.x = pista.getX() + 0.5;
        this.y = pista.getY() + 0.5;
        this.z = pista.getZ() + 0.5;
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            stop();
            return;
        }
        edad++;
        if (!mc.level.isLoaded(pista) || !(mc.level.getBlockEntity(pista) instanceof PistaBaileBlockEntity be) || be.isRemoved()) {
            stop();
            return;
        }
        if (cancion.equals(be.cancion) && be.bailarin != null) vista = true;
        else if (vista || edad > 100) {
            // la pista ya no tiene esta canción (terminó, cambió o se bajó el bailarín)
            stop();
            return;
        }
        if (mc.player.distanceToSqr(x, y, z) > 64 * 64) stop();
    }

    /** Tics que lleva sonando (deja de contar cuando el motor de sonido la suelta). */
    int edadTics() {
        return edad;
    }
}

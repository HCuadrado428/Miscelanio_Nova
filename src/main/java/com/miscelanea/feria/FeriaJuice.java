package com.miscelanea.feria;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Efectos de celebración de los puestos (lado servidor): lluvia de confeti de colores y chispitas al ganar. */
public final class FeriaJuice {
    private static final int[] CONFETI = {0xFF3B3B, 0xFFD23B, 0x3BD26A, 0x3B9BFF, 0xE45BFF, 0xFFFFFF};

    private FeriaJuice() {}

    /** Confeti en c; 'fuerza' 1 = premio normal, 2 o más = premio gordo (más papelitos, chispas y un silbido). */
    public static void confeti(ServerLevel l, Vec3 c, int fuerza) {
        int n = 4 + 4 * fuerza;
        double ab = 0.35 + 0.15 * fuerza;
        for (int col : CONFETI) {
            Vector3f v = new Vector3f((col >> 16 & 255) / 255F, (col >> 8 & 255) / 255F, (col & 255) / 255F);
            l.sendParticles(new DustParticleOptions(v, 0.9F + 0.3F * l.random.nextFloat()), c.x, c.y, c.z, n, ab, ab * 0.6, ab, 0);
        }
        l.sendParticles(ParticleTypes.FIREWORK, c.x, c.y, c.z, 6 * fuerza, 0.1, 0.1, 0.1, 0.12);
        if (fuerza >= 2) {
            l.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, c.x, c.y, c.z, 30, 0.3, 0.3, 0.3, 0.35);
            l.playSound(null, c.x, c.y, c.z, SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.BLOCKS, 0.7F, 1.1F);
        }
    }
}

package com.miscelanea.client.baile.videoclip;

import com.miscelanea.baile.CancionesBaile;
import com.miscelanea.client.baile.Coreografia;

/**
 * Por dónde va el protagonista en el videoclip, sacado de su coreografía: con el pavoneo avanza por el vestíbulo, en
 * el vuelo sube (y sigue avanzando despacio) y al aterrizar baja. Se calcula una vez por canción, cada cuarto de
 * pulso, con arranques y frenadas suaves; así es igual en todos los fotogramas y para todos los que bailan.
 */
final class Recorrido {
    private static final float PASO = 0.25F;
    private final float[] z, y;

    Recorrido(CancionesBaile.Cancion c) {
        int n = (int) ((c.pulso(c.duracion) + 8) / PASO) + 2;
        z = new float[n];
        y = new float[n];
        float vz = 0, h = 0, zz = 0;
        for (int i = 1; i < n; i++) {
            float p = (i - 1) * PASO;
            String paso = Coreografia.pasoEn(c, p);
            float dt = Math.max(0.001F, c.segundo(p + PASO) - c.segundo(p));
            float objV = switch (paso) {
                case "pavoneo" -> 1.15F;
                case "vuelo" -> 0.8F;
                case "deslizar" -> 0.35F;
                default -> 0;
            };
            float objH = paso.equals("vuelo") ? 4.2F : 0;
            vz += (objV - vz) * (1 - (float) Math.exp(-dt / 0.45F));
            h += (objH - h) * (1 - (float) Math.exp(-dt / (objH > h ? 1.7F : 1.2F)));
            zz += vz * dt;
            z[i] = zz;
            y[i] = h;
        }
    }

    /** {avance, altura} en el pulso p. */
    float[] en(float p) {
        if (p <= 0) return new float[]{0, 0};
        float f = p / PASO;
        int i = (int) f;
        if (i >= z.length - 1) return new float[]{z[z.length - 1], y[y.length - 1]};
        float k = f - i;
        return new float[]{z[i] + (z[i + 1] - z[i]) * k, y[i] + (y[i + 1] - y[i]) * k};
    }
}

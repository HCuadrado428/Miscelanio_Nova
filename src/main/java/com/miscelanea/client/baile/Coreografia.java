package com.miscelanea.client.baile;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.miscelanea.Miscelanea;
import com.miscelanea.baile.CancionesBaile;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/**
 * Coreografía de la pista de baile: los pasos de baile (assets/miscelanea/baile/pasos.json) y cómo se encadenan en
 * cada canción (las secciones de su JSON, ver {@link CancionesBaile}). La pose sale SOLO del tiempo de la canción (no de
 * las teclas): cada paso va en pulsos y se estira o encoge con los BPM; entre paso y paso se funde medio pulso.
 * <p>
 * Una pose son 7 piezas × 3 números: ra, la, rl, ll, hd (brazos, piernas y cabeza: giros x, y, z en radianes, como las
 * piezas del modelo), b (giro de todo el cuerpo: x hacia atrás, y gira, z hacia la izquierda) y pos (desplazamiento del
 * cuerpo en bloques: x a la derecha, y arriba, z hacia atrás). Es lo mismo que dibuja scratchpad/baile_prev.py.
 * <p>
 * pasos.json: {"pasos": {"nombre": {"pulsos": 4, "bucle": true, "claves": [{"t": 0, "e": "suave", "ra": [x,y,z], ...}]}}}
 * con t en pulsos dentro del paso y "e" la curva con la que se llega a esa clave: lin, suave, sale (frena al llegar),
 * entra (acelera), pasa (se pasa un poco y vuelve: el remate) o seco (salta de golpe, para el robot).
 */
public final class Coreografia {
    public static final int N = 21;
    static final String[] PIEZAS = {"ra", "la", "rl", "ll", "hd", "b", "pos"};
    /** Pulsos que dura la mezcla de un paso con el siguiente. */
    static final float MEZCLA = 0.5F;

    static final class Paso {
        float pulsos;
        boolean bucle = true;
        float[] t;
        int[] e;
        float[][] v;
    }

    private static Map<String, Paso> PASOS;

    private Coreografia() {}

    static int curva(String s) {
        return switch (s) {
            case "lin" -> 0;
            case "sale" -> 2;
            case "entra" -> 3;
            case "pasa" -> 4;
            case "seco" -> 5;
            default -> 1;
        };
    }

    static float aplicar(int e, float x) {
        x = Mth.clamp(x, 0F, 1F);
        switch (e) {
            case 0: return x;
            case 2: { float u = 1F - x; return 1F - u * u * u; }
            case 3: return x * x * x;
            case 4: { float c = 1.7F, u = x - 1F; return 1F + (c + 1F) * u * u * u + c * u * u; }
            case 5: return x < 0.999F ? 0F : 1F;
            default: return x * x * (3F - 2F * x);
        }
    }

    static synchronized Map<String, Paso> pasos() {
        if (PASOS != null) return PASOS;
        Map<String, Paso> m = new HashMap<>();
        JsonObject o = CancionesBaile.leer("/assets/" + Miscelanea.MOD_ID + "/baile/pasos.json");
        if (o != null && o.has("pasos")) for (Map.Entry<String, JsonElement> en : o.getAsJsonObject("pasos").entrySet()) {
            try {
                JsonObject po = en.getValue().getAsJsonObject();
                Paso p = new Paso();
                p.pulsos = po.get("pulsos").getAsFloat();
                if (po.has("bucle")) p.bucle = po.get("bucle").getAsBoolean();
                JsonArray cs = po.getAsJsonArray("claves");
                p.t = new float[cs.size()];
                p.e = new int[cs.size()];
                p.v = new float[cs.size()][N];
                for (int i = 0; i < cs.size(); i++) {
                    JsonObject k = cs.get(i).getAsJsonObject();
                    p.t[i] = k.get("t").getAsFloat();
                    p.e[i] = curva(k.has("e") ? k.get("e").getAsString() : "suave");
                    for (int j = 0; j < PIEZAS.length; j++) {
                        if (!k.has(PIEZAS[j])) continue;
                        JsonArray a = k.getAsJsonArray(PIEZAS[j]);
                        for (int q = 0; q < 3; q++) p.v[i][j * 3 + q] = a.get(q).getAsFloat();
                    }
                }
                if (p.t.length > 0) m.put(en.getKey(), p);
            } catch (Exception ex) {
                Miscelanea.LOGGER.warn("Pista de baile: paso {} mal escrito: {}", en.getKey(), ex.toString());
            }
        }
        PASOS = m;
        return m;
    }

    /** Pose del paso en el pulso u (dentro del paso; si es en bucle, vuelve a empezar). */
    static float[] evaluar(String nombre, float u, boolean espejo) {
        float[] out = new float[N];
        Paso p = pasos().get(nombre);
        if (p == null) p = pasos().get("groove");
        if (p == null) return out;
        if (p.bucle) u = ((u % p.pulsos) + p.pulsos) % p.pulsos;
        else u = Mth.clamp(u, 0F, p.pulsos);
        int n = p.t.length;
        if (u <= p.t[0] || n == 1) System.arraycopy(p.v[0], 0, out, 0, N);
        else if (u >= p.t[n - 1]) System.arraycopy(p.v[n - 1], 0, out, 0, N);
        else for (int i = 1; i < n; i++) {
            if (u > p.t[i]) continue;
            float k = aplicar(p.e[i], (u - p.t[i - 1]) / Math.max(1e-4F, p.t[i] - p.t[i - 1]));
            for (int q = 0; q < N; q++) out[q] = p.v[i - 1][q] + (p.v[i][q] - p.v[i - 1][q]) * k;
            break;
        }
        // el giro de todo el cuerpo, en (-π, π]: una vuelta entera es como no girar (y así se funde bien con el siguiente)
        out[16] = Mth.wrapDegrees(out[16] * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
        return espejo ? espejo(out) : out;
    }

    /** La misma pose con el otro lado del cuerpo. */
    static float[] espejo(float[] v) {
        float[] o = new float[N];
        int[] cambio = {1, 0, 3, 2, 4, 5, 6};
        for (int j = 0; j < 7; j++) {
            int s = cambio[j] * 3;
            if (j == 6) {
                o[18] = -v[18];
                o[19] = v[19];
                o[20] = v[20];
            } else {
                o[j * 3] = v[s];
                o[j * 3 + 1] = -v[s + 1];
                o[j * 3 + 2] = -v[s + 2];
            }
        }
        return o;
    }

    /** Pose en el segundo s de la canción (null = sin canción: el vaivén de esperar en el menú). */
    public static float[] pose(CancionesBaile.Cancion c, float s) {
        if (c == null) return evaluar("groove", s * 100F / 60F, false);
        float pulso = c.pulso(s);
        int i = c.tramo(pulso);
        if (i < 0) return evaluar("groove", pulso, false);
        CancionesBaile.Tramo tr = c.tramos.get(i);
        // al acabar la canción se queda en la última pose del último paso
        float local = Math.min(pulso, tr.hasta) - tr.desde;
        float[] v = evaluar(tr.paso, local * tr.vel, tr.espejo);
        if (local < MEZCLA) {
            float[] antes = i > 0 ? evaluar(c.tramos.get(i - 1).paso, (pulso - c.tramos.get(i - 1).desde) * c.tramos.get(i - 1).vel,
                    c.tramos.get(i - 1).espejo) : evaluar("groove", pulso, false);
            float k = aplicar(1, local / MEZCLA);
            for (int q = 0; q < N; q++) {
                float a = antes[q], b = v[q];
                if (q == 16) b = a + Mth.wrapDegrees((b - a) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
                v[q] = a + (b - a) * k;
            }
        }
        return v;
    }

    /** Nombre del paso que se baila en el pulso p (para la cámara), o "groove". */
    public static String pasoEn(CancionesBaile.Cancion c, float p) {
        int i = c.tramo(p);
        return i < 0 ? "groove" : c.tramos.get(i).paso;
    }
}

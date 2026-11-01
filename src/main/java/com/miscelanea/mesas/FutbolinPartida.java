package com.miscelanea.mesas;

import java.util.Random;

/**
 * Futbolín: campo de 3x2 bloques (u a lo largo, v a lo ancho), 8 barras (4 por equipo) con sus muñecos, pelota con física
 * y chut. El equipo 0 defiende la portería de u=0 y ataca hacia u+; el 1 al revés. Sin nada de Minecraft.
 */
public class FutbolinPartida {
    public static final float LARGO = 3, ANCHO = 2, U0 = 0.2F, U1 = 2.8F, V0 = 0.25F, V1 = 1.75F, R = 0.035F;
    public static final float PORTERIA = 0.26F, ALCANCE = 0.05F;
    public static final int ESPERA = 0, JUEGA = 1, GOL = 2, FIN = 3, META = 5;
    /** Barras en orden a lo largo: equipo, muñecos, separación. */
    public static final int[] EQUIPO = {0, 0, 1, 0, 1, 0, 1, 1};
    public static final int[] MUNECOS = {1, 2, 3, 5, 5, 3, 2, 1};
    public static final float[] SEPARA = {0, 0.5F, 0.4F, 0.27F, 0.27F, 0.4F, 0.5F, 0};

    public float bu = LARGO / 2, bv = ANCHO / 2, vu, vv;
    public final float[] s = new float[2];        // desplazamiento de cada equipo (-1..1)
    final float[] velS = new float[2];
    public final int[] chut = new int[2];         // tics que quedan de la patada
    public final int[] goles = new int[2];
    public final int[] mando = new int[2];        // bits 1 izq, 2 der, 4 chut
    public final boolean[] cpu = new boolean[2];
    public int fase = ESPERA, t, ultimoGol = -1, quieto;
    // eventos para sonidos
    public int golpes, paredes, gol;
    final Random rnd = new Random();
    final float[] objetivoCpu = new float[2];

    public static float barraU(int k) {
        return U0 + (U1 - U0) * (k + 0.5F) / 8;
    }

    public static float limite(int k) {
        float l = (V1 - V0) / 2 - 0.07F - SEPARA[k] * (MUNECOS[k] - 1) / 2;
        if (MUNECOS[k] == 1) l = Math.min(l, PORTERIA + 0.06F);
        return Math.max(0.02F, l);
    }

    /** Posición v del muñeco i de la barra k. */
    public float munecoV(int k, int i) {
        return ANCHO / 2 + s[EQUIPO[k]] * limite(k) + (i - (MUNECOS[k] - 1) / 2F) * SEPARA[k];
    }

    public void empezar() {
        goles[0] = goles[1] = 0;
        fase = JUEGA;
        saque(rnd.nextInt(2));
    }

    void saque(int haciaEquipo) {
        bu = LARGO / 2;
        bv = ANCHO / 2 + (rnd.nextFloat() - 0.5F) * 0.3F;
        vu = (haciaEquipo == 0 ? -1 : 1) * 0.25F;
        vv = (rnd.nextFloat() - 0.5F) * 0.8F;
        quieto = 0;
    }

    public void tic() {
        t++;
        if (fase == GOL) {
            if (t > 50) {
                fase = goles[0] >= META || goles[1] >= META ? FIN : JUEGA;
                if (fase == JUEGA) saque(ultimoGol);
            }
            return;
        }
        if (fase != JUEGA) return;
        for (int e = 0; e < 2; e++) {
            if (cpu[e]) mando[e] = cerebro(e);
            int m = mando[e];
            float dir = ((m & 1) != 0 ? 1 : 0) - ((m & 2) != 0 ? 1 : 0);   // empujar (alejar) o tirar (acercar)
            float sentido = e == 0 ? 1 : -1;                                   // el equipo 0 está en el lado v=0: empujar es v+
            float objetivo = dir * sentido * (cpu[e] ? 2.2F : 2.6F);
            velS[e] += (objetivo - velS[e]) * 0.5F;
            if ((m & 4) != 0 && chut[e] == 0) chut[e] = 9;
        }
        float dt = 1 / 200F;
        for (int k = 0; k < 10; k++) paso(dt);
        for (int e = 0; e < 2; e++) if (chut[e] > 0) chut[e]--;
        float v = (float) Math.sqrt(vu * vu + vv * vv);
        quieto = v < 0.06F ? quieto + 1 : 0;
        if (quieto > 60) {
            // pelota muerta: saque desde la banda en el centro del campo
            bu = LARGO / 2 + (rnd.nextFloat() - 0.5F) * 0.2F;
            bv = rnd.nextBoolean() ? V0 + 0.05F : V1 - 0.05F;
            vv = bv < ANCHO / 2 ? 0.6F : -0.6F;
            vu = (rnd.nextFloat() - 0.5F) * 0.4F;
            quieto = 0;
        }
    }

    void paso(float dt) {
        for (int e = 0; e < 2; e++) s[e] = Math.max(-1, Math.min(1, s[e] + velS[e] * dt));
        float v = (float) Math.sqrt(vu * vu + vv * vv);
        if (v > 0) {
            float nv = Math.max(0, v - 0.35F * dt);
            vu *= nv / v;
            vv *= nv / v;
        }
        bu += vu * dt;
        bv += vv * dt;
        // muñecos
        for (int k = 0; k < 8; k++) {
            int e = EQUIPO[k];
            float u = barraU(k), dirAtaque = e == 0 ? 1 : -1;
            for (int i = 0; i < MUNECOS[k]; i++) {
                float mv = munecoV(k, i);
                float du = bu - u, dv = bv - mv;
                // chut: la patada pilla la pelota que está justo delante o debajo del muñeco
                if (chut[e] >= 5 && du * dirAtaque > -0.05F && du * dirAtaque < 0.14F && Math.abs(dv) < 0.075F) {
                    float vel = velS[e] * limite(k);
                    vu = dirAtaque * (3.0F + rnd.nextFloat() * 0.6F);
                    if (cpu[e]) {
                        // la máquina apunta al lado de la portería donde no está el portero rival
                        int portero = e == 0 ? 7 : 0;
                        float pv = munecoV(portero, 0), meta = e == 0 ? U1 : U0;
                        float destino = ANCHO / 2 + (pv > ANCHO / 2 ? -1 : 1) * 0.17F + (rnd.nextFloat() - 0.5F) * 0.12F;
                        vv = (destino - bv) / Math.max(0.2F, Math.abs(meta - bu)) * Math.abs(vu);
                    } else vv = dv * 10 + vel * 0.6F + (rnd.nextFloat() - 0.5F) * 0.3F;
                    bu = u + dirAtaque * 0.115F;
                    golpes++;
                    continue;
                }
                // si no chuta, el muñeco es un obstáculo
                float d2 = du * du + dv * dv, min = R + ALCANCE;
                if (d2 < min * min && chut[e] < 3) {
                    float d = (float) Math.sqrt(d2);
                    if (d < 1e-5F) {
                        du = dirAtaque;
                        dv = 0;
                        d = 1;
                    }
                    float nx = du / d, ny = dv / d;
                    bu = u + nx * min;
                    bv = mv + ny * min;
                    float rel = vu * nx + vv * ny;
                    float vm = velS[e] * limite(k);
                    if (rel < 0) {
                        vu -= 1.6F * rel * nx;
                        vv -= 1.6F * rel * ny;
                    }
                    vv += vm * 0.3F * Math.abs(ny);
                    if (Math.abs(rel) > 0.3F) golpes++;
                }
            }
        }
        // bandas y porterías
        if (bv < V0 + R) {
            bv = V0 + R;
            vv = Math.abs(vv) * 0.85F;
            paredes++;
        } else if (bv > V1 - R) {
            bv = V1 - R;
            vv = -Math.abs(vv) * 0.85F;
            paredes++;
        }
        boolean enPorteria = Math.abs(bv - ANCHO / 2) < PORTERIA - R * 0.5F;
        if (bu < U0 + R) {
            if (enPorteria) {
                if (bu < U0 - R) marcar(1);
            } else {
                bu = U0 + R;
                vu = Math.abs(vu) * 0.85F;
                paredes++;
            }
        } else if (bu > U1 - R) {
            if (enPorteria) {
                if (bu > U1 + R) marcar(0);
            } else {
                bu = U1 - R;
                vu = -Math.abs(vu) * 0.85F;
                paredes++;
            }
        }
    }

    void marcar(int equipo) {
        if (fase != JUEGA) return;
        goles[equipo]++;
        ultimoGol = 1 - equipo;      // saca hacia el que ha encajado
        fase = GOL;
        t = 0;
        gol++;
        vu = vv = 0;
    }

    /** La máquina: alinea el muñeco más cercano de la barra adecuada con la pelota y chuta cuando la tiene delante. */
    int cerebro(int e) {
        float dirAtaque = e == 0 ? 1 : -1;
        if (t % 3 == 0) {
            // barra propia más cercana que esté detrás de la pelota (o la más cercana en general)
            int mejor = -1;
            float md = 1e9F;
            float prevista = bv + vv * 0.12F;
            for (int k = 0; k < 8; k++) {
                if (EQUIPO[k] != e) continue;
                float du = (bu - barraU(k)) * dirAtaque;
                float d = du >= -0.04F ? du : 2 - du;
                if (d < md) {
                    md = d;
                    mejor = k;
                }
            }
            // elegir el muñeco más cercano a la pelota y calcular qué desplazamiento lo pone delante
            float objetivo = s[e], mejorDist = 1e9F;
            for (int i = 0; i < MUNECOS[mejor]; i++) {
                float base = ANCHO / 2 + (i - (MUNECOS[mejor] - 1) / 2F) * SEPARA[mejor];
                float sNecesaria = (prevista - base) / limite(mejor);
                if (Math.abs(sNecesaria) > 1.05F) continue;
                float d = Math.abs(sNecesaria - s[e]);
                if (d < mejorDist) {
                    mejorDist = d;
                    objetivo = sNecesaria;
                }
            }
            objetivoCpu[e] = Math.max(-1, Math.min(1, objetivo + (rnd.nextFloat() - 0.5F) * 0.06F));
        }
        int m = 0;
        float sentido = e == 0 ? 1 : -1;
        float dif = objetivoCpu[e] - s[e];
        if (Math.abs(dif) > 0.03F) m |= (dif * sentido > 0) ? 1 : 2;
        // chutar si algún muñeco propio tiene la pelota delante
        for (int k = 0; k < 8; k++) {
            if (EQUIPO[k] != e) continue;
            float du = (bu - barraU(k)) * dirAtaque;
            if (du < -0.03F || du > 0.12F) continue;
            for (int i = 0; i < MUNECOS[k]; i++)
                if (Math.abs(bv - munecoV(k, i)) < 0.05F && chut[e] == 0 && rnd.nextInt(3) == 0) m |= 4;
        }
        return m;
    }

    public float[] estado() {
        return new float[]{bu, bv, s[0], s[1], chut[0], chut[1], goles[0], goles[1], fase};
    }

    public void ponerEstado(float[] d) {
        if (d.length < 9) return;
        bu = d[0];
        bv = d[1];
        s[0] = d[2];
        s[1] = d[3];
        chut[0] = (int) d[4];
        chut[1] = (int) d[5];
        goles[0] = (int) d[6];
        goles[1] = (int) d[7];
        fase = (int) d[8];
    }
}

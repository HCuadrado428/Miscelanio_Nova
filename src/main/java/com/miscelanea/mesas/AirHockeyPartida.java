package com.miscelanea.mesas;

import java.util.Random;

/**
 * Air hockey: mesa de 3x2 bloques (u a lo largo, v a lo ancho). El equipo 0 defiende la portería de u=0 y el 1 la de
 * u=3; cada uno mueve su mazo por su mitad. Disco con poco rozamiento que rebota en las bandas. Partido a 7.
 */
public class AirHockeyPartida {
    public static final float LARGO = 3, ANCHO = 2, U0 = 0.12F, U1 = 2.88F, V0 = 0.14F, V1 = 1.86F;
    public static final float RD = 0.07F, RM = 0.11F, PORTERIA = 0.3F, VMAX = 0.32F;
    public static final int ESPERA = 0, JUEGA = 1, GOL = 2, FIN = 3, META = 7;
    public float pu = LARGO / 2, pv = ANCHO / 2, vu, vv;
    public final float[] mu = {0.5F, 2.5F}, mv = {1, 1}, mvu = new float[2], mvv = new float[2];
    public final int[] goles = new int[2], mando = new int[2];
    public final boolean[] cpu = new boolean[2];
    public int fase = ESPERA, t, golpes, paredes, gol;
    final Random rnd = new Random();

    public void empezar() {
        goles[0] = goles[1] = 0;
        fase = JUEGA;
        saque(rnd.nextInt(2));
    }

    void saque(int hacia) {
        pu = hacia == 0 ? 1.0F : 2.0F;
        pv = ANCHO / 2;
        vu = vv = 0;
        mu[0] = 0.4F;
        mu[1] = 2.6F;
        mv[0] = mv[1] = 1;
        t = 0;
    }

    public void tic() {
        t++;
        if (fase == GOL) {
            if (t > 40) {
                fase = JUEGA;
                saque(gol > 0 ? gol - 1 : rnd.nextInt(2));
            }
            return;
        }
        if (fase != JUEGA) return;
        for (int e = 0; e < 2; e++) moverMazo(e);
        // disco
        pu += vu;
        pv += vv;
        vu *= 0.996F;
        vv *= 0.996F;
        if (pv < V0 + RD) {
            pv = V0 + RD;
            vv = Math.abs(vv) * 0.9F;
            paredes++;
        } else if (pv > V1 - RD) {
            pv = V1 - RD;
            vv = -Math.abs(vv) * 0.9F;
            paredes++;
        }
        boolean enHueco = Math.abs(pv - ANCHO / 2) < PORTERIA - RD * 0.5F;
        if (pu < U0 + RD && !enHueco) {
            pu = U0 + RD;
            vu = Math.abs(vu) * 0.9F;
            paredes++;
        } else if (pu > U1 - RD && !enHueco) {
            pu = U1 - RD;
            vu = -Math.abs(vu) * 0.9F;
            paredes++;
        }
        if (pu < U0 - 0.05F || pu > U1 + 0.05F) {
            int marca = pu < U0 ? 1 : 0;
            goles[marca]++;
            gol = marca + 1;
            t = 0;
            fase = goles[marca] >= META ? FIN : GOL;
            if (fase == GOL) gol = (1 - marca) + 1;  // saca quien encaja
            golpes = 0;
            return;
        }
        for (int e = 0; e < 2; e++) choque(e);
        float vel = (float) Math.hypot(vu, vv);
        if (vel > VMAX) {
            vu *= VMAX / vel;
            vv *= VMAX / vel;
        }
        // disco parado en un campo sin nadie que lo alcance: empujoncito
        if (vel < 0.002F && t % 80 == 0) {
            vu += (pu < LARGO / 2 ? 0.02F : -0.02F);
        }
    }

    /** Mando: bits 1 adelante (hacia el rival), 2 atrás, 4 izquierda, 8 derecha (vistos desde su portería). */
    void moverMazo(int e) {
        float du = 0, dv = 0;
        if (cpu[e]) {
            float[] o = objetivoCpu(e);
            du = Math.max(-1, Math.min(1, (o[0] - mu[e]) * 12));
            dv = Math.max(-1, Math.min(1, (o[1] - mv[e]) * 12));
            du *= 0.8F;
            dv *= 0.8F;
        } else {
            int m = mando[e];
            float adelante = ((m & 1) != 0 ? 1 : 0) - ((m & 2) != 0 ? 1 : 0);
            float derecha = ((m & 8) != 0 ? 1 : 0) - ((m & 4) != 0 ? 1 : 0);
            // equipo 0 mira hacia u+ (su derecha es v-); el 1 mira hacia u- (su derecha es v+)
            du = e == 0 ? adelante : -adelante;
            dv = e == 0 ? -derecha : derecha;
        }
        float acel = 0.012F, max = 0.07F;
        mvu[e] = Math.max(-max, Math.min(max, mvu[e] * 0.8F + du * acel));
        mvv[e] = Math.max(-max, Math.min(max, mvv[e] * 0.8F + dv * acel));
        if (du == 0) mvu[e] *= 0.6F;
        if (dv == 0) mvv[e] *= 0.6F;
        float uMin = e == 0 ? U0 + RM : LARGO / 2 + RM, uMax = e == 0 ? LARGO / 2 - RM : U1 - RM;
        mu[e] = Math.max(uMin, Math.min(uMax, mu[e] + mvu[e]));
        mv[e] = Math.max(V0 + RM, Math.min(V1 - RM, mv[e] + mvv[e]));
    }

    float[] objetivoCpu(int e) {
        boolean miCampo = e == 0 ? pu < LARGO / 2 : pu > LARGO / 2;
        float casa = e == 0 ? U0 + 0.25F : U1 - 0.25F;
        boolean viene = e == 0 ? vu < 0 : vu > 0;
        if (miCampo && (!viene || Math.abs(pu - casa) > 0.15F)) {
            // ir a por el disco, colocándose detrás para tirar hacia la portería contraria
            float detras = e == 0 ? -0.12F : 0.12F;
            return new float[]{pu + detras, pv + (pv - ANCHO / 2) * 0.15F};
        }
        // defender: entre el disco y la portería
        float v = ANCHO / 2 + (pv - ANCHO / 2) * 0.5F;
        return new float[]{casa, v};
    }

    void choque(int e) {
        float du = pu - mu[e], dv = pv - mv[e];
        float d = (float) Math.hypot(du, dv);
        if (d >= RD + RM || d < 1e-5F) return;
        float nu = du / d, nv = dv / d;
        pu = mu[e] + nu * (RD + RM);
        pv = mv[e] + nv * (RD + RM);
        float ru = vu - mvu[e], rv = vv - mvv[e];
        float rel = ru * nu + rv * nv;
        if (rel < 0) {
            vu -= 1.9F * rel * nu;
            vv -= 1.9F * rel * nv;
        }
        vu += mvu[e] * 0.6F + nu * 0.01F;
        vv += mvv[e] * 0.6F + nv * 0.01F;
        golpes++;
    }

    public float[] estado() {
        return new float[]{pu, pv, mu[0], mv[0], mu[1], mv[1]};
    }

    public void ponerEstado(float[] d) {
        if (d.length < 6) return;
        pu = d[0];
        pv = d[1];
        mu[0] = d[2];
        mv[0] = d[3];
        mu[1] = d[4];
        mv[1] = d[5];
    }
}

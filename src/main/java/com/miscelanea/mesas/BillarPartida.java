package com.miscelanea.mesas;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Billar americano de bola 8: física de las 16 bolas en la mesa (coordenadas en bloques: u a lo largo 0..4, v a lo ancho
 * 0..2), troneras, bandas y reglas (grupos lisas/rayadas, faltas, la negra al final). Sin nada de Minecraft para poder
 * probarla aparte.
 */
public class BillarPartida {
    public static final float LARGO = 4, ANCHO = 2, BORDE = 0.3F, R = 0.055F;
    public static final float U0 = BORDE, U1 = LARGO - BORDE, V0 = BORDE, V1 = ANCHO - BORDE;
    public static final float[][] TRONERAS = {{U0 - 0.02F, V0 - 0.02F}, {LARGO / 2, V0 - 0.05F}, {U1 + 0.02F, V0 - 0.02F},
            {U0 - 0.02F, V1 + 0.02F}, {LARGO / 2, V1 + 0.05F}, {U1 + 0.02F, V1 + 0.02F}};
    static final float CAPTURA = 0.105F, BOCA = 0.16F, FRICCION = 0.75F, VMAX = 5.5F;
    public static final int ESPERA = 0, APUNTA = 1, MUEVE = 2, FIN = 3;
    public static final int SIN = 0, LISAS = 1, RAYADAS = 2;

    public final float[] x = new float[16], y = new float[16], vx = new float[16], vy = new float[16];
    public final boolean[] dentro = new boolean[16];
    public int fase = ESPERA, turno, ganador = -1;
    public final int[] grupo = new int[2];
    public String mensaje = "billar.miscelanea.msg.espera";
    public String mensajeArg = "";
    // seguimiento del tiro en curso
    int primeraTocada = -1;
    final List<Integer> metidas = new ArrayList<>();
    // eventos para sonidos (los recoge la entidad en cada tic)
    public int choques, bandas, troneras;
    public float choqueFuerte;

    public void colocar(Random rnd) {
        for (int i = 0; i < 16; i++) {
            dentro[i] = true;
            vx[i] = vy[i] = 0;
        }
        // triángulo: la 8 en el centro y en las esquinas de atrás una lisa y una rayada
        List<Integer> resto = new ArrayList<>();
        for (int i = 1; i < 16; i++) if (i != 8) resto.add(i);
        java.util.Collections.shuffle(resto, rnd);
        int[] orden = new int[15];
        int k = 0;
        for (int i = 0; i < 15; i++) orden[i] = i == 4 ? 8 : resto.get(k++);
        // posiciones 10 y 14 son las esquinas de la última fila
        if (lisa(orden[10]) == lisa(orden[14]))
            for (int i = 0; i < 10; i++)
                if (orden[i] != 8 && lisa(orden[i]) != lisa(orden[14])) {
                    int t = orden[i];
                    orden[i] = orden[10];
                    orden[10] = t;
                    break;
                }
        float apex = LARGO * 0.68F, dx = R * 2 * 0.8661F + 0.002F;
        int idx = 0;
        for (int fila = 0; fila < 5; fila++)
            for (int c = 0; c <= fila; c++) {
                int b = orden[idx++];
                x[b] = apex + fila * dx;
                y[b] = ANCHO / 2 + (c - fila / 2F) * (R * 2 + 0.002F);
            }
        x[0] = LARGO * 0.25F;
        y[0] = ANCHO / 2;
        grupo[0] = grupo[1] = SIN;
        ganador = -1;
    }

    public static boolean lisa(int b) {
        return b >= 1 && b <= 7;
    }

    public static boolean rayada(int b) {
        return b >= 9;
    }

    public boolean moviendo() {
        for (int i = 0; i < 16; i++) if (dentro[i] && (vx[i] != 0 || vy[i] != 0)) return true;
        return false;
    }

    public boolean tirar(float angulo, float fuerza) {
        if (fase != APUNTA || !dentro[0]) return false;
        float f = Math.max(0.05F, Math.min(1, fuerza)) * VMAX;
        vx[0] = (float) Math.cos(angulo) * f;
        vy[0] = (float) Math.sin(angulo) * f;
        primeraTocada = -1;
        metidas.clear();
        fase = MUEVE;
        return true;
    }

    /** Avanza la física un tic (1/20 s) en pasos pequeños. Devuelve true cuando las bolas se acaban de parar. */
    public boolean tic() {
        if (fase != MUEVE) return false;
        float dt = 1 / 400F;
        for (int s = 0; s < 20; s++) paso(dt);
        if (!moviendo()) {
            resolver();
            return true;
        }
        return false;
    }

    void paso(float dt) {
        for (int i = 0; i < 16; i++) {
            if (!dentro[i]) continue;
            float v = (float) Math.sqrt(vx[i] * vx[i] + vy[i] * vy[i]);
            if (v == 0) continue;
            float nv = v - FRICCION * dt;
            if (nv < 0.01F) {
                vx[i] = vy[i] = 0;
                continue;
            }
            vx[i] *= nv / v;
            vy[i] *= nv / v;
            x[i] += vx[i] * dt;
            y[i] += vy[i] * dt;
        }
        // choques entre bolas (masas iguales, casi elásticos)
        for (int i = 0; i < 16; i++) {
            if (!dentro[i]) continue;
            for (int j = i + 1; j < 16; j++) {
                if (!dentro[j]) continue;
                float dx = x[j] - x[i], dy = y[j] - y[i];
                float d2 = dx * dx + dy * dy, min = 2 * R;
                if (d2 >= min * min || d2 == 0) continue;
                float d = (float) Math.sqrt(d2), nx = dx / d, ny = dy / d;
                float solape = (min - d) / 2;
                x[i] -= nx * solape;
                y[i] -= ny * solape;
                x[j] += nx * solape;
                y[j] += ny * solape;
                float rel = (vx[i] - vx[j]) * nx + (vy[i] - vy[j]) * ny;
                if (rel <= 0) continue;
                float imp = rel * 0.97F;
                vx[i] -= imp * nx;
                vy[i] -= imp * ny;
                vx[j] += imp * nx;
                vy[j] += imp * ny;
                if (i == 0 && primeraTocada < 0) primeraTocada = j;
                choques++;
                choqueFuerte = Math.max(choqueFuerte, rel);
            }
        }
        // troneras y bandas
        for (int i = 0; i < 16; i++) {
            if (!dentro[i]) continue;
            boolean boca = false;
            for (float[] t : TRONERAS) {
                float dx = x[i] - t[0], dy = y[i] - t[1];
                float d2 = dx * dx + dy * dy;
                if (d2 < CAPTURA * CAPTURA) {
                    dentro[i] = false;
                    vx[i] = vy[i] = 0;
                    metidas.add(i);
                    troneras++;
                    break;
                }
                if (d2 < BOCA * BOCA) boca = true;
            }
            if (!dentro[i]) continue;
            if (boca) {
                // en la boca de la tronera no hay banda: si la bola se sale del paño, cae dentro
                if (x[i] < U0 - R * 0.3F || x[i] > U1 + R * 0.3F || y[i] < V0 - R * 0.3F || y[i] > V1 + R * 0.3F) {
                    dentro[i] = false;
                    vx[i] = vy[i] = 0;
                    metidas.add(i);
                    troneras++;
                }
                continue;
            }
            if (x[i] < U0 + R) {
                x[i] = U0 + R;
                rebote(i, true);
            } else if (x[i] > U1 - R) {
                x[i] = U1 - R;
                rebote(i, true);
            }
            if (y[i] < V0 + R) {
                y[i] = V0 + R;
                rebote(i, false);
            } else if (y[i] > V1 - R) {
                y[i] = V1 - R;
                rebote(i, false);
            }
        }
    }

    void rebote(int i, boolean enX) {
        float antes = enX ? Math.abs(vx[i]) : Math.abs(vy[i]);
        if (enX) vx[i] = -vx[i] * 0.78F;
        else vy[i] = -vy[i] * 0.78F;
        if (antes > 0.3F) bandas++;
    }

    int quedan(int jugador) {
        int g = grupo[jugador], n = 0;
        if (g == SIN) return 7;
        for (int b = 1; b < 16; b++) if (dentro[b] && (g == LISAS ? lisa(b) : rayada(b))) n++;
        return n;
    }

    boolean suya(int jugador, int b) {
        int g = grupo[jugador];
        if (b == 8) return g != SIN && quedan(jugador) == 0;
        if (g == SIN) return b != 0;
        return g == LISAS ? lisa(b) : rayada(b);
    }

    /** Reglas al pararse las bolas. */
    void resolver() {
        int otro = 1 - turno;
        boolean blanca = !dentro[0];
        boolean negra = metidas.contains(8);
        boolean falta = blanca || primeraTocada < 0 || !suya(turno, primeraTocada);
        // reparto de grupos con la primera bola metida
        if (grupo[turno] == SIN && !falta)
            for (int b : metidas)
                if (b != 0 && b != 8) {
                    grupo[turno] = lisa(b) ? LISAS : RAYADAS;
                    grupo[otro] = lisa(b) ? RAYADAS : LISAS;
                    break;
                }
        if (negra) {
            boolean vale = !falta && grupo[turno] != SIN && quedan(turno) == 0;
            ganador = vale ? turno : otro;
            fase = FIN;
            mensaje = vale ? "billar.miscelanea.msg.gana_negra" : "billar.miscelanea.msg.pierde_negra";
            return;
        }
        boolean metioSuya = false;
        for (int b : metidas) if (b != 0 && (grupo[turno] == SIN || suya(turno, b))) metioSuya = true;
        if (blanca) recolocarBlanca();
        if (falta) {
            turno = otro;
            mensaje = blanca ? "billar.miscelanea.msg.falta_blanca" : "billar.miscelanea.msg.falta";
        } else if (metioSuya) {
            mensaje = "billar.miscelanea.msg.sigue";
        } else {
            turno = otro;
            mensaje = "billar.miscelanea.msg.turno";
        }
        fase = APUNTA;
    }

    void recolocarBlanca() {
        float px = LARGO * 0.25F, py = ANCHO / 2;
        for (int intento = 0; intento < 40; intento++) {
            boolean libre = true;
            for (int b = 1; b < 16; b++)
                if (dentro[b] && (x[b] - px) * (x[b] - px) + (y[b] - py) * (y[b] - py) < 4.4F * R * R) libre = false;
            if (libre) break;
            py += (intento % 2 == 0 ? 1 : -1) * (intento + 1) * R;
            py = Math.max(V0 + R, Math.min(V1 - R, py));
        }
        x[0] = px;
        y[0] = py;
        vx[0] = vy[0] = 0;
        dentro[0] = true;
    }

    // ------------------------------------------------------------------ la máquina
    /** Elige un tiro: {ángulo, fuerza}. Busca bola propia con camino libre a una tronera (bola fantasma). */
    public float[] tiroCpu(Random rnd, float puntería) {
        float mejor = -1e9F, ang = 0, fu = 0.6F;
        for (int b = 1; b < 16; b++) {
            if (!dentro[b] || !suya(turno, b)) continue;
            for (float[] t : TRONERAS) {
                float tx = t[0] - x[b], ty = t[1] - y[b];
                float dt = (float) Math.sqrt(tx * tx + ty * ty);
                tx /= dt;
                ty /= dt;
                float gx = x[b] - tx * 2 * R, gy = y[b] - ty * 2 * R;     // bola fantasma
                float ax = gx - x[0], ay = gy - y[0];
                float da = (float) Math.sqrt(ax * ax + ay * ay);
                if (da < 1e-4F) continue;
                float corte = (ax * tx + ay * ty) / da;                    // 1 = recto
                if (corte < 0.25F) continue;
                if (!libre(x[0], y[0], gx, gy, 0, b) || !libre(x[b], y[b], t[0], t[1], b, -1)) continue;
                float nota = corte * 3 - da * 0.5F - dt * 0.7F;
                if (nota > mejor) {
                    mejor = nota;
                    ang = (float) Math.atan2(ay, ax);
                    fu = Math.min(1, 0.25F + (da + dt) * 0.18F / Math.max(0.4F, corte));
                }
            }
        }
        if (mejor == -1e9F) {
            // sin tiro claro: tocar la bola propia más cercana
            float dmin = 1e9F;
            for (int b = 1; b < 16; b++) {
                if (!dentro[b] || !suya(turno, b)) continue;
                float d = (x[b] - x[0]) * (x[b] - x[0]) + (y[b] - y[0]) * (y[b] - y[0]);
                if (d < dmin) {
                    dmin = d;
                    ang = (float) Math.atan2(y[b] - y[0], x[b] - x[0]);
                }
            }
            fu = 0.55F;
        }
        ang += (float) rnd.nextGaussian() * (1 - puntería) * 0.05F;
        return new float[]{ang, fu};
    }

    boolean libre(float ax, float ay, float bx, float by, int salvo1, int salvo2) {
        float dx = bx - ax, dy = by - ay, l2 = dx * dx + dy * dy;
        if (l2 < 1e-8F) return true;
        for (int i = 0; i < 16; i++) {
            if (!dentro[i] || i == salvo1 || i == salvo2) continue;
            float t = Math.max(0, Math.min(1, ((x[i] - ax) * dx + (y[i] - ay) * dy) / l2));
            float px = ax + dx * t - x[i], py = ay + dy * t - y[i];
            if (px * px + py * py < 4 * R * R * 0.98F) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ red
    public float[] posiciones() {
        float[] d = new float[49];
        for (int i = 0; i < 16; i++) {
            d[i * 3] = x[i];
            d[i * 3 + 1] = y[i];
            d[i * 3 + 2] = dentro[i] ? 1 : 0;
        }
        d[48] = fase;
        return d;
    }

    public void ponerPosiciones(float[] d) {
        if (d.length < 48) return;
        for (int i = 0; i < 16; i++) {
            x[i] = d[i * 3];
            y[i] = d[i * 3 + 1];
            dentro[i] = d[i * 3 + 2] > 0.5F;
        }
    }
}

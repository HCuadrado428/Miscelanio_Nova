package com.miscelanea.client.arcade;

import com.miscelanea.client.consola.Botones;
import com.miscelanea.registry.ModSounds;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.sounds.SoundEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Pinball «Galaxia»: mesa con flippers, tres setas, dos tirachinas, dianas abatibles, carriles de arriba (M-I-S) que suben
 * el multiplicador, un agujero que retiene la bola y el lanzador. Izquierda/derecha (o A/B): flippers · abajo o C: lanzar.
 */
public class Pinball extends JuegoArcade {
    static final int OX = 4, OY = 1, W = 152, H = 190;
    static final float R = 3.5F, G = 0.085F;

    /** Segmento de pared: extremos, rebote y si es un tirachinas (empuja). */
    record Pared(float x0, float y0, float x1, float y1, float rebote, boolean tirachinas, int color) {}

    record Seta(float x, float y, float r) {}

    final List<Pared> paredes = new ArrayList<>();
    final Seta[] setas = {new Seta(52, 64, 8), new Seta(92, 64, 8), new Seta(72, 90, 8)};
    final int[] setaLuz = new int[3];
    final boolean[] dianas = {true, true, true};
    final boolean[] carriles = new boolean[3];
    final int[] carrilLuz = new int[3];
    float bx, by, bvx, bvy;
    float angI, angD, wI, wD;     // flippers: ángulo y velocidad angular
    static final float PIV_IX = 44, PIV_IY = 172, PIV_DX = 96, PIV_DY = 172, LARGO = 21;
    static final float REPOSO = 0.5F, ARRIBA = -0.45F;
    int agujeroCd, quietoT, bolas, mult = 1, carga, enAgujero, salvarT, extraDada, avisoT, tirachinasLuz, enCarril;
    boolean enJuego, lanzada;
    String aviso = "";
    Sprite fondo;

    public Pinball(Botones[] b, long semilla, int jugadores) {
        super(b, semilla, 1);
    }

    @Override
    public SoundEvent musica() {
        return ModSounds.PB_MUSICA.get();
    }

    @Override
    public void reiniciar() {
        fondo = Sprite.uno("pinball/mesa");
        paredes.clear();
        int borde = 63;
        // laterales y arco de arriba
        paredes.add(new Pared(4, 190, 4, 40, 0.45F, false, borde));
        int n = 14;
        float px = 4, py = 40;
        for (int k = 1; k <= n; k++) {
            double a = Math.PI - Math.PI * k / n;
            float x = (float) (76 + 72 * Math.cos(a)), y = (float) (40 - 36 * Math.sin(a));
            paredes.add(new Pared(px, py, x, y, 0.45F, false, borde));
            px = x;
            py = y;
        }
        paredes.add(new Pared(148, 40, 148, 190, 0.45F, false, borde));
        // pasillo del lanzador
        paredes.add(new Pared(136, 190, 136, 74, 0.45F, false, 45));
        paredes.add(new Pared(136, 186, 148, 186, 0.2F, false, 45));
        // guías hacia los flippers
        paredes.add(new Pared(4, 136, 40, 168, 0.4F, false, 45));
        paredes.add(new Pared(136, 136, 100, 168, 0.4F, false, 45));
        // tirachinas
        paredes.add(new Pared(4, 110, 16, 120, 0.45F, false, 45));
        paredes.add(new Pared(136, 110, 124, 120, 0.45F, false, 45));
        paredes.add(new Pared(16, 120, 32, 152, 1.3F, true, 19));
        paredes.add(new Pared(16, 120, 16, 142, 0.4F, false, 45));
        paredes.add(new Pared(16, 142, 32, 152, 0.4F, false, 45));
        paredes.add(new Pared(124, 120, 108, 152, 1.3F, true, 19));
        paredes.add(new Pared(124, 120, 124, 142, 0.4F, false, 45));
        paredes.add(new Pared(124, 142, 108, 152, 0.4F, false, 45));
        // separadores de los carriles de arriba
        for (float x : new float[]{60, 84}) paredes.add(new Pared(x, 18, x, 32, 0.4F, false, 39));
        // recodo del agujero de la derecha
        paredes.add(new Pared(128, 74, 116, 70, 0.5F, false, 45));
        bolas = 3;
        puntos = 0;
        mult = 1;
        extraDada = 0;
        fin = false;
        java.util.Arrays.fill(dianas, true);
        java.util.Arrays.fill(carriles, false);
        nuevaBola();
    }

    void nuevaBola() {
        bx = 142;
        by = 180;
        bvx = bvy = 0;
        enJuego = true;
        lanzada = false;
        enCarril = 1;
        carga = 0;
        salvarT = 0;
        enAgujero = 0;
    }

    void avisar(String s, int t) {
        aviso = s;
        avisoT = t;
    }

    void sumar(int p) {
        puntos += p * mult;
        if (extraDada == 0 && puntos >= 50000) {
            extraDada = 1;
            bolas++;
            avisar(I18n.get("pinball.miscelanea.extra"), 120);
            sonido(ModSounds.PB_EXTRA, 1F);
        }
    }

    // ------------------------------------------------------------------ paso
    @Override
    public void paso() {
        Botones bb = b[0];
        if (avisoT > 0) avisoT--;
        if (tirachinasLuz > 0) tirachinasLuz--;
        for (int i = 0; i < 3; i++) {
            if (setaLuz[i] > 0) setaLuz[i]--;
            if (carrilLuz[i] > 0) carrilLuz[i]--;
        }
        if (!enJuego) return;
        boolean izq = bb.mantiene(Botones.IZQ) || bb.mantiene(Botones.A), der = bb.mantiene(Botones.DER) || bb.mantiene(Botones.B);
        if (izq && angI > ARRIBA + 0.05F && wI >= 0) sonido(ModSounds.PB_FLIPPER, 1F);
        if (der && angD > ARRIBA + 0.05F && wD >= 0) sonido(ModSounds.PB_FLIPPER, 1.1F);
        // lanzador
        boolean cargando = bb.mantiene(Botones.ABAJO) || bb.mantiene(Botones.C);
        if (enCarril == 1 && cargando) carga = Math.min(60, carga + 1);
        else if (enCarril == 1 && carga > 0) {
            if (bx > 136 && by > 160) {
                bvy = -(3.5F + carga / 60F * 4.2F);
                sonido(ModSounds.PB_LANZAR, 1F);
                lanzada = true;
                salvarT = 420;
            }
            carga = 0;
        }
        if (salvarT > 0) salvarT--;
        if (agujeroCd > 0) agujeroCd--;
        // agujero que retiene la bola
        if (enAgujero > 0) {
            if (--enAgujero == 0) {
                bx = 118;
                agujeroCd = 240;
                bvx = -2.0F;
                bvy = -4.2F;
                sonido(ModSounds.PB_LANZAR, 1.4F);
            }
            moverFlippers(izq, der);
            return;
        }
        for (int s = 0; s < 4; s++) {
            moverFlippers(izq, der, 0.25F);
            fisica(0.25F);
        }
        // si la bola se queda parada en algún rincón, la mesa da un meneo
        if (enCarril == 0 && bvx * bvx + bvy * bvy < 0.0025F) {
            if (++quietoT > 150) {
                bvy = -2.5F;
                bvx = bx < 76 ? 1.2F : -1.2F;
                quietoT = 0;
                sonido(ModSounds.PB_TIRACHINAS, 0.7F);
            }
        } else quietoT = 0;
        if (bx < 136) enCarril = 0;
        if (bx > 136 && by > 170 && Math.abs(bvy) < 0.3F) {
            enCarril = 1;
            lanzada = false;
        }
        if (by > H + 6) perderBola();
    }

    void moverFlippers(boolean izq, boolean der) {
        moverFlippers(izq, der, 1);
    }

    void moverFlippers(boolean izq, boolean der, float dt) {
        float objI = izq ? ARRIBA : REPOSO, objD = der ? ARRIBA : REPOSO;
        wI = flipper(angI, objI, dt);
        angI += wI;
        wD = flipper(angD, objD, dt);
        angD += wD;
    }

    float flipper(float ang, float obj, float dt) {
        float vel = (obj < ang ? 0.42F : 0.22F) * dt;
        float d = obj - ang;
        if (Math.abs(d) <= vel) return d;
        return Math.signum(d) * vel;
    }

    void perderBola() {
        if (salvarT > 0) {
            avisar(I18n.get("pinball.miscelanea.salvada"), 90);
            sonido(ModSounds.PB_EXTRA, 1.3F);
            nuevaBola();
            return;
        }
        bolas--;
        sonido(ModSounds.PB_PERDER, 1F);
        // bonus por multiplicador
        if (bolas <= 0) {
            enJuego = false;
            fin = true;
            mensajeFin = I18n.get("pinball.miscelanea.fin");
            return;
        }
        mult = 1;
        avisar(I18n.get("pinball.miscelanea.bola", 4 - bolas), 90);
        nuevaBola();
    }

    // ------------------------------------------------------------------ física
    void fisica(float dt) {
        bvy += G * dt;
        float v = (float) Math.sqrt(bvx * bvx + bvy * bvy);
        if (v > 7.5F) {
            bvx *= 7.5F / v;
            bvy *= 7.5F / v;
        }
        bx += bvx * dt;
        by += bvy * dt;
        for (Pared p : paredes) choquePared(p);
        // compuerta de un solo sentido arriba del pasillo del lanzador: la bola que baja no puede volver a entrar
        if (bx > 133 && by > 58 && by < 76 && bvy > 0 && enCarril == 0) {
            bx = 132;
            bvx = -Math.abs(bvx) - 0.6F;
        }
        // setas
        for (int i = 0; i < 3; i++) {
            Seta s = setas[i];
            float dx = bx - s.x(), dy = by - s.y(), d = (float) Math.sqrt(dx * dx + dy * dy), min = s.r() + R;
            if (d < min && d > 0.01F) {
                float nx = dx / d, ny = dy / d;
                bx = s.x() + nx * min;
                by = s.y() + ny * min;
                float vn = bvx * nx + bvy * ny;
                bvx += (4.2F - vn) * nx;
                bvy += (4.2F - vn) * ny;
                setaLuz[i] = 10;
                sumar(100);
                sonido(ModSounds.PB_SETA, 0.9F + i * 0.1F);
            }
        }
        // dianas abatibles (pared izquierda)
        for (int i = 0; i < 3; i++) {
            if (!dianas[i]) continue;
            float ty = 70 + i * 13;
            if (bx - R < 12 && by > ty - 5 && by < ty + 5) {
                dianas[i] = false;
                bvx = Math.abs(bvx) * 0.6F + 0.5F;
                bx = 12 + R;
                sumar(500);
                sonido(ModSounds.PB_DIANA, 1F);
                if (!dianas[0] && !dianas[1] && !dianas[2]) {
                    sumar(5000);
                    avisar(I18n.get("pinball.miscelanea.dianas"), 90);
                    java.util.Arrays.fill(dianas, true);
                    sonido(ModSounds.PB_EXTRA, 1F);
                }
            }
        }
        // carriles de arriba
        for (int i = 0; i < 3; i++) {
            float cx = 48 + i * 24;
            if (Math.abs(bx - cx) < 8 && by > 18 && by < 30 && bvy > 0 && !carriles[i]) {
                carriles[i] = true;
                carrilLuz[i] = 20;
                sumar(300);
                sonido(ModSounds.PB_DIANA, 1.4F);
                if (carriles[0] && carriles[1] && carriles[2]) {
                    mult = Math.min(5, mult + 1);
                    java.util.Arrays.fill(carriles, false);
                    avisar(I18n.get("pinball.miscelanea.multi", mult), 90);
                    sonido(ModSounds.PB_EXTRA, 1.2F);
                }
            }
        }
        // agujero
        float ax = bx - 124, ay = by - 82;
        if (ax * ax + ay * ay < 16 && enAgujero == 0 && agujeroCd == 0) {
            bx = 124;
            by = 82;
            bvx = bvy = 0;
            enAgujero = 45;
            sumar(2000);
            avisar(I18n.get("pinball.miscelanea.agujero"), 60);
            sonido(ModSounds.PB_AGUJERO, 1F);
        }
        flipperChoque(PIV_IX, PIV_IY, angI, wI, 1);
        flipperChoque(PIV_DX, PIV_DY, angD, wD, -1);
    }

    void choquePared(Pared p) {
        float dx = p.x1() - p.x0(), dy = p.y1() - p.y0(), l2 = dx * dx + dy * dy;
        float t = Math.max(0, Math.min(1, ((bx - p.x0()) * dx + (by - p.y0()) * dy) / l2));
        float cx = p.x0() + dx * t, cy = p.y0() + dy * t;
        float ex = bx - cx, ey = by - cy, d2 = ex * ex + ey * ey;
        if (d2 >= R * R || d2 < 1e-6F) return;
        float d = (float) Math.sqrt(d2), nx = ex / d, ny = ey / d;
        bx = cx + nx * R;
        by = cy + ny * R;
        float vn = bvx * nx + bvy * ny;
        if (vn < 0) {
            bvx -= (1 + p.rebote()) * vn * nx;
            bvy -= (1 + p.rebote()) * vn * ny;
            if (p.tirachinas() && vn < -0.5F) {
                bvx += nx * 2.2F;
                bvy += ny * 2.2F;
                tirachinasLuz = 8;
                sumar(50);
                sonido(ModSounds.PB_TIRACHINAS, 1F);
            }
        }
    }

    /** Choque con un flipper (cápsula del eje a la punta); lado 1 = izquierdo (apunta a la derecha), -1 = derecho. */
    void flipperChoque(float pxv, float pyv, float ang, float w, int lado) {
        float dirx = (float) Math.cos(ang) * lado, diry = (float) Math.sin(ang);
        float tx = pxv + dirx * LARGO, ty = pyv + diry * LARGO;
        float dx = tx - pxv, dy = ty - pyv, l2 = dx * dx + dy * dy;
        float t = Math.max(0, Math.min(1, ((bx - pxv) * dx + (by - pyv) * dy) / l2));
        float cx = pxv + dx * t, cy = pyv + dy * t;
        float radio = 3.2F - t * 1.0F;
        float ex = bx - cx, ey = by - cy, d2 = ex * ex + ey * ey, min = R + radio;
        if (d2 >= min * min || d2 < 1e-6F) return;
        float d = (float) Math.sqrt(d2), nx = ex / d, ny = ey / d;
        bx = cx + nx * min;
        by = cy + ny * min;
        // velocidad de la superficie del flipper en el punto de contacto (giro alrededor del eje)
        float rx = cx - pxv, ry = cy - pyv;
        float wz = w * 4;     // w es por cuarto de paso
        float svx = -wz * ry * lado, svy = wz * rx * lado;
        float rvx = bvx - svx, rvy = bvy - svy;
        float vn = rvx * nx + rvy * ny;
        if (vn < 0) {
            bvx -= 1.25F * vn * nx;
            bvy -= 1.25F * vn * ny;
        }
    }

    // ------------------------------------------------------------------ dibujo
    @Override
    public void dibujar(Pantalla p) {
        p.limpiar(0);
        p.spr(fondo, OX, OY, false);
        for (Pared w : paredes) {
            int c = w.tirachinas() && tirachinasLuz > 0 ? 7 : w.color();
            lineaGruesa(p, w.x0(), w.y0(), w.x1(), w.y1(), c);
        }
        for (int i = 0; i < 3; i++) {
            Seta s = setas[i];
            int x = OX + Math.round(s.x()), y = OY + Math.round(s.y());
            p.circulo(x, y, (int) s.r(), setaLuz[i] > 0 ? 7 : 11);
            p.circulo(x, y, (int) s.r() - 2, setaLuz[i] > 0 ? 20 : 12);
            p.circulo(x, y, 2, 7);
        }
        for (int i = 0; i < 3; i++) {
            int ty = OY + 70 + i * 13;
            if (dianas[i]) {
                p.rect(OX + 6, ty - 5, 6, 10, 19);
                p.rect(OX + 6, ty - 5, 6, 2, 20);
            } else p.rect(OX + 6, ty - 1, 6, 2, 2);
        }
        String letras = "MIS";
        for (int i = 0; i < 3; i++) {
            int cx = OX + 48 + i * 24;
            boolean l = carriles[i] || (carrilLuz[i] / 3) % 2 == 1;
            p.circulo(cx, OY + 38, 4, l ? 63 : 2);
            p.texto(letras.substring(i, i + 1), cx - 2, OY + 34, l ? 0 : 5);
        }
        // agujero
        p.circulo(OX + 124, OY + 82, 5, 1);
        p.circulo(OX + 124, OY + 82, 3, 0);
        // flippers
        dibujarFlipper(p, PIV_IX, PIV_IY, angI, 1);
        dibujarFlipper(p, PIV_DX, PIV_DY, angD, -1);
        // lanzador
        int pl = (int) (carga / 60F * 10);
        p.rect(OX + 139, OY + 186 - 10 + pl, 6, 4 + 10 - pl, 5);
        // bola
        if (enJuego) {
            int x = OX + Math.round(bx), y = OY + Math.round(by);
            p.circulo(x, y, 3, 5);
            p.circulo(x, y, 2, 6);
            p.px(x - 1, y - 1, 7);
        }
        hud(p);
    }

    void lineaGruesa(Pantalla p, float x0, float y0, float x1, float y1, int c) {
        int a = OX + Math.round(x0), b2 = OY + Math.round(y0), cx = OX + Math.round(x1), cy = OY + Math.round(y1);
        p.linea(a, b2, cx, cy, c);
        p.linea(a + 1, b2, cx + 1, cy, c);
    }

    void dibujarFlipper(Pantalla p, float pxv, float pyv, float ang, int lado) {
        float dirx = (float) Math.cos(ang) * lado, diry = (float) Math.sin(ang);
        for (int k = 0; k <= 22; k++) {
            float t = k / 22F;
            int r = Math.round(3.2F - t * 1.2F);
            p.circulo(OX + Math.round(pxv + dirx * LARGO * t), OY + Math.round(pyv + diry * LARGO * t), Math.max(1, r), 7);
        }
        for (int k = 0; k <= 22; k++) {
            float t = k / 22F;
            p.circulo(OX + Math.round(pxv + dirx * LARGO * t), OY + Math.round(pyv + diry * LARGO * t), Math.max(0, Math.round(2.0F - t * 1.2F)), 36);
        }
        p.circulo(OX + Math.round(pxv), OY + Math.round(pyv), 1, 0);
    }

    void hud(Pantalla p) {
        int x = 162;
        p.rect(158, 0, 98, 192, 1);
        p.rect(158, 0, 2, 192, 45);
        p.textoC("GALAXIA", 207, 6, 20);
        p.texto(I18n.get("pinball.miscelanea.puntos"), x, 26, 6);
        p.textoD(Integer.toString(puntos), 252, 38, 7);
        p.texto(I18n.get("pinball.miscelanea.bolas"), x, 58, 6);
        for (int i = 0; i < Math.min(6, bolas); i++) p.circulo(x + 4 + i * 10, 74, 3, 6);
        p.texto(I18n.get("pinball.miscelanea.multiplica"), x, 88, 6);
        p.textoD("x" + mult, 252, 100, mult > 1 ? 15 : 7);
        if (enCarril == 1 && !lanzada) {
            p.texto(I18n.get("pinball.miscelanea.lanzar"), x, 124, (avisoT / 10) % 2 == 0 ? 19 : 5);
            p.rect(x, 140, 86, 6, 2);
            p.rect(x, 140, (int) (86 * carga / 60F), 6, carga > 45 ? 11 : 19);
        }
        if (salvarT > 0) p.texto(I18n.get("pinball.miscelanea.salvar"), x, 156, (salvarT / 15) % 2 == 0 ? 29 : 27);
        if (avisoT > 0) {
            p.rect(160, 168, 96, 16, 0);
            p.textoC(aviso, 208, 172, 20);
        }
    }
}

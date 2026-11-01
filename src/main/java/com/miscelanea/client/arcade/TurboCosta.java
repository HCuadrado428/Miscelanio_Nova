package com.miscelanea.client.arcade;

import com.miscelanea.client.consola.Botones;
import com.miscelanea.registry.ModSounds;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.sounds.SoundEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Turbo Costa: carreras pseudo-3D por la costa (carretera por segmentos proyectados, curvas, colinas, tráfico y decorado
 * escalado). Contra el reloj: cada punto de control da tiempo extra; hay que llegar a la meta tras cinco tramos.
 * A o arriba: acelerar · B o abajo: frenar · izq/der: girar · C: turbo (3 por carrera).
 */
public class TurboCosta extends JuegoArcade {
    static final int SEG = 200, ANCHO = 2000, DIBUJO = 160, BANDAS = 3;
    static final float PROF = 0.84F, ALTURA_CAM = 1000, VMAX = SEG * 60F / 60F * 1.5F;   // VMAX en unidades por paso
    static final int TRAMOS = 5, PZ = 820;

    static final class Segmento {
        float curva, y0, y1;
        int ix;
        final List<float[]> cosas = new ArrayList<>();     // {tipo, desplazamiento x (en anchos de carretera)}
        boolean control, meta;
        // proyección (recalculada al dibujar)
        float sx0, sy0, sw0, sx1, sy1, sw1, esc1, clip;
    }

    static final class Coche {
        float z, x, v;
        int tipo;
    }

    final List<Segmento> segs = new ArrayList<>();
    final List<Coche> trafico = new ArrayList<>();
    float pos, x, vel, tiempo, fondoX, giroVis, saltoY;
    int choqueT;
    int turbos = 3, turboT, tramo, cuentaT, avisoT, derrapeCad, controlT;
    long distancia;
    boolean empezado, metaCruzada;
    String aviso = "";
    Sprite[] coche, traficoS, sombrillas;
    Sprite palmera, cartel, roca, faro, caseta, nubes, isla;

    public TurboCosta(Botones[] b, long semilla, int jugadores) {
        super(b, semilla, 1);
    }

    @Override
    public SoundEvent musica() {
        return ModSounds.TC_MUSICA.get();
    }

    @Override
    public void reiniciar() {
        coche = Sprite.hoja("turbo/coche", 64, 64);
        traficoS = Sprite.hoja("turbo/trafico", 64, 64);
        sombrillas = Sprite.hoja("turbo/sombrilla", 48, 48);
        palmera = Sprite.uno("turbo/palmera");
        cartel = Sprite.uno("turbo/cartel");
        roca = Sprite.uno("turbo/roca");
        faro = Sprite.uno("turbo/faro");
        caseta = Sprite.uno("turbo/caseta");
        nubes = Sprite.uno("turbo/nubes");
        isla = Sprite.uno("turbo/isla");
        construir();
        pos = 0;
        x = 0;
        vel = 0;
        tiempo = 35 * 60;
        tramo = 0;
        turbos = 3;
        cuentaT = 180;
        puntos = 0;
        fin = false;
        metaCruzada = false;
    }

    // ------------------------------------------------------------------ circuito
    void tramoCarretera(int entrar, int mantener, int salir, float curva, float alto) {
        float y0 = segs.isEmpty() ? 0 : segs.get(segs.size() - 1).y1;
        int total = entrar + mantener + salir;
        for (int n = 0; n < total; n++) {
            Segmento s = new Segmento();
            s.ix = segs.size();
            float c;
            if (n < entrar) c = curva * suave(n / (float) entrar);
            else if (n < entrar + mantener) c = curva;
            else c = curva * (1 - suave((n - entrar - mantener) / (float) salir));
            s.curva = c;
            s.y0 = segs.isEmpty() ? 0 : segs.get(segs.size() - 1).y1;
            s.y1 = y0 + alto * (float) ((1 - StrictMath.cos((n + 1) / (double) total * Math.PI)) / 2);
            segs.add(s);
        }
    }

    static float suave(float t) {
        return t * t * (3 - 2 * t);
    }

    void construir() {
        segs.clear();
        trafico.clear();
        for (int tr = 0; tr < TRAMOS; tr++) {
            tramoCarretera(10, 30, 10, 0, 0);
            int base = segs.size();
            for (int k = 0; k < 14; k++) {
                float curva = (rnd.nextBoolean() ? 1 : -1) * Math.min(6, 2 + 2 * rnd.nextInt(2 + tr / 2)) * (0.8F + rnd.nextFloat() * 0.4F);
                float alto = rnd.nextInt(5) < 2 ? 0 : (rnd.nextFloat() * 2 - 1) * (2000 + tr * 800);
                tramoCarretera(20 + rnd.nextInt(30), 20 + rnd.nextInt(50), 20 + rnd.nextInt(30), rnd.nextInt(5) == 0 ? 0 : curva, alto);
            }
            // decorado del tramo: playa, faro, pueblo...
            for (int i = base; i < segs.size(); i++) {
                Segmento s = segs.get(i);
                if (i % 8 == 0) s.cosas.add(new float[]{0, -1.25F - rnd.nextFloat() * 0.8F});
                if (i % 11 == 0) s.cosas.add(new float[]{0, 1.25F + rnd.nextFloat() * 0.8F});
                if (i % 17 == 3) s.cosas.add(new float[]{rnd.nextInt(2) == 0 ? 1 : 2, (rnd.nextInt(2) == 0 ? -1 : 1) * (1.4F + rnd.nextFloat())});
                if (i % 60 == 20) s.cosas.add(new float[]{3, rnd.nextInt(2) == 0 ? -1.4F : 1.4F});
                if (i % 23 == 9) s.cosas.add(new float[]{4, (rnd.nextInt(2) == 0 ? -1 : 1) * (1.3F + rnd.nextFloat() * 0.5F)});
                if (i % 140 == 70) s.cosas.add(new float[]{5, rnd.nextInt(2) == 0 ? -2.4F : 2.4F});
                if (i % 37 == 15) s.cosas.add(new float[]{6, (rnd.nextInt(2) == 0 ? -1 : 1) * (2F + rnd.nextFloat())});
            }
            segs.get(segs.size() - 1).control = true;
        }
        tramoCarretera(20, 60, 10, 0, 0);
        segs.get(segs.size() - 60).meta = true;
        for (int k = 0; k < 40; k++) {
            Coche c = new Coche();
            c.z = (200 + rnd.nextInt(segs.size() - 300)) * SEG;
            c.x = (rnd.nextInt(3) - 1) * 0.6F;
            c.v = VMAX * (0.3F + rnd.nextFloat() * 0.3F);
            c.tipo = rnd.nextInt(4);
            trafico.add(c);
        }
    }

    int largo() {
        return segs.size() * SEG;
    }

    Segmento segEn(float z) {
        return segs.get(Math.floorMod((int) (z / SEG), segs.size()));
    }

    void avisar(String s, int d) {
        aviso = s;
        avisoT = d;
    }

    // ------------------------------------------------------------------ lógica
    @Override
    public void paso() {
        if (avisoT > 0) avisoT--;
        if (derrapeCad > 0) derrapeCad--;
        if (controlT > 0) controlT--;
        if (cuentaT > 0) {
            if (cuentaT % 60 == 0) sonido(ModSounds.TC_CUENTA, cuentaT == 60 ? 1.0F : 0.75F);
            cuentaT--;
            if (cuentaT == 0) sonido(ModSounds.TC_CUENTA, 1.5F);
            return;
        }
        Botones bb = b[0];
        boolean acel = bb.mantiene(Botones.A) || bb.mantiene(Botones.ARRIBA), freno = bb.mantiene(Botones.B) || bb.mantiene(Botones.ABAJO);
        float dx = 0.032F * (vel / VMAX);
        Segmento s = segEn(pos + PZ);
        if (choqueT > 0) {
            choqueT--;
            vel *= 0.93F;
        } else {
            if (bb.mantiene(Botones.IZQ)) x -= dx;
            if (bb.mantiene(Botones.DER)) x += dx;
            giroVis = bb.mantiene(Botones.IZQ) ? -1 : bb.mantiene(Botones.DER) ? 1 : 0;
            float vmax = turboT > 0 ? VMAX * 1.3F : VMAX;
            if (acel) vel += VMAX / 220F;
            else if (freno) vel -= VMAX / 70F;
            else vel -= VMAX / 400F;
            if (Math.abs(x) > 1 && vel > VMAX / 3.5F) vel -= VMAX / 70F;      // fuera de la carretera
            vel = Math.max(0, Math.min(vmax, vel));
        }
        if (bb.pulsa(Botones.C) && turbos > 0 && turboT == 0 && cuentaT == 0) {
            turbos--;
            turboT = 150;
            sonido(ModSounds.TC_TURBO, 1F);
        }
        if (turboT > 0) {
            turboT--;
            vel = Math.min(VMAX * 1.3F, vel + VMAX / 120F);
        }
        // fuerza centrífuga en las curvas
        x -= dx * (vel / VMAX) * s.curva * 0.33F;
        if (Math.abs(s.curva) > 3 && vel > VMAX * 0.7F && derrapeCad == 0 && Math.abs(giroVis) > 0) {
            sonido(ModSounds.TC_DERRAPE, 1F);
            derrapeCad = 40;
        }
        x = Math.max(-2.5F, Math.min(2.5F, x));
        // choques con el decorado
        Segmento sj = segEn(pos + PZ);
        for (float[] c : sj.cosas) {
            float ancho = c[0] == 0 ? 0.35F : c[0] == 5 ? 0.5F : 0.3F;
            if (Math.abs(x - c[1]) < ancho && choqueT == 0 && vel > VMAX / 10F) {
                choqueT = 60;
                vel *= 0.3F;
                x = x * 0.8F;
                sonido(ModSounds.TC_CHOQUE, 1F);
                break;
            }
        }
        // tráfico
        for (Coche c : trafico) {
            c.z = (c.z + c.v) % largo();
            float rel = Math.floorMod((int) (c.z - pos - PZ), largo());
            if (rel < SEG * 0.8F && Math.abs(c.x - x) < 0.45F && vel > c.v && choqueT == 0) {
                vel = c.v * 0.6F;
                choqueT = 20;
                sonido(ModSounds.TC_CHOQUE, 1.3F);
            }
        }
        float antes = pos;
        pos += vel;
        distancia += (long) vel;
        puntos = (int) (distancia / 100);
        // puntos de control y meta
        int ia = (int) ((antes + PZ) / SEG), ib = (int) ((pos + PZ) / SEG);
        for (int i = ia + 1; i <= ib && i < segs.size(); i++) {
            Segmento c = segs.get(i);
            if (c.control) {
                tramo++;
                tiempo += 20 * 60;
                controlT = 120;
                avisar(I18n.get("turbo.miscelanea.control", tramo, TRAMOS), 120);
                sonido(ModSounds.TC_PUNTO, 1F);
            }
            if (c.meta && !metaCruzada) {
                metaCruzada = true;
                puntos += (int) (tiempo / 60) * 1000;
                sonido(ModSounds.TC_META, 1F);
                fin = true;
                mensajeFin = I18n.get("turbo.miscelanea.meta");
            }
        }
        if (pos >= largo() - SEG * 5) pos = largo() - SEG * 5;
        tiempo--;
        if (tiempo <= 0) {
            tiempo = 0;
            vel *= 0.97F;
            if (vel < 1) {
                fin = true;
                mensajeFin = I18n.get("turbo.miscelanea.tiempo");
            }
        }
        fondoX += s.curva * vel / VMAX * 0.6F;
        saltoY = (vel > 10 && (int) (pos / 40) % 2 == 0) ? 1 : 0;
    }

    // ------------------------------------------------------------------ dibujo
    @Override
    public void dibujar(Pantalla p) {
        int W = Pantalla.W, H = Pantalla.H;
        // cielo, nubes, mar e isla con paralaje según las curvas
        int[] cielo = {39, 39, 40, 40, 62};
        for (int y = 0; y < 96; y++) {
            int banda = Math.min(4, y / 20), f = y % 20;
            p.rect(0, y, W, 1, cielo[banda]);
            if (f >= 14 && banda < 4) for (int xx = y % 2; xx < W; xx += 2) p.px(xx, y, cielo[banda + 1]);
        }
        int nx = Math.floorMod((int) (-fondoX * 0.5F), 256);
        p.spr(nubes, nx - 256, 12, false);
        p.spr(nubes, nx, 12, false);
        int ix = Math.floorMod((int) (-fondoX * 1.2F), 256);
        p.spr(isla, ix - 256, 52, false);
        p.spr(isla, ix, 52, false);
        p.rect(0, 88, W, 40, 37);
        for (int y = 89; y < 128; y += 3) for (int xx = (y * 7 + (int) fondoX) & 15; xx < W; xx += 16) p.rect(xx, y, 5, 1, 39);
        // carretera
        Segmento base = segEn(pos);
        float baseFr = (pos % SEG) / SEG;
        float camY = ALTURA_CAM + interp(base.y0, base.y1, baseFr);
        float camX = x * ANCHO;
        float dx = -(base.curva * baseFr), xAcum = 0;
        float maxy = H;
        int bi = base.ix;
        for (int n = 0; n < DIBUJO; n++) {
            Segmento s = segs.get((bi + n) % segs.size());
            float z0 = n * SEG - pos % SEG, z1 = z0 + SEG;
            if (z0 < 1) z0 = 1;
            float e0 = PROF / z0, e1 = PROF / z1;
            float x0c = camX - xAcum, x1c = camX - xAcum - dx;
            s.sx0 = W / 2F + e0 * (-x0c) * W / 2F;
            s.sy0 = H / 2F - e0 * (s.y0 - camY) * H / 2F;
            s.sw0 = e0 * ANCHO * W / 2F;
            s.sx1 = W / 2F + e1 * (-x1c) * W / 2F;
            s.sy1 = H / 2F - e1 * (s.y1 - camY) * H / 2F;
            s.sw1 = e1 * ANCHO * W / 2F;
            s.esc1 = e1;
            xAcum += dx;
            dx += s.curva;
            s.clip = maxy;
            if (s.sy1 >= maxy || s.sy0 <= s.sy1) continue;
            boolean claro = (s.ix / BANDAS) % 2 == 0;
            // hierba / arena
            int hierba = claro ? 58 : 57;
            franja(p, 0, s.sy1, W, s.sy0, hierba, maxy);
            // arcén, asfalto y líneas
            trapecio(p, s.sx0, s.sy0, s.sw0 * 1.12F, s.sx1, s.sy1, s.sw1 * 1.12F, claro ? 7 : 11, maxy);
            trapecio(p, s.sx0, s.sy0, s.sw0, s.sx1, s.sy1, s.sw1, claro ? 3 : 2, maxy);
            if (claro) for (int l = -1; l <= 1; l += 2) {
                float lx0 = s.sx0 + l * s.sw0 / 3F, lx1 = s.sx1 + l * s.sw1 / 3F;
                trapecio(p, lx0, s.sy0, s.sw0 * 0.03F, lx1, s.sy1, s.sw1 * 0.03F, 7, maxy);
            }
            if (s.control || s.meta) {
                // pancarta del punto de control / meta
                for (int k = 0; k < 2; k++) {
                    float bx = s.sx1 + (k == 0 ? -1.2F : 1.2F) * s.sw1;
                    int alto = (int) (s.esc1 * 3000 * H / 2F);
                    p.rect((int) bx - 1, (int) s.sy1 - alto, Math.max(2, (int) (s.esc1 * 60 * W)), alto, 5);
                }
                int alto = (int) (s.esc1 * 3000 * H / 2F), grosor = Math.max(2, (int) (s.esc1 * 700 * H / 2F));
                int bx0 = (int) (s.sx1 - 1.2F * s.sw1), bx1 = (int) (s.sx1 + 1.2F * s.sw1);
                for (int yy = 0; yy < grosor; yy++)
                    for (int xx = bx0; xx < bx1; xx++) {
                        int cuad = Math.max(1, grosor / 3);
                        int c = s.meta ? (((xx - bx0) / cuad + yy / cuad) % 2 == 0 ? 7 : 0) : (yy < grosor / 2 ? 19 : 11);
                        if ((int) s.sy1 - alto + yy < maxy) p.px(xx, (int) s.sy1 - alto + yy, c);
                    }
            }
            maxy = s.sy1;
        }
        // sprites del decorado y tráfico (de lejos a cerca)
        for (int n = DIBUJO - 1; n > 0; n--) {
            Segmento s = segs.get((bi + n) % segs.size());
            for (float[] c : s.cosas) {
                Sprite sp = switch ((int) c[0]) {
                    case 0 -> palmera;
                    case 1 -> sombrillas[0];
                    case 2 -> sombrillas[1];
                    case 3 -> cartel;
                    case 4 -> roca;
                    case 5 -> faro;
                    default -> caseta;
                };
                float sx = s.sx1 + s.sw1 * c[1];
                spriteEsc(p, sp, sx, s.sy1, s.esc1, c[1] < 0 ? -1 : 0, s.clip);
            }
            for (Coche c : trafico) {
                int ci = (int) (c.z / SEG);
                if (ci != s.ix) continue;
                float sx = s.sx1 + s.sw1 * c.x;
                spriteEsc(p, traficoS[c.tipo], sx, s.sy1, s.esc1, -0.5F, s.clip);
            }
        }
        // coche del jugador
        int cf = choqueT > 0 ? ((choqueT / 4) % 3) : (int) giroVis + 1;
        int cy = H - 66 - (int) saltoY;
        p.spr(coche[Math.max(0, Math.min(2, cf))], W / 2 - 32, cy, false);
        if (turboT > 0 && turboT % 4 < 2) {
            p.rect(W / 2 - 22, cy + 58, 4, 3, 15);
            p.rect(W / 2 + 18, cy + 58, 4, 3, 15);
        }
        if (Math.abs(x) > 1 && vel > 5 && (int) (pos / 60) % 2 == 0) {
            p.rect(W / 2 - 34, cy + 56, 6, 4, 58);
            p.rect(W / 2 + 28, cy + 56, 6, 4, 58);
        }
        hud(p);
    }

    static float interp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    void franja(Pantalla p, float x0, float y0, float x1, float y1, int c, float maxy) {
        int a = Math.max(0, Math.round(y0)), b2 = Math.min(Math.round(maxy), Math.round(y1));
        if (b2 > a) p.rect((int) x0, a, (int) (x1 - x0), b2 - a, c);
    }

    /** Trapecio entre la línea de abajo (x1, y1, ancho w1) y la de arriba (x2, y2, w2). */
    void trapecio(Pantalla p, float x1, float y1, float w1, float x2, float y2, float w2, int c, float maxy) {
        int ya = Math.max(0, Math.round(y2)), yb = Math.min(Math.round(Math.min(maxy, y1)), Pantalla.H);
        float alto = y1 - y2;
        if (alto <= 0) return;
        for (int y = ya; y < yb; y++) {
            float t = Math.max(0, Math.min(1, (y + 0.5F - y2) / alto));
            float cx = x2 + (x1 - x2) * t, w = w2 + (w1 - w2) * t;
            p.rect((int) (cx - w), y, (int) (2 * w) + 1, 1, c);
        }
    }

    /** Sprite escalado con la perspectiva; ox = desplazamiento horizontal en anchos del sprite (-1 a la izquierda, -0.5 centrado). */
    void spriteEsc(Pantalla p, Sprite sp, float sx, float sy, float esc, float ox, float clip) {
        float k = esc * 8.3F * Pantalla.W / 2F;
        int w = (int) (sp.w * k), h = (int) (sp.h * k);
        if (w < 1 || h < 1 || w > 900) return;
        int x0 = (int) (sx + ox * w), y0 = (int) sy - h;
        for (int y = Math.max(0, y0); y < Math.min((int) clip, Math.min(Pantalla.H, y0 + h)); y++) {
            int v = (int) ((y - y0) / k);
            if (v >= sp.h) continue;
            for (int xx = Math.max(0, x0); xx < Math.min(Pantalla.W, x0 + w); xx++) {
                int u = (int) ((xx - x0) / k);
                if (u >= sp.w) continue;
                byte c = sp.px[v * sp.w + u];
                if (c != (byte) 255) p.buf[y * Pantalla.W + xx] = c;
            }
        }
    }

    void hud(Pantalla p) {
        int seg = (int) Math.ceil(tiempo / 60F);
        p.textoC(I18n.get("turbo.miscelanea.tiempo_hud"), 128, 3, 7);
        p.textoC(Integer.toString(seg), 128, 13, seg <= 10 && (int) tiempo / 15 % 2 == 0 ? 11 : 20);
        p.texto(I18n.get("turbo.miscelanea.puntos_hud") + " " + puntos, 4, 3, 7);
        int kmh = (int) (vel / VMAX * 280);
        p.textoD(kmh + " km/h", 252, 3, turboT > 0 ? 15 : 7);
        p.textoD(I18n.get("turbo.miscelanea.tramo", Math.min(TRAMOS, tramo + 1), TRAMOS), 252, 13, 39);
        for (int k = 0; k < turbos; k++) p.rect(4 + k * 8, 15, 6, 4, 15);
        // barra de velocidad
        p.rect(4, 186, 80, 3, 1);
        p.rect(4, 186, (int) (80 * Math.min(1, vel / (VMAX * 1.3F))), 3, turboT > 0 ? 15 : 29);
        if (cuentaT > 0) {
            String c = cuentaT > 120 ? "3" : cuentaT > 60 ? "2" : "1";
            p.rect(108, 70, 40, 22, 0);
            p.textoC(c, 128, 77, 20);
        } else if (cuentaT == 0 && pos < SEG * 20) p.textoC(I18n.get("turbo.miscelanea.ya"), 128, 77, 29);
        if (avisoT > 0 && (avisoT > 40 || avisoT / 6 % 2 == 0)) {
            p.rect(0, 54, Pantalla.W, 16, 0);
            p.textoC(aviso, 128, 58, 20);
        }
    }
}

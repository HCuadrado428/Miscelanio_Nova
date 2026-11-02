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
 * A dos jugadores la pantalla se parte en dos (1P arriba, 2P abajo) y gana el primero en cruzar la meta.
 */
public class TurboCosta extends JuegoArcade {
    static final int SEG = 200, ANCHO = 2000, DIBUJO = 160, BANDAS = 3;
    static final float PROF = 0.84F, ALTURA_CAM = 1000, VMAX = SEG * 60F / 60F * 1.5F;   // VMAX en unidades por paso
    static final int TRAMOS = 5, PZ = 820;
    /** Alto de cada mitad de la pantalla partida. */
    static final int MITAD = Pantalla.H / 2;

    static final class Segmento {
        float curva, y0, y1;
        int ix;
        final List<float[]> cosas = new ArrayList<>();     // {tipo, desplazamiento x (en anchos de carretera)}
        boolean control, meta;
        // proyección (recalculada al dibujar cada vista)
        float sx0, sy0, sw0, sx1, sy1, sw1, esc1, clip;
    }

    static final class Coche {
        float z, x, v;
        int tipo;
    }

    /** Estado de cada jugador: su coche, su reloj y sus avisos. */
    static final class Piloto {
        final int n;
        float pos, x, vel, tiempo, fondoX, giroVis, saltoY;
        int choqueT, turbos = 3, turboT, tramo, avisoT, derrapeCad, controlT, puntos, metaPaso = -1;
        long distancia;
        boolean metaCruzada, terminado;
        String aviso = "";

        Piloto(int n) {
            this.n = n;
        }
    }

    final List<Segmento> segs = new ArrayList<>();
    final List<Coche> trafico = new ArrayList<>();
    Piloto[] pilotos = {};
    int cuentaT, pasoN;
    Sprite[] coche, coche2, traficoS, sombrillas;
    Sprite palmera, cartel, roca, faro, caseta, nubes, isla;
    /** Pantalla auxiliar donde se dibuja cada mitad antes de copiarla. */
    private final Pantalla vista = new Pantalla();

    public TurboCosta(Botones[] b, long semilla, int jugadores) {
        super(b, semilla, Math.max(1, Math.min(2, Math.min(jugadores, b.length))));
    }

    @Override
    public SoundEvent musica() {
        return ModSounds.TC_MUSICA.get();
    }

    @Override
    public void reiniciar() {
        coche = Sprite.hoja("turbo/coche", 64, 64);
        coche2 = recolorear(coche);
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
        pilotos = new Piloto[jugadores];
        for (int i = 0; i < jugadores; i++) {
            Piloto j = new Piloto(i);
            j.tiempo = 35 * 60;
            // a dos, cada uno sale por un carril
            if (jugadores == 2) j.x = i == 0 ? -0.4F : 0.4F;
            pilotos[i] = j;
        }
        cuentaT = 180;
        pasoN = 0;
        puntos = 0;
        fin = false;
    }

    /** El descapotable del jugador 2, en azul (la rampa de rojos pasa a la de azules). */
    static Sprite[] recolorear(Sprite[] s) {
        Sprite[] out = new Sprite[s.length];
        for (int k = 0; k < s.length; k++) {
            byte[] px = s[k].px.clone();
            for (int i = 0; i < px.length; i++) {
                int c = px[i] & 255;
                if (c >= 8 && c <= 12) px[i] = (byte) (33 + c - 8);
            }
            out[k] = new Sprite(s[k].w, s[k].h, px);
        }
        return out;
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

    static void avisar(Piloto j, String s, int d) {
        j.aviso = s;
        j.avisoT = d;
    }

    // ------------------------------------------------------------------ lógica
    @Override
    public void paso() {
        for (Piloto j : pilotos) {
            if (j.avisoT > 0) j.avisoT--;
            if (j.derrapeCad > 0) j.derrapeCad--;
            if (j.controlT > 0) j.controlT--;
        }
        if (cuentaT > 0) {
            if (cuentaT % 60 == 0) sonido(ModSounds.TC_CUENTA, cuentaT == 60 ? 1.0F : 0.75F);
            cuentaT--;
            if (cuentaT == 0) sonido(ModSounds.TC_CUENTA, 1.5F);
            return;
        }
        pasoN++;
        // el tráfico avanza una sola vez por paso, para los dos
        for (Coche c : trafico) c.z = (c.z + c.v) % largo();
        for (Piloto j : pilotos) pasoPiloto(j);
        if (jugadores == 2) {
            Piloto a = pilotos[0], c = pilotos[1];
            // roce entre los dos coches: se separan y el de detrás pierde algo de velocidad
            float dz = a.pos - c.pos;
            if (!a.terminado && !c.terminado && Math.abs(dz) < SEG * 0.6F && Math.abs(a.x - c.x) < 0.4F) {
                float lado = a.x < c.x ? -1 : a.x > c.x ? 1 : (dz >= 0 ? 1 : -1);
                a.x = Math.max(-2.5F, Math.min(2.5F, a.x + lado * 0.03F));
                c.x = Math.max(-2.5F, Math.min(2.5F, c.x - lado * 0.03F));
                Piloto detras = dz >= 0 ? c : a;
                detras.vel *= 0.97F;
            }
        }
        puntos = 0;
        for (Piloto j : pilotos) puntos = Math.max(puntos, j.puntos);
        if (jugadores == 1) {
            Piloto j = pilotos[0];
            if (j.terminado) {
                fin = true;
                mensajeFin = I18n.get(j.metaCruzada ? "turbo.miscelanea.meta" : "turbo.miscelanea.tiempo");
            }
        } else if (pilotos[0].terminado && pilotos[1].terminado) {
            fin = true;
            int g = ganador();
            mensajeFin = g < 0 ? I18n.get("turbo.miscelanea.empate") : I18n.get("turbo.miscelanea.gana", (g + 1) + "P");
        }
    }

    /** Quién gana a dos: el primero en cruzar la meta; si nadie llega, el que más lejos ha ido (-1 = empate). */
    int ganador() {
        Piloto a = pilotos[0], c = pilotos[1];
        if (a.metaCruzada && c.metaCruzada) return a.metaPaso == c.metaPaso ? (a.puntos == c.puntos ? -1 : a.puntos > c.puntos ? 0 : 1)
                : a.metaPaso < c.metaPaso ? 0 : 1;
        if (a.metaCruzada != c.metaCruzada) return a.metaCruzada ? 0 : 1;
        return a.distancia == c.distancia ? -1 : a.distancia > c.distancia ? 0 : 1;
    }

    void pasoPiloto(Piloto j) {
        if (j.terminado) {
            // ya ha acabado (solo a dos): el coche se para solo mientras el otro termina
            j.vel *= 0.97F;
            j.turboT = 0;
            j.giroVis = 0;
            j.pos = Math.min(largo() - SEG * 5, j.pos + j.vel);
            return;
        }
        Botones bb = b[j.n];
        boolean acel = bb.mantiene(Botones.A) || bb.mantiene(Botones.ARRIBA), freno = bb.mantiene(Botones.B) || bb.mantiene(Botones.ABAJO);
        float dx = 0.032F * (j.vel / VMAX);
        Segmento s = segEn(j.pos + PZ);
        if (j.choqueT > 0) {
            j.choqueT--;
            j.vel *= 0.93F;
        } else {
            if (bb.mantiene(Botones.IZQ)) j.x -= dx;
            if (bb.mantiene(Botones.DER)) j.x += dx;
            j.giroVis = bb.mantiene(Botones.IZQ) ? -1 : bb.mantiene(Botones.DER) ? 1 : 0;
            float vmax = j.turboT > 0 ? VMAX * 1.3F : VMAX;
            if (acel) j.vel += VMAX / 220F;
            else if (freno) j.vel -= VMAX / 70F;
            else j.vel -= VMAX / 400F;
            if (Math.abs(j.x) > 1 && j.vel > VMAX / 3.5F) j.vel -= VMAX / 70F;      // fuera de la carretera
            j.vel = Math.max(0, Math.min(vmax, j.vel));
        }
        if (bb.pulsa(Botones.C) && j.turbos > 0 && j.turboT == 0 && cuentaT == 0) {
            j.turbos--;
            j.turboT = 150;
            sonido(ModSounds.TC_TURBO, 1F);
        }
        if (j.turboT > 0) {
            j.turboT--;
            j.vel = Math.min(VMAX * 1.3F, j.vel + VMAX / 120F);
        }
        // fuerza centrífuga en las curvas
        j.x -= dx * (j.vel / VMAX) * s.curva * 0.33F;
        if (Math.abs(s.curva) > 3 && j.vel > VMAX * 0.7F && j.derrapeCad == 0 && Math.abs(j.giroVis) > 0) {
            sonido(ModSounds.TC_DERRAPE, 1F);
            j.derrapeCad = 40;
        }
        j.x = Math.max(-2.5F, Math.min(2.5F, j.x));
        // choques con el decorado
        Segmento sj = segEn(j.pos + PZ);
        for (float[] c : sj.cosas) {
            float ancho = c[0] == 0 ? 0.35F : c[0] == 5 ? 0.5F : 0.3F;
            if (Math.abs(j.x - c[1]) < ancho && j.choqueT == 0 && j.vel > VMAX / 10F) {
                j.choqueT = 60;
                j.vel *= 0.3F;
                j.x = j.x * 0.8F;
                sonido(ModSounds.TC_CHOQUE, 1F);
                break;
            }
        }
        // tráfico
        for (Coche c : trafico) {
            float rel = Math.floorMod((int) (c.z - j.pos - PZ), largo());
            if (rel < SEG * 0.8F && Math.abs(c.x - j.x) < 0.45F && j.vel > c.v && j.choqueT == 0) {
                j.vel = c.v * 0.6F;
                j.choqueT = 20;
                sonido(ModSounds.TC_CHOQUE, 1.3F);
            }
        }
        float antes = j.pos;
        j.pos += j.vel;
        j.distancia += (long) j.vel;
        j.puntos = (int) (j.distancia / 100);
        // puntos de control y meta
        int ia = (int) ((antes + PZ) / SEG), ib = (int) ((j.pos + PZ) / SEG);
        for (int i = ia + 1; i <= ib && i < segs.size(); i++) {
            Segmento c = segs.get(i);
            if (c.control) {
                j.tramo++;
                j.tiempo += 20 * 60;
                j.controlT = 120;
                avisar(j, I18n.get("turbo.miscelanea.control", j.tramo, TRAMOS), 120);
                sonido(ModSounds.TC_PUNTO, 1F);
            }
            if (c.meta && !j.metaCruzada) {
                j.metaCruzada = true;
                j.metaPaso = pasoN;
                j.puntos += (int) (j.tiempo / 60) * 1000;
                sonido(ModSounds.TC_META, 1F);
                j.terminado = true;
            }
        }
        if (j.pos >= largo() - SEG * 5) j.pos = largo() - SEG * 5;
        j.tiempo--;
        if (j.tiempo <= 0) {
            j.tiempo = 0;
            j.vel *= 0.97F;
            if (j.vel < 1) j.terminado = true;
        }
        j.fondoX += s.curva * j.vel / VMAX * 0.6F;
        j.saltoY = (j.vel > 10 && (int) (j.pos / 40) % 2 == 0) ? 1 : 0;
    }

    // ------------------------------------------------------------------ dibujo
    @Override
    public void dibujar(Pantalla p) {
        if (jugadores == 1 || pilotos.length < 2) {
            if (pilotos.length == 0) return;
            dibujarVista(p, pilotos[0], null, 1F, Pantalla.H);
            hud(p, pilotos[0]);
            return;
        }
        // pantalla partida: cada mitad se dibuja a media escala en la pantalla auxiliar y se copia
        int W = Pantalla.W;
        for (int i = 0; i < 2; i++) {
            Pantalla v = vista;
            v.font = p.font;
            v.empezar();
            v.limpiar(37);
            dibujarVista(v, pilotos[i], pilotos[1 - i], 0.5F, MITAD);
            hudMitad(v, pilotos[i], pilotos[1 - i]);
            int oy = i * MITAD;
            System.arraycopy(v.buf, 0, p.buf, oy * W, MITAD * W);
            for (Object[] t : v.textos) {
                int y = (int) t[2];
                if (y >= 0 && y < MITAD - 6) p.textos.add(new Object[]{t[0], t[1], y + oy, t[3]});
            }
        }
        // separador entre las dos mitades
        p.rect(0, MITAD - 1, W, 1, 45);
        p.rect(0, MITAD, W, 1, 41);
    }

    /**
     * Vista de un jugador: cielo, mar, carretera, decorado, tráfico, el rival (si va delante y se ve) y su coche.
     * f escala toda la proyección (1 a pantalla completa, 0.5 en cada mitad) y vh es el alto de la vista.
     */
    void dibujarVista(Pantalla p, Piloto j, Piloto rival, float f, int vh) {
        int W = Pantalla.W, H = Pantalla.H;
        float kx = W / 2F * f, ky = H / 2F * f;
        // cielo, nubes, mar e isla con paralaje según las curvas
        int[] cielo = {39, 39, 40, 40, 62};
        int altoCielo = Math.round(96 * f);
        for (int y = 0; y < altoCielo; y++) {
            int yy = (int) (y / f);
            int banda = Math.min(4, yy / 20), fr = yy % 20;
            p.rect(0, y, W, 1, cielo[banda]);
            if (fr >= 14 && banda < 4) for (int xx = y % 2; xx < W; xx += 2) p.px(xx, y, cielo[banda + 1]);
        }
        int tile = Math.round(256 * f);
        int nx = Math.floorMod((int) (-j.fondoX * 0.5F * f), tile);
        for (int xx = nx - tile; xx < W; xx += tile) sprEsc(p, nubes, xx, Math.round(12 * f), f);
        int ix = Math.floorMod((int) (-j.fondoX * 1.2F * f), tile);
        for (int xx = ix - tile; xx < W; xx += tile) sprEsc(p, isla, xx, Math.round(52 * f), f);
        p.rect(0, Math.round(88 * f), W, Math.round(40 * f), 37);
        if (f == 1F) {
            for (int y = 89; y < 128; y += 3) for (int xx = (y * 7 + (int) j.fondoX) & 15; xx < W; xx += 16) p.rect(xx, y, 5, 1, 39);
        } else {
            int paso = Math.max(1, Math.round(16 * f)), ancho = Math.max(1, Math.round(5 * f));
            for (int y0 = 89; y0 < 128; y0 += 3) {
                int y = (int) (y0 * f);
                for (int xx = (int) ((((y0 * 7 + (int) (j.fondoX * f)) & 15)) * f); xx < W; xx += paso) p.rect(xx, y, ancho, 1, 39);
            }
        }
        // carretera
        Segmento base = segEn(j.pos);
        float baseFr = (j.pos % SEG) / SEG;
        float camY = ALTURA_CAM + interp(base.y0, base.y1, baseFr);
        float camX = j.x * ANCHO;
        float dx = -(base.curva * baseFr), xAcum = 0;
        float maxy = vh;
        int bi = base.ix;
        for (int n = 0; n < DIBUJO; n++) {
            Segmento s = segs.get((bi + n) % segs.size());
            float z0 = n * SEG - j.pos % SEG, z1 = z0 + SEG;
            if (z0 < 1) z0 = 1;
            float e0 = PROF / z0, e1 = PROF / z1;
            float x0c = camX - xAcum, x1c = camX - xAcum - dx;
            s.sx0 = W / 2F + e0 * (-x0c) * kx;
            s.sy0 = vh / 2F - e0 * (s.y0 - camY) * ky;
            s.sw0 = e0 * ANCHO * kx;
            s.sx1 = W / 2F + e1 * (-x1c) * kx;
            s.sy1 = vh / 2F - e1 * (s.y1 - camY) * ky;
            s.sw1 = e1 * ANCHO * kx;
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
                    int alto = (int) (s.esc1 * 3000 * ky);
                    p.rect((int) bx - 1, (int) s.sy1 - alto, Math.max(2, (int) (s.esc1 * 60 * W * f)), alto, 5);
                }
                int alto = (int) (s.esc1 * 3000 * ky), grosor = Math.max(2, (int) (s.esc1 * 700 * ky));
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
        // el rival: en qué segmento va su coche (a la distancia PZ por delante de su cámara)
        int rivalSeg = -1;
        float rivalFr = 0;
        if (rival != null) {
            float zr = rival.pos + PZ;
            int ci = (int) (zr / SEG), nRel = ci - bi;
            if (nRel >= 1 && nRel < DIBUJO) {
                rivalSeg = ci;
                rivalFr = (zr % SEG) / SEG;
            }
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
                spriteEsc(p, sp, sx, s.sy1, s.esc1, c[1] < 0 ? -1 : 0, s.clip, f);
            }
            for (Coche c : trafico) {
                int ci = (int) (c.z / SEG);
                if (ci != s.ix) continue;
                float sx = s.sx1 + s.sw1 * c.x;
                spriteEsc(p, traficoS[c.tipo], sx, s.sy1, s.esc1, -0.5F, s.clip, f);
            }
            if (rival != null && s.ix == rivalSeg) {
                // posición interpolada dentro del segmento (más suave que el tráfico, porque se le sigue de cerca)
                float sy = interp(s.sy0, s.sy1, rivalFr), sw = interp(s.sw0, s.sw1, rivalFr);
                float sx = interp(s.sx0, s.sx1, rivalFr) + sw * rival.x;
                float esc = sw / (ANCHO * kx);
                Sprite[] hoja = rival.n == 0 ? coche : coche2;
                int cf = rival.choqueT > 0 ? ((rival.choqueT / 4) % 3) : (int) rival.giroVis + 1;
                // si el borde lejano del segmento se ve, el coche (que va dentro del segmento) tampoco lo tapa nada
                float clip = s.clip > s.sy1 ? Math.max(s.clip, sy + 1) : s.clip;
                spriteEsc(p, hoja[Math.max(0, Math.min(2, cf))], sx, sy, esc, -0.5F, clip, f);
                // etiqueta encima del rival si se ve
                int alto = (int) (64 * esc * 8.3F * kx);
                if (sy - alto < s.clip && alto >= 6 && sy - alto - 10 >= 0)
                    p.textoC((rival.n + 1) + "P", (int) sx, (int) (sy - alto - 10), rival.n == 0 ? 12 : 37);
            }
        }
        // coche del jugador
        Sprite[] hoja = j.n == 0 ? coche : coche2;
        int cf = j.choqueT > 0 ? ((j.choqueT / 4) % 3) : (int) j.giroVis + 1;
        int cy = vh - (int) (66 * f) - (int) j.saltoY;
        sprEsc(p, hoja[Math.max(0, Math.min(2, cf))], W / 2 - (int) (32 * f), cy, f);
        int fw = Math.max(1, (int) (4 * f)), fh = Math.max(1, (int) (3 * f));
        if (j.turboT > 0 && j.turboT % 4 < 2) {
            p.rect(W / 2 - (int) (22 * f), cy + (int) (58 * f), fw, fh, 15);
            p.rect(W / 2 + (int) (18 * f), cy + (int) (58 * f), fw, fh, 15);
        }
        if (Math.abs(j.x) > 1 && j.vel > 5 && (int) (j.pos / 60) % 2 == 0) {
            int pw = Math.max(1, (int) (6 * f));
            p.rect(W / 2 - (int) (34 * f), cy + (int) (56 * f), pw, fw, 58);
            p.rect(W / 2 + (int) (28 * f), cy + (int) (56 * f), pw, fw, 58);
        }
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
    void spriteEsc(Pantalla p, Sprite sp, float sx, float sy, float esc, float ox, float clip, float f) {
        float k = esc * 8.3F * Pantalla.W / 2F * f;
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

    /** Sprite a escala fija (vecino más cercano); a escala 1 es igual que Pantalla.spr. */
    static void sprEsc(Pantalla p, Sprite s, int x, int y, float f) {
        if (s == null) return;
        if (f == 1F) {
            p.spr(s, x, y, false);
            return;
        }
        int w = Math.max(1, Math.round(s.w * f)), h = Math.max(1, Math.round(s.h * f));
        for (int j = 0; j < h; j++) {
            int yy = y + j;
            if (yy < 0 || yy >= Pantalla.H) continue;
            int v = Math.min(s.h - 1, (int) (j / f));
            for (int i = 0; i < w; i++) {
                int xx = x + i;
                if (xx < 0 || xx >= Pantalla.W) continue;
                byte c = s.px[v * s.w + Math.min(s.w - 1, (int) (i / f))];
                if (c != (byte) 255) p.buf[yy * Pantalla.W + xx] = c;
            }
        }
    }

    void hud(Pantalla p, Piloto j) {
        int seg = (int) Math.ceil(j.tiempo / 60F);
        p.textoC(I18n.get("turbo.miscelanea.tiempo_hud"), 128, 3, 7);
        p.textoC(Integer.toString(seg), 128, 13, seg <= 10 && (int) j.tiempo / 15 % 2 == 0 ? 11 : 20);
        p.texto(I18n.get("turbo.miscelanea.puntos_hud") + " " + puntos, 4, 3, 7);
        int kmh = (int) (j.vel / VMAX * 280);
        p.textoD(kmh + " km/h", 252, 3, j.turboT > 0 ? 15 : 7);
        p.textoD(I18n.get("turbo.miscelanea.tramo", Math.min(TRAMOS, j.tramo + 1), TRAMOS), 252, 13, 39);
        for (int k = 0; k < j.turbos; k++) p.rect(4 + k * 8, 15, 6, 4, 15);
        // barra de velocidad
        p.rect(4, 186, 80, 3, 1);
        p.rect(4, 186, (int) (80 * Math.min(1, j.vel / (VMAX * 1.3F))), 3, j.turboT > 0 ? 15 : 29);
        if (cuentaT > 0) {
            String c = cuentaT > 120 ? "3" : cuentaT > 60 ? "2" : "1";
            p.rect(108, 70, 40, 22, 0);
            p.textoC(c, 128, 77, 20);
        } else if (cuentaT == 0 && j.pos < SEG * 20) p.textoC(I18n.get("turbo.miscelanea.ya"), 128, 77, 29);
        if (j.avisoT > 0 && (j.avisoT > 40 || j.avisoT / 6 % 2 == 0)) {
            p.rect(0, 54, Pantalla.W, 16, 0);
            p.textoC(j.aviso, 128, 58, 20);
        }
    }

    /** Marcador compacto de cada mitad a dos jugadores. */
    void hudMitad(Pantalla p, Piloto j, Piloto rival) {
        int vh = MITAD, seg = (int) Math.ceil(j.tiempo / 60F);
        int colJ = j.n == 0 ? 12 : 37;
        // franja oscura para que se lean los textos
        p.rect(0, 0, Pantalla.W, 12, 1);
        p.rect(0, 12, Pantalla.W, 1, colJ);
        p.texto((j.n + 1) + "P", 3, 2, colJ);
        // ¿quién va delante?
        String puesto = j.terminado && rival.terminado ? "" : j.pos + (j.metaCruzada ? 1e9F : 0) >= rival.pos + (rival.metaCruzada ? 1e9F : 0) ? "1º" : "2º";
        if (!puesto.isEmpty()) p.texto(puesto, 20, 2, puesto.equals("1º") ? 20 : 6);
        for (int k = 0; k < j.turbos; k++) p.rect(38 + k * 6, 5, 4, 3, 15);
        p.textoC(I18n.get("turbo.miscelanea.tiempo_hud") + " " + seg, 112, 2, seg <= 10 && (int) j.tiempo / 15 % 2 == 0 ? 11 : 20);
        p.texto(I18n.get("turbo.miscelanea.tramo", Math.min(TRAMOS, j.tramo + 1), TRAMOS), 154, 2, 39);
        int kmh = (int) (j.vel / VMAX * 280);
        p.textoD(Integer.toString(kmh), 253, 2, j.turboT > 0 ? 15 : 7);
        // barra de velocidad
        p.rect(3, vh - 6, 50, 2, 1);
        p.rect(3, vh - 6, (int) (50 * Math.min(1, j.vel / (VMAX * 1.3F))), 2, j.turboT > 0 ? 15 : 29);
        if (cuentaT > 0) {
            String c = cuentaT > 120 ? "3" : cuentaT > 60 ? "2" : "1";
            p.rect(114, 34, 28, 16, 0);
            p.textoC(c, 128, 38, 20);
        } else if (j.pos < SEG * 20 && !j.terminado) p.textoC(I18n.get("turbo.miscelanea.ya"), 128, 38, 29);
        if (j.terminado) {
            p.rect(0, 32, Pantalla.W, 14, 0);
            p.textoC(I18n.get(j.metaCruzada ? "turbo.miscelanea.meta_corta" : "turbo.miscelanea.tiempo"), 128, 35, j.metaCruzada ? 20 : 11);
        } else if (j.avisoT > 0 && (j.avisoT > 40 || j.avisoT / 6 % 2 == 0)) {
            p.rect(0, 32, Pantalla.W, 14, 0);
            p.textoC(j.aviso, 128, 35, 20);
        }
    }
}

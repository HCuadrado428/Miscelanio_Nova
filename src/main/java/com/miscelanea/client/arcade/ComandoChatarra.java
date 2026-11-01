package com.miscelanea.client.arcade;

import com.miscelanea.client.consola.Botones;
import com.miscelanea.registry.ModSounds;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.sounds.SoundEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Comando Chatarra: run & gun de desplazamiento lateral para uno o dos jugadores a la vez.
 * Playa, ruinas, puente sobre el río y la base enemiga con el Cangrejo de Acero al final.
 * A dispara (arriba/abajo para apuntar), B salta, C lanza granada. Los rehenes liberados dejan armas.
 */
public class ComandoChatarra extends JuegoArcade {
    // ------------------------------------------------------------------ constantes del nivel
    static final int SUELO = 168, LARGO = 3500, ARENA = 3150, RIO_A = 2180, RIO_B = 2460, BASE = 2860;
    static final float GRAV = 0.22F;
    static final int[][] HOMBRO = {{15, 13}, {15, 13}, {16, 12}, {16, 11}, {16, 11}, {16, 12}, {16, 11}, {16, 11}, {15, 10}, {15, 11}, {17, 21}};
    static final int ARMA_PISTOLA = 0, ARMA_METRALLETA = 1, ARMA_COHETES = 2;

    // ------------------------------------------------------------------ sprites
    Sprite[][] cuerpo = new Sprite[2][], brazo = new Sprite[2][];
    Sprite[] rebelde, rehenS, tanqueS, heliS, torretaS, jefeS, objS, boomS, balaS, tileS;
    Sprite lejos, cerca, ruinas, palmera;

    // ------------------------------------------------------------------ entidades
    static final class Jug {
        int n;
        float x, y, vx, vy;
        boolean suelo, agachado, vivo = true, activo = true;
        int mira = 1, arma, municion, granadas = 10, vidas = 3, invul = 120, muerto, cad, anim, puntos, disparoT, apunta, lanzaT;
    }

    static final class Bala {
        float x, y, vx, vy;
        int tipo, dueno, vida, rebotes;   // tipo: 0 bala, 1 bala enemiga, 2 cohete, 3 obús, 4 granada propia, 5 granada enemiga, 6 misil
        boolean propia;
    }

    static final class Enem {
        int tipo, hp, hpMax, t, mira = -1, estado, golpe, cad;
        float x, y, vx, vy, base;
        boolean vivo = true;
        int muerteT;
    }

    static final class Rehen {
        float x, y;
        int estado, t;
    }

    static final class Obj {
        float x, y, vy;
        int tipo, t;
    }

    static final class Fx {
        float x, y, vx, vy;
        int tipo, t, vida, c;     // 0 explosión, 1 chispa, 2 partícula, 3 texto de puntos
        String txt;
    }

    static final class Plat {
        int x, y, w, tile;
    }

    static final class Caja {
        float x, y;
        int hp = 3;
    }

    final List<Jug> js = new ArrayList<>();
    final List<Bala> balas = new ArrayList<>();
    final List<Enem> enems = new ArrayList<>();
    final List<Rehen> rehenes = new ArrayList<>();
    final List<Obj> objs = new ArrayList<>();
    final List<Fx> fx = new ArrayList<>();
    final List<Plat> plats = new ArrayList<>();
    final List<Caja> cajas = new ArrayList<>();
    final List<int[]> oleadas = new ArrayList<>();     // x de activación, tipo, cantidad, lado
    final List<int[]> palmeras = new ArrayList<>();
    float cam;
    int t, temblor, aviso, avisoT, jefeAviso, finT;
    boolean bloqueo, jefeMuerto;
    String avisoTxt = "";
    int sonidoCad;

    public ComandoChatarra(Botones[] b, long semilla, int jugadores) {
        super(b, semilla, jugadores);
    }

    @Override
    public SoundEvent musica() {
        return ModSounds.CH_MUSICA.get();
    }

    private void cargarSprites() {
        for (int i = 0; i < 2; i++) {
            cuerpo[i] = Sprite.hoja("chatarra/jugador" + (i + 1), 32, 32);
            brazo[i] = Sprite.hoja("chatarra/brazos" + (i + 1), 32, 32);
        }
        rebelde = Sprite.hoja("chatarra/rebelde", 32, 32);
        rehenS = Sprite.hoja("chatarra/rehen", 32, 32);
        tanqueS = Sprite.hoja("chatarra/tanque", 64, 40);
        heliS = Sprite.hoja("chatarra/helicoptero", 64, 32);
        torretaS = Sprite.hoja("chatarra/torreta", 32, 32);
        jefeS = Sprite.hoja("chatarra/jefe", 112, 80);
        objS = Sprite.hoja("chatarra/objetos", 16, 16);
        boomS = Sprite.hoja("chatarra/explosion", 32, 32);
        balaS = Sprite.hoja("chatarra/balas", 8, 8);
        tileS = Sprite.hoja("chatarra/tiles", 16, 16);
        lejos = Sprite.uno("chatarra/fondo_lejos");
        cerca = Sprite.uno("chatarra/fondo_cerca");
        ruinas = Sprite.uno("chatarra/fondo_ruinas");
        palmera = Sprite.uno("chatarra/palmera");
    }

    @Override
    public void reiniciar() {
        cargarSprites();
        js.clear();
        for (int i = 0; i < jugadores; i++) {
            Jug j = new Jug();
            j.n = i;
            j.x = 40 + i * 24;
            j.y = SUELO;
            js.add(j);
        }
        balas.clear();
        enems.clear();
        rehenes.clear();
        objs.clear();
        fx.clear();
        plats.clear();
        cajas.clear();
        oleadas.clear();
        palmeras.clear();
        cam = 0;
        t = 0;
        puntos = 0;
        fin = false;
        bloqueo = false;
        jefeMuerto = false;
        construirNivel();
        anunciar(I18n.get("chatarra.miscelanea.mision"), 150);
    }

    private void plat(int x, int y, int w, int tile) {
        Plat p = new Plat();
        p.x = x;
        p.y = y;
        p.w = w;
        p.tile = tile;
        plats.add(p);
    }

    private void oleada(int x, int tipo, int n, int lado) {
        oleadas.add(new int[]{x, tipo, n, lado});
    }

    private void construirNivel() {
        // playa
        for (int x = 60; x < 1000; x += 90 + rnd.nextInt(60)) palmeras.add(new int[]{x, 0});
        plat(300, SUELO - 16, 48, 2);
        plat(560, SUELO - 16, 32, 2);
        caja(420, SUELO);
        caja(436, SUELO);
        rehen(500, SUELO);
        oleada(120, 0, 2, 1);
        oleada(330, 0, 3, 1);
        oleada(520, 1, 1, 1);
        oleada(640, 0, 3, 1);
        oleada(760, 4, 1, 0);
        oleada(880, 0, 2, -1);
        oleada(980, 2, 1, 1);
        // ruinas
        plat(1150, 128, 80, 4);
        plat(1300, 104, 64, 4);
        plat(1420, 136, 96, 4);
        plat(1600, 112, 80, 4);
        rehen(1320, 104);
        rehen(1640, 112);
        caja(1500, SUELO);
        caja(1516, SUELO);
        caja(1508, SUELO - 16);
        oleada(1100, 0, 3, 1);
        oleada(1180, 1, 2, 1);
        oleada(1310, 4, 1, 0);
        oleada(1430, 0, 4, 1);
        oleada(1560, 3, 1, 1);
        oleada(1700, 0, 3, -1);
        oleada(1800, 1, 2, 1);
        oleada(1950, 2, 1, 1);
        // puente y río
        oleada(2100, 0, 3, 1);
        oleada(2250, 3, 1, 1);
        oleada(2350, 1, 2, 1);
        rehen(2560, SUELO);
        plat(2620, SUELO - 16, 48, 2);
        oleada(2600, 0, 4, 1);
        oleada(2700, 2, 1, 1);
        // base
        plat(2930, 136, 64, 8);
        plat(3040, 120, 48, 8);
        rehen(2960, 136);
        caja(3080, SUELO);
        oleada(2880, 4, 1, 0);
        oleada(2950, 0, 4, 1);
        oleada(3050, 1, 2, 1);
        oleada(ARENA - 60, 5, 1, 1);
    }

    private void caja(float x, float y) {
        Caja c = new Caja();
        c.x = x;
        c.y = y;
        cajas.add(c);
    }

    private void rehen(float x, float y) {
        Rehen r = new Rehen();
        r.x = x;
        r.y = y;
        rehenes.add(r);
    }

    private void anunciar(String s, int dur) {
        avisoTxt = s;
        avisoT = dur;
    }

    // ------------------------------------------------------------------ terreno
    boolean enRio(float x) {
        return x > RIO_A && x < RIO_B;
    }

    /** Altura del suelo bajo x (los pies se apoyan aquí). En el río hay un puente. */
    float sueloEn(float x) {
        if (enRio(x)) return SUELO - 8;
        return SUELO;
    }

    /** Suelo o plataforma más alta por debajo de y en x (para caer encima). */
    float apoyo(float x, float yAntes, float yAhora) {
        float mejor = sueloEn(x);
        for (Plat p : plats) if (x >= p.x && x <= p.x + p.w && yAntes <= p.y + 0.5F && yAhora >= p.y && p.y < mejor) mejor = p.y;
        for (Caja c : cajas) if (x >= c.x - 8 && x <= c.x + 8 && yAntes <= c.y - 16 + 0.5F && yAhora >= c.y - 16 && c.y - 16 < mejor) mejor = c.y - 16;
        return mejor;
    }

    // ------------------------------------------------------------------ lógica
    @Override
    public void paso() {
        t++;
        if (avisoT > 0) avisoT--;
        if (temblor > 0) temblor--;
        if (sonidoCad > 0) sonidoCad--;
        // cámara: solo avanza; se bloquea en la arena del jefe
        float lider = -1;
        for (Jug j : js) if (j.activo && j.vivo) lider = Math.max(lider, j.x);
        if (lider > 0) {
            float obj = Math.min(lider - 100, LARGO - Pantalla.W);
            if (bloqueo && !jefeMuerto) obj = Math.min(obj, ARENA);
            if (obj > cam) cam += Math.min(3F, obj - cam);
        }
        if (!bloqueo && cam >= ARENA - 2) {
            bloqueo = true;
            sonido(ModSounds.CH_JEFE, 1F);
            anunciar(I18n.get("chatarra.miscelanea.jefe"), 120);
        }
        // oleadas
        for (Iterator<int[]> it = oleadas.iterator(); it.hasNext(); ) {
            int[] o = it.next();
            if (cam + 200 >= o[0]) {
                it.remove();
                for (int k = 0; k < o[2]; k++) generar(o[1], o[3], k, o[0]);
            }
        }
        for (Jug j : js) pasoJugador(j);
        pasoBalas();
        pasoEnemigos();
        pasoRehenes();
        pasoObjetos();
        pasoFx();
        // fin de partida
        boolean alguno = false;
        for (Jug j : js) if (j.activo && (j.vivo || j.vidas > 0)) alguno = true;
        if (!alguno) {
            if (++finT > 90) {
                fin = true;
                mensajeFin = I18n.get("chatarra.miscelanea.derrota");
            }
        }
        if (jefeMuerto && ++finT > 240) {
            for (Jug j : js) j.puntos += j.vidas * 2000;
            fin = true;
            mensajeFin = I18n.get("chatarra.miscelanea.victoria");
        }
        puntos = 0;
        for (Jug j : js) puntos += j.puntos;
    }

    private void generar(int tipo, int lado, int k, int x0) {
        Enem e = new Enem();
        e.tipo = tipo;
        switch (tipo) {
            case 0, 1 -> {
                e.hp = 1;
                e.x = lado >= 0 ? cam + Pantalla.W + 16 + k * 26 : cam - 16 - k * 26;
                e.y = SUELO;
                e.vx = lado >= 0 ? -0.55F : 0.55F;
                e.mira = lado >= 0 ? -1 : 1;
                e.cad = 60 + rnd.nextInt(80) + k * 20;
            }
            case 2 -> {
                e.hp = e.hpMax = 26;
                e.x = cam + Pantalla.W + 40;
                e.y = SUELO;
                e.vx = -0.35F;
                e.cad = 100;
            }
            case 3 -> {
                e.hp = e.hpMax = 34;
                e.x = cam + Pantalla.W + 40;
                e.y = 64;
                e.base = 64;
                e.cad = 80;
            }
            case 4 -> {
                e.hp = e.hpMax = 14;
                e.x = x0 + 120;
                e.y = SUELO;
                e.cad = 90;
            }
            case 5 -> {
                e.hp = e.hpMax = 340;
                e.x = ARENA + 210;
                e.y = SUELO;
                e.vx = -0.3F;
                e.cad = 120;
            }
            default -> {}
        }
        enems.add(e);
    }

    private Jug masCercano(float x) {
        Jug m = null;
        float md = Float.MAX_VALUE;
        for (Jug j : js) if (j.activo && j.vivo && Math.abs(j.x - x) < md) {
            md = Math.abs(j.x - x);
            m = j;
        }
        return m;
    }

    private void pasoJugador(Jug j) {
        if (!j.activo) return;
        Botones bb = b[Math.min(j.n, b.length - 1)];
        if (!j.vivo) {
            if (++j.muerto > 70 && j.vidas > 0) {
                j.vidas--;
                j.vivo = true;
                j.muerto = 0;
                j.x = cam + 30 + j.n * 20;
                j.y = 20;
                j.vy = 0;
                j.invul = 150;
                j.arma = ARMA_PISTOLA;
                j.granadas = Math.max(j.granadas, 10);
            }
            return;
        }
        if (j.invul > 0) j.invul--;
        if (j.cad > 0) j.cad--;
        if (j.disparoT > 0) j.disparoT--;
        if (j.lanzaT > 0) j.lanzaT--;
        boolean izq = bb.mantiene(Botones.IZQ), der = bb.mantiene(Botones.DER);
        j.agachado = j.suelo && bb.mantiene(Botones.ABAJO);
        j.apunta = bb.mantiene(Botones.ARRIBA) ? 1 : (!j.suelo && bb.mantiene(Botones.ABAJO)) ? 2 : 0;
        float vel = j.agachado ? 0.6F : 1.25F;
        j.vx = (der ? vel : 0) - (izq ? vel : 0);
        if (der) j.mira = 1;
        if (izq) j.mira = -1;
        if (bb.pulsa(Botones.B) && j.suelo && !j.agachado) {
            j.vy = -4.4F;
            j.suelo = false;
            sonido(ModSounds.CH_SALTO, 1.1F);
        }
        float yAntes = j.y;
        j.vy = Math.min(5F, j.vy + GRAV);
        j.x += j.vx;
        j.y += j.vy;
        j.x = Math.max(cam + 6, Math.min(cam + Pantalla.W - 6, j.x));
        if (enRio(j.x) && j.y > SUELO + 10) matar(j);
        float ap = apoyo(j.x, yAntes, j.y);
        if (j.vy >= 0 && j.y >= ap) {
            j.y = ap;
            j.vy = 0;
            j.suelo = true;
        } else if (j.y < ap - 0.5F) j.suelo = false;
        if (j.vx != 0 && j.suelo) j.anim++;
        // disparar
        boolean pulsa = bb.pulsa(Botones.A), mantiene = bb.mantiene(Botones.A);
        if ((pulsa || (j.arma == ARMA_METRALLETA && mantiene)) && j.cad == 0) {
            Enem cuerpo = enemigoCerca(j);
            if (cuerpo != null && j.apunta == 0) {
                // cuerpo a cuerpo: cuchillo
                danar(cuerpo, 3, j.n);
                j.cad = 14;
                sonido(ModSounds.CH_GOLPE, 1.4F);
            } else disparar(j);
        }
        if (bb.pulsa(Botones.C) && j.granadas > 0) {
            j.granadas--;
            Bala g = new Bala();
            g.tipo = 4;
            g.propia = true;
            g.dueno = j.n;
            g.x = j.x + j.mira * 4;
            g.y = j.y - 20;
            g.vx = j.mira * 2.2F + j.vx * 0.5F;
            g.vy = -3.2F;
            balas.add(g);
            j.lanzaT = 12;
            sonido(ModSounds.CH_GRANADA, 1.2F);
        }
        // tocar a un rehén lo libera
        for (Rehen r : rehenes)
            if (r.estado == 0 && Math.abs(r.x - j.x) < 12 && Math.abs(r.y - j.y) < 16) {
                r.estado = 1;
                r.t = 0;
                j.puntos += 1000;
                texto(r.x, r.y - 30, "+1000", 20);
                sonido(ModSounds.CH_RESCATE, 1F);
            }
        // recoger objetos
        for (Iterator<Obj> it = objs.iterator(); it.hasNext(); ) {
            Obj o = it.next();
            if (Math.abs(o.x - j.x) < 12 && Math.abs(o.y - j.y) < 20) {
                it.remove();
                switch (o.tipo) {
                    case 0 -> {
                        j.arma = ARMA_METRALLETA;
                        j.municion = 200;
                        anunciar(I18n.get("chatarra.miscelanea.metralleta"), 60);
                    }
                    case 1 -> {
                        j.arma = ARMA_COHETES;
                        j.municion = 30;
                        anunciar(I18n.get("chatarra.miscelanea.cohetes"), 60);
                    }
                    case 3 -> j.granadas += 10;
                    case 4 -> {
                        j.puntos += 500;
                        texto(o.x, o.y - 18, "+500", 20);
                    }
                    default -> {
                        j.puntos += 100;
                        texto(o.x, o.y - 18, "+100", 20);
                    }
                }
                sonido(ModSounds.CH_ARMA, 1F);
            }
        }
    }

    private Enem enemigoCerca(Jug j) {
        for (Enem e : enems)
            if (e.vivo && (e.tipo == 0 || e.tipo == 1) && Math.abs(e.x - (j.x + j.mira * 10)) < 12 && Math.abs(e.y - j.y) < 14) return e;
        return null;
    }

    private void disparar(Jug j) {
        float hx = j.x + (HOMBRO[frame(j)][0] - 16) * j.mira, hy = j.y - 32 + HOMBRO[frame(j)][1];
        float dx = j.apunta == 1 ? 0 : j.apunta == 2 ? 0 : j.mira, dy = j.apunta == 1 ? -1 : j.apunta == 2 ? 1 : 0;
        float bx = hx + dx * 10, by = hy + dy * 10 + (dy == 0 ? 1 : 0);
        Bala bl = new Bala();
        bl.propia = true;
        bl.dueno = j.n;
        bl.x = bx;
        bl.y = by;
        switch (j.arma) {
            case ARMA_METRALLETA -> {
                bl.tipo = 0;
                float disp = (rnd.nextFloat() - 0.5F) * 0.5F;
                bl.vx = dx * 6 + (dy != 0 ? disp : 0);
                bl.vy = dy * 6 + (dx != 0 ? disp : 0);
                j.cad = 5;
                if (--j.municion <= 0) j.arma = ARMA_PISTOLA;
                if (sonidoCad == 0) {
                    sonido(ModSounds.CH_METRALLA, 0.9F + rnd.nextFloat() * 0.2F);
                    sonidoCad = 3;
                }
            }
            case ARMA_COHETES -> {
                bl.tipo = 2;
                bl.vx = dx * 1.5F;
                bl.vy = dy * 1.5F;
                j.cad = 22;
                if (--j.municion <= 0) j.arma = ARMA_PISTOLA;
                sonido(ModSounds.CH_COHETE, 1F);
            }
            default -> {
                int propias = 0;
                for (Bala x : balas) if (x.propia && x.tipo == 0 && x.dueno == j.n) propias++;
                if (propias >= 4) return;
                bl.tipo = 0;
                bl.vx = dx * 5.5F;
                bl.vy = dy * 5.5F;
                j.cad = 8;
                sonido(ModSounds.CH_DISPARO, 1F + rnd.nextFloat() * 0.1F);
            }
        }
        balas.add(bl);
        j.disparoT = 6;
        // casquillo
        Fx c = new Fx();
        c.tipo = 2;
        c.x = hx;
        c.y = hy;
        c.vx = -j.mira * (0.5F + rnd.nextFloat());
        c.vy = -1.5F - rnd.nextFloat();
        c.vida = 30;
        c.c = 19;
        fx.add(c);
    }

    private void matar(Jug j) {
        if (!j.vivo || j.invul > 0) return;
        j.vivo = false;
        j.muerto = 0;
        j.vy = -2;
        sonido(ModSounds.CH_MUERTE, 1F);
    }

    private void pasoBalas() {
        for (Iterator<Bala> it = balas.iterator(); it.hasNext(); ) {
            Bala bl = it.next();
            bl.vida++;
            if (bl.tipo == 3 || bl.tipo == 4 || bl.tipo == 5) bl.vy += GRAV * 0.8F;
            if (bl.tipo == 2) {
                // cohete: acelera y busca al enemigo más cercano
                Enem obj = null;
                float md = 140;
                for (Enem e : enems)
                    if (e.vivo) {
                        float d = Math.abs(e.x - bl.x) + Math.abs(e.y - 16 - bl.y);
                        if (d < md) {
                            md = d;
                            obj = e;
                        }
                    }
                float sp = Math.min(5F, (float) Math.hypot(bl.vx, bl.vy) + 0.15F);
                float ax = bl.vx, ay = bl.vy;
                if (obj != null) {
                    float tx = obj.x - bl.x, ty = obj.y - alto(obj) / 2F - bl.y;
                    float l = (float) Math.hypot(tx, ty) + 0.01F;
                    ax += tx / l * 0.35F;
                    ay += ty / l * 0.35F;
                }
                float l = (float) Math.hypot(ax, ay) + 0.01F;
                bl.vx = ax / l * sp;
                bl.vy = ay / l * sp;
                if (t % 3 == 0) humo(bl.x, bl.y);
            }
            if (bl.tipo == 6) {
                // misil del jefe: sube y luego cae sobre un jugador
                if (bl.vida < 30) bl.vy = -2.5F;
                else if (bl.vida == 30) {
                    Jug j = masCercano(bl.x);
                    bl.x = j != null ? j.x : bl.x;
                    bl.y = -10;
                    bl.vx = 0;
                    bl.vy = 2.4F;
                }
                if (t % 4 == 0) humo(bl.x, bl.y);
            }
            bl.x += bl.vx;
            bl.y += bl.vy;
            boolean quitar = bl.x < cam - 20 || bl.x > cam + Pantalla.W + 20 || bl.y > Pantalla.H + 10 || bl.y < -60 || bl.vida > 400;
            // granadas: rebotan en el suelo y explotan
            if ((bl.tipo == 4 || bl.tipo == 5 || bl.tipo == 3) && bl.y >= sueloEn(bl.x) - 2) {
                if (bl.tipo == 4 && bl.rebotes == 0 && !enRio(bl.x)) {
                    bl.y = sueloEn(bl.x) - 2;
                    bl.vy = -1.6F;
                    bl.vx *= 0.6F;
                    bl.rebotes++;
                } else {
                    explotar(bl.x, bl.y, bl.propia, bl.dueno, bl.tipo == 4 ? 26 : 20, bl.tipo == 4 ? 12 : 1);
                    quitar = true;
                }
            }
            if (!quitar && bl.tipo == 6 && bl.vida > 30 && bl.y >= SUELO - 2) {
                explotar(bl.x, bl.y, false, 0, 22, 1);
                quitar = true;
            }
            if (!quitar && bl.propia) {
                for (Enem e : enems) {
                    if (!e.vivo || !dentro(e, bl.x, bl.y)) continue;
                    if (bl.tipo == 2 || bl.tipo == 4) explotar(bl.x, bl.y, true, bl.dueno, bl.tipo == 2 ? 22 : 26, bl.tipo == 2 ? 8 : 12);
                    else danar(e, 1, bl.dueno);
                    chispa(bl.x, bl.y);
                    quitar = true;
                    break;
                }
                if (!quitar) for (Iterator<Caja> ic = cajas.iterator(); ic.hasNext(); ) {
                    Caja c = ic.next();
                    if (bl.x > c.x - 8 && bl.x < c.x + 8 && bl.y > c.y - 16 && bl.y < c.y) {
                        quitar = true;
                        chispa(bl.x, bl.y);
                        if (--c.hp <= 0) romperCaja(c, ic);
                        break;
                    }
                }
            } else if (!quitar) {
                for (Jug j : js)
                    if (j.activo && j.vivo && j.invul == 0 && Math.abs(bl.x - j.x) < 6 && bl.y > j.y - (j.agachado ? 16 : 26) && bl.y < j.y) {
                        if (bl.tipo == 3 || bl.tipo == 5 || bl.tipo == 6) explotar(bl.x, bl.y, false, 0, 20, 1);
                        matar(j);
                        quitar = true;
                        break;
                    }
            }
            if (quitar) it.remove();
        }
    }

    private void romperCaja(Caja c, Iterator<Caja> it) {
        it.remove();
        explotar(c.x, c.y - 8, true, 0, 0, 0);
        soltarObjeto(c.x, c.y - 10, rnd.nextInt(3) == 0 ? 3 : 4 + rnd.nextInt(2));
    }

    private void soltarObjeto(float x, float y, int tipo) {
        Obj o = new Obj();
        o.x = x;
        o.y = y;
        o.vy = -2;
        o.tipo = tipo;
        objs.add(o);
    }

    int alto(Enem e) {
        return switch (e.tipo) {
            case 2 -> 34;
            case 3 -> 26;
            case 4 -> 24;
            case 5 -> 70;
            default -> 26;
        };
    }

    int ancho(Enem e) {
        return switch (e.tipo) {
            case 2 -> 54;
            case 3 -> 52;
            case 4 -> 26;
            case 5 -> 90;
            default -> 12;
        };
    }

    boolean dentro(Enem e, float x, float y) {
        float w = ancho(e), h = alto(e);
        float top = e.tipo == 3 ? e.y - h : e.y - h;
        return x > e.x - w / 2 && x < e.x + w / 2 && y > top && y < e.y;
    }

    private void explotar(float x, float y, boolean propia, int dueno, int radio, int dano) {
        Fx f = new Fx();
        f.tipo = 0;
        f.x = x;
        f.y = y;
        fx.add(f);
        temblor = Math.max(temblor, 6);
        sonido(ModSounds.CH_EXPLOSION, 0.9F + rnd.nextFloat() * 0.2F);
        if (radio <= 0) return;
        if (propia) {
            for (Enem e : enems)
                if (e.vivo && Math.abs(e.x - x) < radio + ancho(e) / 2F && Math.abs(e.y - alto(e) / 2F - y) < radio + alto(e) / 2F) danar(e, dano, dueno);
            for (Iterator<Caja> ic = cajas.iterator(); ic.hasNext(); ) {
                Caja c = ic.next();
                if (Math.abs(c.x - x) < radio + 8 && Math.abs(c.y - 8 - y) < radio + 8) romperCaja(c, ic);
            }
        } else for (Jug j : js) if (j.activo && j.vivo && Math.abs(j.x - x) < radio && Math.abs(j.y - 12 - y) < radio + 8) matar(j);
    }

    private void danar(Enem e, int d, int dueno) {
        e.hp -= d;
        e.golpe = 4;
        if (e.tipo >= 2 && sonidoCad == 0) {
            sonido(ModSounds.CH_GOLPE, 0.9F + rnd.nextFloat() * 0.2F);
            sonidoCad = 4;
        }
        if (e.hp <= 0 && e.vivo) {
            e.vivo = false;
            e.muerteT = 0;
            int pts = switch (e.tipo) {
                case 0 -> 100;
                case 1 -> 150;
                case 2 -> 1000;
                case 3 -> 1500;
                case 4 -> 500;
                default -> 10000;
            };
            Jug j = js.get(Math.min(dueno, js.size() - 1));
            j.puntos += pts;
            texto(e.x, e.y - alto(e) - 4, "+" + pts, 20);
            if (e.tipo >= 2) {
                for (int k = 0; k < (e.tipo == 5 ? 10 : 4); k++) {
                    Fx f = new Fx();
                    f.tipo = 0;
                    f.x = e.x + (rnd.nextFloat() - 0.5F) * ancho(e);
                    f.y = e.y - rnd.nextFloat() * alto(e);
                    f.t = -k * 6;
                    fx.add(f);
                }
                for (int k = 0; k < 16; k++) particula(e.x, e.y - alto(e) / 2F, 3);
                sonido(ModSounds.CH_EXPLOSION, 0.7F);
                temblor = 20;
                if (e.tipo == 5) {
                    jefeMuerto = true;
                    finT = 0;
                    anunciar(I18n.get("chatarra.miscelanea.jefe_caido"), 200);
                } else if (rnd.nextInt(2) == 0) soltarObjeto(e.x, e.y - 20, rnd.nextInt(2));
            }
        }
    }

    private void pasoEnemigos() {
        for (Iterator<Enem> it = enems.iterator(); it.hasNext(); ) {
            Enem e = it.next();
            e.t++;
            if (e.golpe > 0) e.golpe--;
            if (!e.vivo) {
                e.muerteT++;
                if (e.tipo == 3) {
                    e.vy += GRAV;
                    e.y += e.vy;
                    if (e.y >= SUELO) {
                        if (e.muerteT < 200) explotar(e.x, e.y - 10, true, 0, 0, 0);
                        e.muerteT = 999;
                    }
                }
                if (e.muerteT > (e.tipo <= 1 ? 50 : 30) || e.muerteT >= 999) it.remove();
                continue;
            }
            Jug j = masCercano(e.x);
            if (e.x < cam - 80 || e.x > cam + Pantalla.W + 160) {
                if (e.x < cam - 80) it.remove();
                continue;
            }
            if (e.cad > 0) e.cad--;
            switch (e.tipo) {
                case 0, 1 -> {
                    if (e.estado == 2 && e.cad < 100) e.estado = 1;
                    if (j != null) e.mira = j.x < e.x ? -1 : 1;
                    float dist = j == null ? 999 : Math.abs(j.x - e.x);
                    boolean enPantalla = e.x > cam + 8 && e.x < cam + Pantalla.W - 8;
                    if (dist > (e.tipo == 1 ? 110 : 80) || !enPantalla) {
                        e.x += e.mira * 0.55F;
                        e.estado = 0;
                    } else e.estado = 1;
                    if (e.estado == 1 && e.cad == 0 && j != null && enPantalla) {
                        Bala bl = new Bala();
                        bl.x = e.x + e.mira * 14;
                        if (e.tipo == 0) {
                            bl.tipo = 1;
                            bl.y = e.y - 18;
                            bl.vx = e.mira * 1.9F;
                            sonido(ModSounds.CH_DISPARO, 0.75F);
                            e.cad = 110 + rnd.nextInt(80);
                        } else {
                            bl.tipo = 5;
                            bl.y = e.y - 26;
                            float d = j.x - e.x;
                            bl.vx = d / 55F;
                            bl.vy = -3.2F;
                            sonido(ModSounds.CH_GRANADA, 0.8F);
                            e.cad = 140 + rnd.nextInt(60);
                        }
                        balas.add(bl);
                        e.estado = 2;
                    }
                }
                case 2 -> {
                    if (j != null && e.x - j.x > 150) e.x += e.vx;
                    if (e.cad == 0 && j != null) {
                        Bala bl = new Bala();
                        bl.tipo = 3;
                        bl.x = e.x - 30;
                        bl.y = e.y - 28;
                        float d = j.x - bl.x;
                        bl.vx = Math.max(-4, Math.min(-1, d / 45F));
                        bl.vy = -2.6F;
                        balas.add(bl);
                        sonido(ModSounds.CH_EXPLOSION, 1.6F);
                        e.cad = 130;
                    }
                    for (Jug q : js) if (q.vivo && Math.abs(q.x - e.x) < 28 && q.y > e.y - 30) matar(q);
                }
                case 3 -> {
                    if (j != null) e.x += Math.signum(j.x + 20 - e.x) * Math.min(1F, Math.abs(j.x + 20 - e.x) * 0.02F);
                    e.y = e.base + (float) StrictMath.sin(e.t * 0.05) * 6;
                    if (e.cad == 0) {
                        Bala bl = new Bala();
                        bl.tipo = 3;
                        bl.x = e.x;
                        bl.y = e.y;
                        bl.vx = 0;
                        bl.vy = 0.5F;
                        balas.add(bl);
                        e.cad = 70 + rnd.nextInt(40);
                    }
                    if (t % 50 == 0 && j != null) {
                        Bala bl = new Bala();
                        bl.tipo = 1;
                        bl.x = e.x - 6;
                        bl.y = e.y - 4;
                        float dx = j.x - bl.x, dy = j.y - 14 - bl.y, l = (float) Math.hypot(dx, dy);
                        bl.vx = dx / l * 2F;
                        bl.vy = dy / l * 2F;
                        balas.add(bl);
                    }
                }
                case 4 -> {
                    if (e.cad == 0 && j != null && e.x - j.x < 240) {
                        for (int k = 0; k < 3; k++) {
                            Bala bl = new Bala();
                            bl.tipo = 1;
                            bl.x = e.x - 14;
                            bl.y = e.y - 18;
                            float dx = j.x - bl.x, dy = j.y - 14 - bl.y, l = (float) Math.hypot(dx, dy);
                            bl.vx = dx / l * (1.8F + k * 0.25F);
                            bl.vy = dy / l * (1.8F + k * 0.25F);
                            balas.add(bl);
                        }
                        sonido(ModSounds.CH_DISPARO, 0.7F);
                        e.cad = 120;
                    }
                }
                case 5 -> {
                    // Cangrejo de Acero: camina por la arena, cañonazos y misiles (más rabioso a media vida)
                    float min = ARENA + 120, max = ARENA + 220;
                    e.x += e.vx;
                    if (e.x < min) e.vx = 0.3F;
                    if (e.x > max) e.vx = -0.3F;
                    boolean rabia = e.hp < e.hpMax / 2;
                    if (e.cad == 0) {
                        Bala bl = new Bala();
                        bl.tipo = 3;
                        bl.x = e.x - 52;
                        bl.y = e.y - 62;
                        bl.vx = -3.2F - rnd.nextFloat();
                        bl.vy = -1F;
                        balas.add(bl);
                        sonido(ModSounds.CH_EXPLOSION, 1.5F);
                        e.cad = rabia ? 70 : 110;
                        temblor = 4;
                    }
                    if (e.t % (rabia ? 110 : 170) == 0) for (int k = 0; k < (rabia ? 3 : 2); k++) {
                        Bala bl = new Bala();
                        bl.tipo = 6;
                        bl.x = e.x + 40;
                        bl.y = e.y - 60 + k * 5;
                        bl.vida = -k * 12;
                        balas.add(bl);
                        sonido(ModSounds.CH_COHETE, 0.8F);
                    }
                    for (Jug q : js) if (q.vivo && Math.abs(q.x - e.x) < 40 && q.y > e.y - 20) matar(q);
                }
                default -> {}
            }
        }
    }

    private void pasoRehenes() {
        for (Iterator<Rehen> it = rehenes.iterator(); it.hasNext(); ) {
            Rehen r = it.next();
            r.t++;
            if (r.estado == 1 && r.t > 40) {
                r.estado = 2;
                soltarObjeto(r.x + 8, r.y - 16, rnd.nextInt(4) == 0 ? 3 : rnd.nextInt(2));
            } else if (r.estado == 2) {
                r.x -= 1.4F;
                if (r.x < cam - 30) it.remove();
            }
            if (r.estado != 0) {
                float ap = apoyo(r.x, r.y, r.y + 1);
                if (r.y < ap) r.y = Math.min(ap, r.y + 2);
            }
        }
    }

    private void pasoObjetos() {
        for (Iterator<Obj> it = objs.iterator(); it.hasNext(); ) {
            Obj o = it.next();
            o.t++;
            float yAntes = o.y;
            o.vy = Math.min(3, o.vy + GRAV);
            o.y += o.vy;
            float ap = apoyo(o.x, yAntes, o.y);
            if (o.y >= ap) {
                o.y = ap;
                o.vy = 0;
            }
            if (o.t > 900 || o.x < cam - 20) it.remove();
        }
    }

    private void pasoFx() {
        for (Iterator<Fx> it = fx.iterator(); it.hasNext(); ) {
            Fx f = it.next();
            f.t++;
            if (f.tipo == 2) {
                f.vy += GRAV * 0.7F;
                f.x += f.vx;
                f.y += f.vy;
            } else if (f.tipo == 3) f.y -= 0.4F;
            else if (f.tipo == 4) f.y -= 0.3F;
            int dur = switch (f.tipo) {
                case 0 -> 30;
                case 1 -> 8;
                case 3 -> 50;
                case 4 -> 24;
                default -> f.vida;
            };
            if (f.t > dur) it.remove();
        }
    }

    private void chispa(float x, float y) {
        Fx f = new Fx();
        f.tipo = 1;
        f.x = x;
        f.y = y;
        fx.add(f);
    }

    private void humo(float x, float y) {
        Fx f = new Fx();
        f.tipo = 4;
        f.x = x;
        f.y = y;
        fx.add(f);
    }

    private void particula(float x, float y, int c) {
        Fx f = new Fx();
        f.tipo = 2;
        f.x = x;
        f.y = y;
        f.vx = (rnd.nextFloat() - 0.5F) * 4;
        f.vy = -rnd.nextFloat() * 4;
        f.vida = 40;
        f.c = c;
        fx.add(f);
    }

    private void texto(float x, float y, String s, int c) {
        Fx f = new Fx();
        f.tipo = 3;
        f.x = x;
        f.y = y;
        f.txt = s;
        f.c = c;
        fx.add(f);
    }

    // ------------------------------------------------------------------ dibujo
    int frame(Jug j) {
        if (!j.suelo) return j.vy < 0 ? 8 : 9;
        if (j.agachado) return 10;
        if (j.vx != 0) return 2 + (j.anim / 5) % 6;
        return (t / 30) % 2;
    }

    @Override
    public void dibujar(Pantalla p) {
        int sx = Math.round(cam) + (temblor > 0 ? (t % 2 == 0 ? 1 : -1) * Math.min(2, temblor / 3) : 0);
        // cielo: degradado por bandas (más rojizo al acercarse a la base)
        boolean tarde = cam > BASE - 300;
        int[] cielo = tarde ? new int[]{41, 42, 45, 13, 16} : new int[]{37, 38, 39, 39, 40};
        for (int y = 0; y < 120; y++) p.rect(0, y, Pantalla.W, 1, cielo[Math.min(4, y / 24)]);
        // capas lejanas
        int ox = Math.floorMod(-(int) (sx * 0.15F), 256);
        p.spr(lejos, ox - 256, 40, false);
        p.spr(lejos, ox, 40, false);
        Sprite medio = sx > 1000 && sx < 2000 ? ruinas : cerca;
        int ox2 = Math.floorMod(-(int) (sx * 0.4F), 256);
        p.spr(medio, ox2 - 256, 80, false);
        p.spr(medio, ox2, 80, false);
        p.camX = sx;
        // palmeras de la playa
        for (int[] pm : palmeras) if (pm[0] - sx > -40 && pm[0] - sx < Pantalla.W + 10) p.spr(palmera, pm[0] - sx, SUELO - 64, false);
        // suelo
        for (int x = (sx / 16) * 16; x < sx + Pantalla.W + 16; x += 16) {
            int px = x - sx;
            if (enRio(x + 8)) {
                for (int y = SUELO + 4; y < Pantalla.H; y += 16) p.spr(tileS[6 + ((t / 20 + x / 16) % 2)], px, y, false);
                p.spr(tileS[5], px, SUELO - 8, false);
                p.rect(px, SUELO - 10, 16, 1, 50);
                if ((x / 16) % 4 == 0) p.rect(px + 6, SUELO - 22, 2, 12, 50);
                continue;
            }
            int top = x >= BASE ? 8 : 0, fondo = x >= BASE ? 8 : 1;
            p.spr(tileS[top], px, SUELO, false);
            for (int y = SUELO + 16; y < Pantalla.H; y += 16) p.spr(tileS[fondo], px, y, false);
        }
        for (Plat pl : plats) for (int x = 0; x < pl.w; x += 16) p.spr(tileS[pl.tile], pl.x + x - sx, pl.y, false);
        for (Plat pl : plats) if (pl.tile == 4) for (int y = pl.y + 16; y < SUELO; y += 16) {
            p.spr(tileS[4], pl.x - sx, y, false);
            p.spr(tileS[4], pl.x + pl.w - 16 - sx, y, false);
        }
        for (Caja c : cajas) p.sprM(tileS[3], c.x - 8, c.y - 16, false);
        // rehenes
        for (Rehen r : rehenes) {
            Sprite s = r.estado == 0 ? rehenS[(t / 40) % 2] : r.estado == 1 ? rehenS[2 + (r.t / 10) % 2] : rehenS[4 + (r.t / 6) % 4];
            p.sprM(s, r.x - 16, r.y - 32, r.estado == 2);
            if (r.estado == 0 && (t / 20) % 2 == 0) p.textoC("HELP!", Math.round(r.x) - sx, Math.round(r.y) - 42, 7);
        }
        // objetos
        for (Obj o : objs) if (o.t < 700 || (t / 4) % 2 == 0) p.sprM(objS[o.tipo], o.x - 8, o.y - 16 + (float) Math.sin(o.t * 0.15) * 1.5F, false);
        // enemigos
        for (Enem e : enems) dibujarEnemigo(p, e);
        // jugadores
        for (Jug j : js) dibujarJugador(p, j);
        // balas
        for (Bala bl : balas) {
            int s = switch (bl.tipo) {
                case 0 -> 0;
                case 1 -> 1;
                case 2, 6 -> 3;
                case 3 -> 4;
                default -> 2;
            };
            p.sprM(balaS[s], bl.x - 4, bl.y - 4, bl.vx < 0);
        }
        // efectos
        for (Fx f : fx) {
            switch (f.tipo) {
                case 0 -> {
                    if (f.t >= 0) p.sprM(boomS[Math.min(5, f.t / 5)], f.x - 16, f.y - 24, false);
                }
                case 1 -> p.sprM(balaS[5], f.x - 4, f.y - 4, false);
                case 2 -> p.px(Math.round(f.x) - sx, Math.round(f.y), f.c);
                case 3 -> p.textoC(f.txt, Math.round(f.x) - sx, Math.round(f.y), f.c);
                case 4 -> {
                    int r = 1 + f.t / 8;
                    p.circulo(Math.round(f.x) - sx, Math.round(f.y), r, f.t < 12 ? 5 : 4);
                }
                default -> {}
            }
        }
        p.camX = 0;
        hud(p);
    }

    private void dibujarJugador(Pantalla p, Jug j) {
        if (!j.activo) return;
        if (!j.vivo) {
            if (j.muerto < 50 || (t / 3) % 2 == 0) {
                int f = 11 + Math.min(3, j.muerto / 6);
                p.sprM(cuerpo[j.n][f], j.x - 16, j.y - 32, j.mira < 0);
            }
            return;
        }
        if (j.invul > 0 && (t / 3) % 2 == 0) return;
        int f = frame(j);
        boolean esp = j.mira < 0;
        p.sprM(cuerpo[j.n][f], j.x - 16, j.y - 32, esp);
        int dir = j.lanzaT > 0 ? 3 : j.apunta == 1 ? 1 : j.apunta == 2 ? 2 : 0;
        int fila = Math.min(2, j.arma);
        int hx = HOMBRO[f][0], hy = HOMBRO[f][1];
        float bx = j.x + (esp ? 16 - hx : hx - 16) - 16, by = j.y - 32 + hy - 16;
        if (j.disparoT > 3 && dir == 0) bx -= j.mira;   // retroceso
        p.sprM(brazo[j.n][fila * 4 + dir], bx, by, esp);
        if (j.disparoT > 3) {
            float mx = j.x + (hx - 16) * j.mira + (dir == 0 ? j.mira * 14 : 0), my = j.y - 32 + hy + (dir == 1 ? -14 : dir == 2 ? 14 : 1);
            p.sprM(balaS[5], mx - 4, my - 4, false);
        }
        if (j.invul > 100) p.textoC(j.n == 0 ? "1P" : "2P", Math.round(j.x) - p.camX, Math.round(j.y) - 44, j.n == 0 ? 12 : 39);
    }

    private void dibujarEnemigo(Pantalla p, Enem e) {
        boolean flash = e.golpe > 0 && e.golpe % 2 == 0;
        switch (e.tipo) {
            case 0, 1 -> {
                Sprite s;
                if (!e.vivo) s = rebelde[8 + Math.min(3, e.muerteT / 5)];
                else if (e.estado == 2) s = rebelde[e.tipo == 0 ? 5 : 7];
                else if (e.estado == 1) s = rebelde[e.tipo == 0 ? 4 : 6];
                else s = rebelde[(e.t / 8) % 4];
                if (!e.vivo && e.muerteT > 30 && (t / 3) % 2 == 0) return;
                p.sprM(s, e.x - 16, e.y - 32, e.mira < 0);
            }
            case 2 -> {
                if (!e.vivo) return;
                Sprite s = tanqueS[(e.t / 6) % 2];
                if (flash) p.sprColor(s, Math.round(e.x) - 32 - p.camX, Math.round(e.y) - 40, false, 7);
                else p.sprM(s, e.x - 32, e.y - 40, false);
            }
            case 3 -> {
                Sprite s = heliS[(e.t / 2) % 3];
                if (!e.vivo && e.muerteT % 4 < 2) return;
                if (flash) p.sprColor(s, Math.round(e.x) - 32 - p.camX, Math.round(e.y) - 30, false, 7);
                else p.sprM(s, e.x - 32, e.y - 30, false);
            }
            case 4 -> {
                if (!e.vivo) return;
                Sprite s = torretaS[e.cad > 110 ? 1 : 0];
                if (flash) p.sprColor(s, Math.round(e.x) - 16 - p.camX, Math.round(e.y) - 32, false, 7);
                else p.sprM(s, e.x - 16, e.y - 32, false);
            }
            case 5 -> {
                if (!e.vivo && e.muerteT > 20) return;
                Sprite s = jefeS[(e.t / 10) % 3];
                if (flash) p.sprColor(s, Math.round(e.x) - 56 - p.camX, Math.round(e.y) - 80, false, 12);
                else p.sprM(s, e.x - 56, e.y - 80, false);
                // barra de vida
                int w = 120;
                p.rect(68, 20, w + 2, 6, 0);
                p.rect(69, 21, Math.max(0, w * e.hp / e.hpMax), 4, e.hp < e.hpMax / 2 ? 11 : 15);
                p.textoC(net.minecraft.client.resources.language.I18n.get("chatarra.miscelanea.cangrejo"), 128, 28, 20);
            }
            default -> {}
        }
    }

    private void hud(Pantalla p) {
        p.rect(0, 0, Pantalla.W, 14, 1);
        p.rect(0, 14, Pantalla.W, 1, 3);
        for (Jug j : js) {
            int x = j.n == 0 ? 4 : 132;
            int col = j.n == 0 ? 12 : 39;
            p.texto((j.n == 0 ? "1P " : "2P ") + j.puntos, x, 3, col);
            String arma = j.arma == ARMA_METRALLETA ? "H " + j.municion : j.arma == ARMA_COHETES ? "R " + j.municion : "∞";
            p.textoD(arma, x + 92, 3, 20);
            p.textoD("●" + j.granadas, x + 120, 3, 28);
            for (int k = 0; k < j.vidas; k++) p.rect(x + k * 5, 16, 3, 3, col);
        }
        if (avisoT > 0 && (avisoT > 40 || (avisoT / 6) % 2 == 0)) {
            p.rect(0, 70, Pantalla.W, 18, 0);
            p.textoC(avisoTxt, 128, 75, 20);
        }
        // flecha de avanzar
        float lider = 0;
        for (Jug j : js) if (j.vivo) lider = Math.max(lider, j.x);
        if (!bloqueo && (t / 20) % 2 == 0 && lider - cam < 110 && avisoT == 0) p.textoD("GO! ▶", 250, 40, 20);
    }
}

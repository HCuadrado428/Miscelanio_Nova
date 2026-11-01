package com.miscelanea.client.arcade;

import com.miscelanea.client.consola.Botones;
import com.miscelanea.registry.ModSounds;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.sounds.SoundEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.miscelanea.client.arcade.Lucha.*;

/**
 * Puños de Barrio: avanzar por la calle (del barrio al puerto) limpiándola de matones, uno o dos jugadores a la vez.
 * Cruceta: andar (también en profundidad) · A: golpes encadenados · B: saltar (y A en el aire: patada voladora) ·
 * C: ataque giratorio que golpea alrededor (gasta un poco de vida).
 */
public class PunosBarrio extends JuegoArcade {
    static final int LARGO = 2560, Z_MIN = 124, Z_MAX = 186, FIN_CAM = LARGO - Pantalla.W;
    static final int E_QUIETO = 0, E_ANDAR = 1, E_SALTO = 3, E_ATAQUE = 4, E_DANO = 5, E_CAIDA = 7, E_SUELO = 8, E_LEVANTA = 9, E_VICTORIA = 10,
            E_KO = 11, E_ENTRA = 12;

    record Mov(int fPrep, int fAct, int ini, int act, int rec, int dano, int x0, int x1, boolean derriba, boolean alrededor) {}

    static final Mov PUNO_M = new Mov(PUNO_P, PUNO, 3, 3, 7, 5, 10, 40, false, false);
    static final Mov PUNO2_M = new Mov(PUNO_P, Lucha.PUNO2, 2, 3, 8, 6, 10, 40, false, false);
    static final Mov GANCHO_M = new Mov(AGACHADO, Lucha.GANCHO, 4, 5, 14, 10, 4, 34, true, false);
    static final Mov PATADA_M = new Mov(PATADA_P, PATADA, 6, 4, 12, 8, 14, 50, false, false);
    static final Mov AEREA_M = new Mov(SALTO2, Lucha.AEREA, 3, 200, 0, 10, 6, 42, true, false);
    static final Mov GIRO_M = new Mov(ESP1, ESP2, 4, 14, 12, 9, -30, 40, true, true);
    static final Mov EMBESTIDA_M = new Mov(ESP1, ESP2, 14, 26, 18, 12, 4, 36, true, false);
    static final Mov GORDO_M = new Mov(PUNO_P, PUNO, 12, 4, 18, 11, 10, 44, true, false);

    static final int[] HP_ENEMIGO = {0, 0, 0, 0, 34, 28, 80, 300};
    static final int[] PUNTOS_ENEMIGO = {0, 0, 0, 0, 100, 150, 400, 5000};
    static final float[] VEL_ENEMIGO = {0, 0, 0, 0, 1.0F, 1.4F, 0.7F, 1.0F};

    final class Ent {
        int pj, jug = -1, est, t, dir = 1, hp, hpMax, flash, invul, golpesT, combo, vidas, espera, ladoX = 1;
        float x, z, h, vx, vz, vh;
        Mov mov;
        boolean golpeo, fuera, borrar;
        final Mando man = new Mando();

        boolean suelo() {
            return h <= 0 && vh >= 0;
        }

        boolean libre() {
            return est == E_QUIETO || est == E_ANDAR;
        }

        boolean jugador() {
            return jug >= 0;
        }

        void poner(int e) {
            est = e;
            t = 0;
        }

        void atacar(Mov m) {
            mov = m;
            golpeo = false;
            poner(E_ATAQUE);
            sonido(ModSounds.LU_AIRE, m == PATADA_M ? 0.8F : 1.1F);
        }

        int ancho() {
            return pj == GRANDULLON || pj == JEFE ? 16 : 12;
        }
    }

    static final class Cosa {
        float x, z;
        int tipo, t;       // 0 cubo, 1 pollo, 2 bolsa
        boolean roto;
        int contenido;
    }

    record Oleada(int cam, int[] tipos) {}

    static final Oleada[] OLEADAS = {
            new Oleada(0, new int[]{CHUPA, CHUPA}),
            new Oleada(240, new int[]{CHUPA, CHUPA, CHANDAL}),
            new Oleada(520, new int[]{CHANDAL, CHANDAL, CHANDAL}),
            new Oleada(800, new int[]{CHUPA, CHUPA, GRANDULLON}),
            new Oleada(1060, new int[]{CHANDAL, CHANDAL, CHUPA, CHUPA}),
            new Oleada(1320, new int[]{GRANDULLON, CHANDAL, CHUPA}),
            new Oleada(1580, new int[]{CHUPA, CHUPA, CHUPA, CHANDAL}),
            new Oleada(1840, new int[]{GRANDULLON, GRANDULLON, CHANDAL}),
            new Oleada(2080, new int[]{CHANDAL, CHANDAL, CHUPA, CHUPA}),
            new Oleada(FIN_CAM, new int[]{JEFE, CHUPA, CHANDAL}),
    };
    static final int[][] CUBOS = {{380, 1}, {700, 2}, {1150, 1}, {1500, 2}, {1700, 1}, {1990, 1}, {2200, 2}, {2420, 1}};

    final List<Ent> ents = new ArrayList<>();
    final List<Cosa> cosas = new ArrayList<>();
    final List<Efecto> efs = new ArrayList<>();
    final Ent[] js = new Ent[2];
    int camX, oleada, pausa, t, avisoT, finT, pendientes, entraT;
    boolean oleadaActiva, victoria;
    String aviso = "";
    Ent ultimoGolpeado;
    int ultimoT;
    Sprite calle;

    public PunosBarrio(Botones[] b, long semilla, int jugadores) {
        super(b, semilla, jugadores);
    }

    @Override
    public SoundEvent musica() {
        return ModSounds.LU_BARRIO.get();
    }

    @Override
    public void reiniciar() {
        Lucha.cargar();
        calle = Sprite.uno("lucha/calle");
        ents.clear();
        cosas.clear();
        efs.clear();
        for (int i = 0; i < jugadores; i++) {
            Ent j = new Ent();
            j.pj = i == 0 ? KAI : LUNA;
            j.jug = i;
            j.hp = j.hpMax = 100;
            j.vidas = 3;
            j.x = 60 + i * 30;
            j.z = 150 + i * 20;
            j.h = 140 + i * 30;
            j.poner(E_ENTRA);
            ents.add(j);
            js[i] = j;
        }
        for (int[] c : CUBOS) {
            Cosa k = new Cosa();
            k.x = c[0];
            k.z = Z_MIN + 2;
            k.contenido = c[1];
            cosas.add(k);
        }
        camX = 0;
        oleada = 0;
        oleadaActiva = false;
        pendientes = 0;
        puntos = 0;
        fin = false;
        victoria = false;
        t = 0;
        avisar(I18n.get("barrio.miscelanea.empieza"), 120);
    }

    void avisar(String s, int d) {
        aviso = s;
        avisoT = d;
    }

    // ------------------------------------------------------------------ paso
    @Override
    public void paso() {
        t++;
        if (avisoT > 0) avisoT--;
        if (pausa > 0) {
            pausa--;
            return;
        }
        for (int i = 0; i < jugadores; i++) js[i].man.desde(b[i]);
        oleadas();
        for (Ent e : new ArrayList<>(ents)) {
            if (e.jugador()) logicaJugador(e);
            else logicaEnemigo(e);
            fisica(e);
        }
        choques();
        recoger();
        ents.removeIf(e -> e.borrar);
        for (int i = efs.size() - 1; i >= 0; i--) if (++efs.get(i).t >= efs.get(i).dur) efs.remove(i);
        for (Cosa c : cosas) c.t++;
        camara();
        if (victoria) {
            if (++finT > 260) {
                fin = true;
                mensajeFin = I18n.get("barrio.miscelanea.libre");
            }
        } else {
            boolean vivos = false;
            for (int i = 0; i < jugadores; i++) if (!js[i].fuera) vivos = true;
            if (!vivos && ++finT > 90) {
                fin = true;
                mensajeFin = I18n.get("barrio.miscelanea.fin");
            }
        }
    }

    void oleadas() {
        if (oleadaActiva) {
            // que entren los que faltan poco a poco
            if (pendientes > 0 && t % 50 == 0) soltarEnemigo(OLEADAS[oleada].tipos()[OLEADAS[oleada].tipos().length - pendientes]);
            boolean quedan = pendientes > 0;
            for (Ent e : ents) if (!e.jugador()) quedan = true;
            if (!quedan) {
                oleadaActiva = false;
                oleada++;
                if (oleada >= OLEADAS.length) {
                    victoria = true;
                    finT = 0;
                    puntos += 10000;
                    for (int i = 0; i < jugadores; i++) {
                        puntos += js[i].vidas * 5000;
                        if (!js[i].fuera) js[i].poner(E_VICTORIA);
                    }
                    avisar(I18n.get("barrio.miscelanea.libre"), 260);
                    sonido(ModSounds.LU_RONDA, 1.5F);
                } else {
                    avisar(I18n.get("barrio.miscelanea.adelante"), 150);
                    sonido(ModSounds.LU_RONDA, 1.2F);
                }
            }
        } else if (oleada < OLEADAS.length && camX >= OLEADAS[oleada].cam() && t > 60) {
            oleadaActiva = true;
            int[] tp = OLEADAS[oleada].tipos();
            pendientes = tp.length;
            // al principio salen dos a la vez; con dos jugadores, uno más de propina
            soltarEnemigo(tp[0]);
            if (tp.length > 1) soltarEnemigo(tp[1]);
            if (jugadores > 1) {
                Ent extra = crearEnemigo(rnd.nextBoolean() ? CHUPA : CHANDAL);
                ents.add(extra);
            }
            if (tp[0] == JEFE) {
                avisar(I18n.get("barrio.miscelanea.jefe"), 150);
                sonido(ModSounds.LU_KO, 0.7F);
            }
        }
    }

    void soltarEnemigo(int tipo) {
        pendientes--;
        ents.add(crearEnemigo(tipo));
    }

    Ent crearEnemigo(int tipo) {
        Ent e = new Ent();
        e.pj = tipo;
        e.hp = e.hpMax = HP_ENEMIGO[tipo];
        boolean derecha = tipo == JEFE || rnd.nextInt(3) > 0 || camX < 20;
        e.x = derecha ? camX + Pantalla.W + 30 : camX - 30;
        e.z = Z_MIN + 6 + rnd.nextInt(Z_MAX - Z_MIN - 6);
        e.dir = derecha ? -1 : 1;
        e.ladoX = rnd.nextBoolean() ? 1 : -1;
        e.espera = 10 + rnd.nextInt(30);
        e.poner(E_ANDAR);
        return e;
    }

    void camara() {
        float suma = 0;
        int n = 0;
        for (int i = 0; i < jugadores; i++)
            if (!js[i].fuera) {
                suma += js[i].x;
                n++;
            }
        if (n == 0) return;
        int obj = (int) (suma / n) - 110;
        int limite = oleadaActiva ? OLEADAS[oleada].cam() : oleada < OLEADAS.length ? Math.max(camX, OLEADAS[oleada].cam()) : FIN_CAM;
        if (oleadaActiva) limite = Math.max(camX, OLEADAS[oleada].cam());
        int nuevo = Math.max(camX, Math.min(Math.min(obj, limite), FIN_CAM));
        camX = Math.min(camX + 2, nuevo);
    }

    // ------------------------------------------------------------------ jugadores
    void logicaJugador(Ent j) {
        j.t++;
        if (j.invul > 0) j.invul--;
        if (j.flash > 0) j.flash--;
        Mando m = j.man;
        switch (j.est) {
            case E_ENTRA -> {
                if (j.suelo() && j.t > 2) {
                    j.poner(E_QUIETO);
                    efs.add(new Efecto(EF_POLVO, j.x, j.z, 14));
                    sonido(ModSounds.LU_CAIDA, 1.3F);
                }
            }
            case E_QUIETO, E_ANDAR -> {
                if (victoria) {
                    j.vx = j.vz = 0;
                    break;
                }
                if (m.usar(Botones.C) && j.hp > 1) {
                    if (j.hp > 6) j.hp -= 4;
                    j.atacar(GIRO_M);
                    j.invul = Math.max(j.invul, 24);
                    sonido(ModSounds.LU_ESPECIAL, 1F);
                    break;
                }
                if (m.usar(Botones.A)) {
                    j.atacar(j.combo == 1 ? PUNO2_M : j.combo >= 2 ? GANCHO_M : PUNO_M);
                    break;
                }
                if (m.usar(Botones.B)) {
                    j.vh = -4.6F;
                    j.vx = (m.m[Botones.DER] ? 1.6F : m.m[Botones.IZQ] ? -1.6F : 0);
                    j.vz = 0;
                    j.poner(E_SALTO);
                    sonido(ModSounds.LU_AIRE, 0.6F);
                    break;
                }
                float vx = (m.m[Botones.DER] ? 1 : 0) - (m.m[Botones.IZQ] ? 1 : 0), vz = (m.m[Botones.ABAJO] ? 1 : 0) - (m.m[Botones.ARRIBA] ? 1 : 0);
                j.vx = vx * 1.4F;
                j.vz = vz * 0.9F;
                if (vx != 0) j.dir = vx > 0 ? 1 : -1;
                j.est = vx != 0 || vz != 0 ? E_ANDAR : E_QUIETO;
                if (j.t > 40) j.combo = 0;
            }
            case E_SALTO -> {
                if (m.usar(Botones.A)) {
                    j.mov = AEREA_M;
                    j.golpeo = false;
                    j.est = E_ATAQUE;
                    j.t = 0;
                    sonido(ModSounds.LU_AIRE, 1F);
                }
                if (j.suelo() && j.t > 2) {
                    j.vx = 0;
                    j.poner(E_QUIETO);
                }
            }
            case E_ATAQUE -> ataque(j);
            default -> comunes(j);
        }
    }

    void ataque(Ent e) {
        Mov mv = e.mov;
        if (mv == AEREA_M) {
            if (e.suelo() && e.t > 2) {
                e.vx = 0;
                e.poner(E_QUIETO);
            }
            return;
        }
        if (e.suelo()) {
            e.vx *= 0.7F;
            e.vz = 0;
        }
        if (mv == EMBESTIDA_M && e.t >= mv.ini() && e.t < mv.ini() + mv.act()) {
            e.vx = 3.4F * e.dir;
            if (e.t % 6 == 0) efs.add(new Efecto(EF_POLVO, e.x - e.dir * 14, e.z, 12));
        }
        if (mv == GIRO_M && e.t >= mv.ini() && (e.t - mv.ini()) % 7 == 0) {
            e.dir = -e.dir;
            e.golpeo = false;
        }
        if (mv == GANCHO_M && e.t == mv.ini()) e.vh = -2F;
        if (e.jugador() && e.golpeo && e.t > mv.ini() && (mv == PUNO_M || mv == PUNO2_M) && e.man.usar(Botones.A)) {
            e.combo++;
            e.atacar(mv == PUNO_M ? PUNO2_M : GANCHO_M);
            return;
        }
        if (e.t >= mv.ini() + mv.act() + mv.rec()) {
            if (e.jugador()) {
                if (!e.golpeo || mv == GANCHO_M || mv == GIRO_M) e.combo = 0;
                else e.combo = mv == PUNO_M ? 1 : 2;
            }
            e.poner(E_QUIETO);
        }
    }

    void comunes(Ent e) {
        switch (e.est) {
            case E_DANO -> {
                e.vx *= 0.8F;
                if (e.t >= e.golpesT) e.poner(E_QUIETO);
            }
            case E_CAIDA -> {
                if (e.suelo() && e.t > 4) {
                    e.vx = 0;
                    sonido(ModSounds.LU_CAIDA, 1F);
                    efs.add(new Efecto(EF_POLVO, e.x, e.z, 14));
                    e.poner(e.hp <= 0 ? E_KO : E_SUELO);
                }
            }
            case E_SUELO -> {
                if (e.t > 34) e.poner(E_LEVANTA);
            }
            case E_LEVANTA -> {
                if (e.t > 14) {
                    e.poner(E_QUIETO);
                    e.invul = 30;
                }
            }
            case E_KO -> {
                if (e.t > 60) {
                    if (!e.jugador()) e.borrar = true;
                    else if (e.vidas > 0) {
                        e.vidas--;
                        e.hp = e.hpMax;
                        e.h = 150;
                        e.vh = 0;
                        e.invul = 120;
                        e.x = camX + 60 + e.jug * 40;
                        e.poner(E_ENTRA);
                    } else {
                        e.fuera = true;
                        e.borrar = true;
                    }
                }
            }
            default -> {}
        }
    }

    // ------------------------------------------------------------------ enemigos
    Ent objetivo(Ent e) {
        Ent mejor = null;
        float d = 1e9F;
        for (int i = 0; i < jugadores; i++) {
            Ent j = js[i];
            if (j.fuera || j.est == E_KO || j.est == E_ENTRA) continue;
            float dd = Math.abs(j.x - e.x) + Math.abs(j.z - e.z) * 2;
            if (dd < d) {
                d = dd;
                mejor = j;
            }
        }
        return mejor;
    }

    void logicaEnemigo(Ent e) {
        e.t++;
        if (e.invul > 0) e.invul--;
        if (e.flash > 0) e.flash--;
        if (e.est == E_ATAQUE) {
            ataque(e);
            return;
        }
        if (!e.libre()) {
            if (e.est == E_SALTO) {
                if (e.suelo() && e.t > 2) e.poner(E_QUIETO);
            } else comunes(e);
            return;
        }
        Ent j = objetivo(e);
        if (j == null || victoria) {
            e.vx = e.vz = 0;
            e.est = E_QUIETO;
            return;
        }
        float v = VEL_ENEMIGO[e.pj];
        float distX = e.pj == GRANDULLON || e.pj == JEFE ? 40 : 34;
        float ox = j.x + e.ladoX * distX, oz = j.z;
        if (e.espera > 0) {
            e.espera--;
            // rondar a cierta distancia
            ox = j.x + e.ladoX * (distX + 30);
        }
        float dx = ox - e.x, dz = oz - e.z;
        e.dir = j.x > e.x ? 1 : -1;
        boolean enPosicion = Math.abs(dx) < 5 && Math.abs(dz) < 4;
        if (!enPosicion) {
            e.vx = Math.abs(dx) < 2 ? 0 : Math.signum(dx) * v;
            e.vz = Math.abs(dz) < 2 ? 0 : Math.signum(dz) * v * 0.7F;
            e.est = E_ANDAR;
            // el de chándal a veces entra con una patada voladora
            if (e.pj == CHANDAL && e.espera == 0 && Math.abs(j.x - e.x) < 80 && Math.abs(dz) < 6 && rnd.nextInt(90) == 0) {
                e.vh = -4.4F;
                e.vx = e.dir * 2.2F;
                e.vz = 0;
                e.mov = AEREA_M;
                e.golpeo = false;
                e.poner(E_ATAQUE);
                e.t = 3;
            }
            // el grande y el jefe embisten desde lejos
            if ((e.pj == GRANDULLON || e.pj == JEFE) && e.espera == 0 && Math.abs(dz) < 5 && Math.abs(j.x - e.x) > 60 && rnd.nextInt(e.pj == JEFE ? 70 : 140) == 0) {
                e.atacar(EMBESTIDA_M);
                sonido(ModSounds.LU_ESPECIAL, 0.6F);
            }
            return;
        }
        e.vx = e.vz = 0;
        e.est = E_QUIETO;
        if (e.espera == 0 && rnd.nextInt(e.pj == JEFE ? 8 : 12) == 0) {
            Mov m = switch (e.pj) {
                case CHANDAL -> PATADA_M;
                case GRANDULLON -> GORDO_M;
                case JEFE -> rnd.nextInt(3) == 0 ? GANCHO_M : rnd.nextBoolean() ? PUNO_M : PATADA_M;
                default -> rnd.nextInt(3) == 0 ? PUNO2_M : PUNO_M;
            };
            e.atacar(m);
            e.espera = 16 + rnd.nextInt(e.pj == JEFE ? 24 : 44);
            if (rnd.nextInt(4) == 0) e.ladoX = -e.ladoX;
        }
    }

    // ------------------------------------------------------------------ física y golpes
    void fisica(Ent e) {
        e.x += e.vx;
        e.z += e.vz;
        if (e.h > 0 || e.vh < 0) {
            e.h -= e.vh;
            e.vh += 0.22F;
            if (e.h <= 0) {
                e.h = 0;
                e.vh = 0;
            }
        }
        e.z = Math.max(Z_MIN, Math.min(Z_MAX, e.z));
        if (e.jugador()) e.x = Math.max(camX + 14, Math.min(camX + Pantalla.W - 14, e.x));
        else e.x = Math.max(camX - 60, Math.min(camX + Pantalla.W + 60, e.x));
    }

    boolean activo(Ent e) {
        if (e.est != E_ATAQUE) return false;
        Mov mv = e.mov;
        return mv == AEREA_M ? e.t >= mv.ini() && !e.suelo() : e.t >= mv.ini() && e.t < mv.ini() + mv.act();
    }

    void choques() {
        for (Ent a : ents) {
            if (!activo(a) || a.golpeo) continue;
            Mov mv = a.mov;
            float x0 = a.dir > 0 ? a.x + mv.x0() : a.x - mv.x1(), x1 = a.dir > 0 ? a.x + mv.x1() : a.x - mv.x0();
            boolean alguno = false;
            for (Ent o : ents) {
                if (o == a || o.jugador() == a.jugador() || o.invul > 0) continue;
                if (o.est == E_SUELO || o.est == E_LEVANTA || o.est == E_KO || o.est == E_CAIDA || o.est == E_ENTRA) continue;
                if (Math.abs(o.z - a.z) > 9 || Math.abs(o.h - a.h) > 40) continue;
                float w = o.ancho();
                if (x1 < o.x - w || x0 > o.x + w) continue;
                alguno = true;
                golpear(a, o, mv, o.x > a.x ? 1 : -1);
            }
            if (alguno) a.golpeo = true;
            // cubos de basura
            if (a.jugador())
                for (Cosa c : cosas) {
                    if (c.tipo != 0 || c.roto || Math.abs(c.z - a.z) > 14 || x1 < c.x - 10 || x0 > c.x + 10) continue;
                    c.roto = true;
                    a.golpeo = true;
                    sonido(ModSounds.LU_ROMPER, 1F);
                    efs.add(new Efecto(EF_GOLPE, c.x, c.z - 20, 9));
                    Cosa d = new Cosa();
                    d.tipo = c.contenido;
                    d.x = c.x + a.dir * 14;
                    d.z = Math.min(Z_MAX, c.z + 12);
                    cosas.add(d);
                    puntos += 50;
                    break;
                }
        }
    }

    void golpear(Ent a, Ent o, Mov mv, int dir) {
        int dano = a.jugador() ? mv.dano() : Math.round(mv.dano() * 1.5F);
        o.hp -= dano;
        o.flash = 6;
        efs.add(new Efecto(EF_GOLPE, (a.x + o.x) / 2 + dir * 4, o.z - o.h - 46, 9));
        sonido(dano >= 9 ? ModSounds.LU_GOLPE2 : ModSounds.LU_GOLPE, 0.9F + rnd.nextFloat() * 0.2F);
        pausa = 3;
        if (a.jugador()) {
            puntos += dano * 10;
            ultimoGolpeado = o;
            ultimoT = 150;
        }
        // los grandes aguantan los golpes sueltos: solo los tumba el remate (o si ya están atacando, ni se inmutan)
        boolean grande = o.pj == GRANDULLON || o.pj == JEFE;
        if (grande && o.hp > 0 && !mv.derriba() && (o.est == E_ATAQUE || rnd.nextInt(3) > 0)) return;
        if (o.hp <= 0 || mv.derriba() || !o.suelo()) {
            if (o.hp <= 0) {
                o.hp = 0;
                if (!o.jugador()) {
                    puntos += PUNTOS_ENEMIGO[o.pj];
                    if (o.pj == JEFE) pausa = 30;
                } else sonido(ModSounds.LU_KO, 1.2F);
            }
            o.poner(E_CAIDA);
            o.vh = -3.2F;
            o.h = Math.max(o.h, 1);
            o.vx = dir * 2F;
            o.vz = 0;
            o.combo = 0;
        } else {
            o.poner(E_DANO);
            o.golpesT = 16;
            o.vx = dir * 1.2F;
            o.vz = 0;
        }
    }

    void recoger() {
        for (int i = 0; i < jugadores; i++) {
            Ent j = js[i];
            if (j.fuera || !j.libre()) continue;
            for (int k = cosas.size() - 1; k >= 0; k--) {
                Cosa c = cosas.get(k);
                if (c.tipo == 0 || c.t < 20 || Math.abs(c.x - j.x) > 14 || Math.abs(c.z - j.z) > 8) continue;
                if (c.tipo == 1) {
                    j.hp = j.hpMax;
                    avisar(I18n.get("barrio.miscelanea.pollo"), 60);
                } else puntos += 1000;
                sonido(ModSounds.LU_COMER, c.tipo == 1 ? 1F : 1.5F);
                cosas.remove(k);
            }
        }
        if (ultimoT > 0) ultimoT--;
    }

    // ------------------------------------------------------------------ dibujo
    @Override
    public void dibujar(Pantalla p) {
        Lucha.fondo(p, calle, camX, 0);
        // todo lo que hay en la calle, ordenado por profundidad
        List<Object> orden = new ArrayList<>();
        orden.addAll(cosas);
        orden.addAll(ents);
        orden.sort(Comparator.comparingDouble(o -> o instanceof Ent e ? e.z : ((Cosa) o).z));
        for (Object o : orden)
            if (o instanceof Ent e) Lucha.sombra(p, (int) e.x - camX, (int) e.z, e.ancho() + 2 - (int) (e.h / 10));
        for (Object o : orden) {
            if (o instanceof Cosa c) {
                int sx = (int) c.x - camX;
                if (sx < -20 || sx > Pantalla.W + 20) continue;
                int f = c.tipo == 0 ? (c.roto ? EF_CUBO_ROTO : EF_CUBO) : c.tipo == 1 ? EF_POLLO : EF_BOLSA;
                if (c.tipo != 0 && c.t < 20) {
                    p.spr(efectos[f], sx - 16, (int) c.z - 30 - (int) (Math.sin(c.t / 20.0 * Math.PI) * 12), false);
                } else if (!(c.tipo != 0 && c.t > 600 && c.t % 8 < 4)) p.spr(efectos[f], sx - 16, (int) c.z - 30, false);
            } else {
                Ent e = (Ent) o;
                if (e.invul > 0 && e.invul % 6 < 3 && e.est != E_ENTRA) continue;
                if (e.est == E_KO && e.t > 30 && e.t % 6 < 3) continue;
                Lucha.dibujar(p, e.pj, cuadro(e), (int) e.x - camX, (int) (e.z - e.h), e.dir, e.flash);
            }
        }
        for (Efecto ef : efs) {
            int f = ef.tipo + Math.min(2, ef.t * 3 / ef.dur);
            p.spr(efectos[f], (int) ef.x - camX - 16, (int) ef.y - 16 - (ef.tipo == EF_POLVO ? 12 : 0), false);
        }
        hud(p);
    }

    int cuadro(Ent e) {
        return switch (e.est) {
            case E_QUIETO -> QUIETO + (e.t / 10) % 4;
            case E_ANDAR -> ANDAR + (e.t / 6) % 6;
            case E_SALTO, E_ENTRA -> e.vh < -1 ? SALTO : SALTO2;
            case E_ATAQUE -> {
                Mov mv = e.mov;
                if (mv == GIRO_M && e.t >= mv.ini()) yield e.pj == LUNA ? ((e.t / 4) % 2 == 0 ? ESP2 : Lucha.PATADA) : ESP2;
                yield e.t < mv.ini() ? mv.fPrep() : mv.fAct();
            }
            case E_DANO -> DANO;
            case E_CAIDA -> e.vh < 0 ? CAIDA1 : CAIDA2;
            case E_SUELO, E_KO -> TUMBADO;
            case E_LEVANTA -> Lucha.LEVANTA;
            case E_VICTORIA -> VICTORIA;
            default -> QUIETO;
        };
    }

    void hud(Pantalla p) {
        for (int i = 0; i < jugadores; i++) {
            Ent j = js[i];
            int x0 = i * 128;
            p.rect(x0, 0, 128, 22, 0);
            Sprite cara = caras[j.pj];
            for (int y = 0; y < 20; y++) System.arraycopy(cara.px, (y + 2) * 32 + 4, p.buf, (y + 1) * Pantalla.W + x0 + 1, 22);
            p.texto(I18n.get("lucha.miscelanea.pj." + Lucha.PJ[j.pj]), x0 + 26, 1, i == 0 ? 12 : 39);
            p.textoD("x" + j.vidas, x0 + 124, 1, 7);
            Lucha.barra(p, x0 + 26, 12, 96, j.fuera ? 0 : j.hp / (float) j.hpMax, j.fuera ? 0 : j.hp / (float) j.hpMax, false, 19);
        }
        // arreglar el fondo transparente de las caras
        for (int i = 0; i < jugadores; i++)
            for (int y = 1; y < 21; y++)
                for (int x = 1; x < 23; x++) {
                    int k = y * Pantalla.W + i * 128 + x;
                    if (p.buf[k] == (byte) 255) p.buf[k] = 2;
                }
        if (jugadores == 1) {
            p.textoD(I18n.get("barrio.miscelanea.puntos") + " " + puntos, 252, 1, 20);
            if ((t / 30) % 2 == 0) p.textoD(I18n.get("barrio.miscelanea.j2"), 252, 12, 5);
        }
        if (ultimoGolpeado != null && ultimoT > 0 && !ultimoGolpeado.jugador()) {
            Ent e = ultimoGolpeado;
            p.rect(0, 176, 140, 16, 0);
            p.texto(I18n.get("lucha.miscelanea.pj." + Lucha.PJ[e.pj]), 4, 178, 6);
            Lucha.barra(p, 70, 181, 64, e.hp / (float) e.hpMax, e.hp / (float) e.hpMax, false, 11);
        }
        if (jugadores > 1) p.textoD(Integer.toString(puntos), 252, 178, 20);
        boolean adelante = !oleadaActiva && !victoria && oleada < OLEADAS.length && camX < OLEADAS[oleada].cam() && t > 120;
        if (adelante && (t / 15) % 2 == 0) {
            p.textoD(I18n.get("barrio.miscelanea.flecha"), 250, 40, 20);
        }
        if (avisoT > 0) {
            p.rect(0, 70, Pantalla.W, 18, 0);
            p.textoC(aviso, 128, 75, 20);
        }
    }
}
